"""CrewAI akışları: sohbet, envanter/SKT özeti, tarif (+ JSON ayrıştırma)."""

from __future__ import annotations

import json
import os
import re
from datetime import date, datetime, timedelta, timezone, tzinfo
from urllib.parse import urlparse
from typing import Any
from zoneinfo import ZoneInfo

from crewai import Agent, Crew, LLM, Process, Task

from app.consumption_validate import merge_and_validate_consumption
from app.schemas import (
    InventoryItemIn,
    MissingItemOut,
    SuggestMode,
    SuggestRequest,
    SuggestResponse,
)


def _api_key() -> str | None:
    raw = (
        os.getenv("LLM_API_KEY")
        or os.getenv("GROQ_API_KEY")
        or os.getenv("OPENAI_API_KEY")
        or ""
    ).strip()
    if not raw:
        return None
    if (raw.startswith('"') and raw.endswith('"')) or (raw.startswith("'") and raw.endswith("'")):
        raw = raw[1:-1].strip()
    return raw or None


def _is_groq_api_key(key: str) -> bool:
    return key.lstrip().lower().startswith("gsk_")


def _llm_model() -> str:
    return (os.getenv("LLM_MODEL") or "gpt-4o-mini").strip()


def _openrouter_model_for_api(raw_model: str, base_url: str) -> str:
    """
    OpenRouter API gövdesinde model, sitedeki slug olmalı (örn. qwen/qwen3.6-plus:free).
    LiteLLM'e `openrouter/qwen/...` verilirse aynı string API'ye gider ve 400 döner.
    Sağlayıcıyı provider='openrouter' ve base_url ile veriyoruz; modele openrouter/ öneki ekleme.
    """
    m = raw_model.strip()
    if not base_url or "openrouter.ai" not in base_url.lower():
        return m
    prefix = "openrouter/"
    if m.lower().startswith(prefix):
        return m[len(prefix) :]
    return m


def _normalize_groq_base_url(base: str) -> str:
    """Groq OpenAI uyumlu uç: https://api.groq.com/openai/v1"""
    raw = base.strip().rstrip("/")
    if "api.groq.com" not in raw.lower():
        return base.strip()
    if raw.lower().endswith("/openai/v1"):
        return raw
    p = urlparse(raw if "://" in raw else f"https://{raw}")
    if "api.groq.com" not in p.netloc.lower():
        return base.strip()
    scheme = p.scheme if p.scheme in ("http", "https") else "https"
    return f"{scheme}://{p.netloc}/openai/v1"


def _groq_model_for_crewai(model: str, base_url: str) -> str:
    """
    qwen/..., meta-llama/... gibi Groq model id'leri '/' içerir; CrewAI bunları LiteLLM'e
    düşürüp yanlış sağlayıcıya yönlendirebilir → 401 (Authorization yok).
    hosted_vllm/ öneki OpenAI uyumlu istemciyi kullanır; base_url Groq olunca doğru API'ye gider.
    """
    m = model.strip()
    if not base_url or "api.groq.com" not in base_url.lower():
        return m
    if m.lower().startswith("hosted_vllm/"):
        return m
    if "/" not in m:
        return m
    return f"hosted_vllm/{m}"


def build_llm() -> LLM:
    key = _api_key()
    if not key:
        raise ValueError(
            "LLM anahtarı yok. Ortam: LLM_API_KEY, GROQ_API_KEY veya OPENAI_API_KEY."
        )
    base_raw = (
        os.getenv("OPENAI_API_BASE")
        or os.getenv("OPENAI_BASE_URL")
        or os.getenv("GROQ_API_BASE")
        or ""
    ).strip()
    # gsk_ yalnızca Groq’ta geçer; eski OpenRouter/OpenAI base ortamda kalırsa 401 olur.
    if _is_groq_api_key(key):
        groq_only = (os.getenv("GROQ_API_BASE") or "").strip()
        if groq_only:
            base_raw = groq_only
        elif (not base_raw) or ("api.groq.com" not in base_raw.lower()):
            base_raw = "https://api.groq.com/openai/v1"
    base = _normalize_groq_base_url(base_raw) if base_raw else ""
    model = _openrouter_model_for_api(_llm_model(), base)
    model = _groq_model_for_crewai(model, base)
    kw: dict[str, Any] = {
        "model": model,
        "api_key": key,
        "temperature": 0.35,
    }
    if base:
        kw["base_url"] = base
    if base and "openrouter.ai" in base.lower():
        kw["provider"] = "openrouter"
    return LLM(**kw)


def _expiry_epoch_seconds(raw: int | float) -> int:
    """İstemci çoğunlukla ms gönderir; fromtimestamp saniye bekler."""
    x = int(raw)
    if x > 1_000_000_000_000:
        x //= 1000
    return x


def _client_tz(body: SuggestRequest) -> tzinfo:
    """
    SKT gününü kullanıcı telefonundaki takvimle hizala.
    Windows'ta IANA veritabanı yoksa ZoneInfo başarısız olur; `pip install tzdata` gerekir.
    Yine de başarısız olursa Türkiye için sabit UTC+3, diğerleri için UTC kullanılır.
    """
    raw = (body.client_time_zone or "").strip() or "Europe/Istanbul"
    candidates = [raw]
    if raw != "Europe/Istanbul":
        candidates.append("Europe/Istanbul")
    for key in candidates:
        try:
            return ZoneInfo(key)
        except Exception:
            continue
    low = raw.lower()
    if low in {
        "europe/istanbul",
        "asia/istanbul",
        "turkey",
        "istanbul",
    }:
        return timezone(timedelta(hours=3))
    return timezone.utc


def _expiry_local_date_and_status(
    it: InventoryItemIn,
    tz: tzinfo,
) -> tuple[date | None, str | None]:
    """Yerel SKT günü ve kullanıcı/LLM için okunaklı Türkçe durum metni."""
    if it.expiry_date is None:
        return None, None
    try:
        sec = _expiry_epoch_seconds(it.expiry_date)
        exp_local = datetime.fromtimestamp(sec, tz=timezone.utc).astimezone(tz).date()
    except (OSError, OverflowError, ValueError):
        return None, None
    today = datetime.now(tz).date()
    delta = (exp_local - today).days
    if delta < 0:
        n = abs(delta)
        status = f"Süresi dolmuş ({n} gün önce; tüketilmemeli)"
    elif delta == 0:
        status = "Son gün (bugün; hemen kullanın veya çöpe ayırın)"
    elif delta <= 3:
        status = f"Kritik ({delta} gün kaldı)"
    elif delta <= 14:
        status = f"Yakın ({delta} gün kaldı)"
    else:
        status = f"Normal ({delta} gün kaldı)"
    return exp_local, status


def _forced_expired_skus_block(body: SuggestRequest) -> str:
    """SKT'si geçmiş ürünlerin kısa Türkçe listesi; analist görevi ve kullanıcı yanıtına eklenir."""
    tz = _client_tz(body)
    today = datetime.now(tz).date()
    rows: list[tuple[date, InventoryItemIn]] = []
    for it in body.items:
        if it.expiry_date is None:
            continue
        exp_local, _ = _expiry_local_date_and_status(it, tz)
        if exp_local is not None and exp_local < today:
            rows.append((exp_local, it))
    if not rows:
        return ""
    rows.sort(key=lambda x: (x[0], x[1].name.lower()))
    lines = [
        f"• {it.name} — {it.quantity:g} {it.unit} (SKT {d.isoformat()}, süresi dolmuş)"
        for d, it in rows
    ]
    today_s = today.isoformat()
    return (
        f"KESİN (uygulama hesabı): Kullanıcı yerel tarihi BUGÜN={today_s}. "
        f"Aşağıdaki ürünlerin SKT takvim günü BUGÜN'den önce; güvenli tüketim için UYGUN DEĞİLDİR "
        f"(çöpe ayırın). Özetinde bunları bölüm 1'de en önce yaz; «yakında biter» veya "
        f"«öncelikle tüketin» DEMEYİN.\n"
        + "\n".join(lines)
    )


def _skt_priority_key(it: InventoryItemIn, tz: tzinfo) -> tuple[int, int, str]:
    """Sıra: süresi dolmuş → 0–3 gün → diğerleri → SKT yok."""
    if it.expiry_date is None:
        return (4, 9999, it.name.lower())
    exp_local, _ = _expiry_local_date_and_status(it, tz)
    if exp_local is None:
        return (4, 9998, it.name.lower())
    today = datetime.now(tz).date()
    delta = (exp_local - today).days
    if delta < 0:
        return (0, delta, it.name.lower())
    if delta <= 3:
        return (1, delta, it.name.lower())
    return (2, delta, it.name.lower())


def _sorted_inventory_items(body: SuggestRequest) -> list[InventoryItemIn]:
    tz = _client_tz(body)
    return sorted(body.items, key=lambda i: _skt_priority_key(i, tz))


def _format_item_line(
    it: InventoryItemIn,
    *,
    include_inventory_id: bool = True,
    tz: tzinfo,
) -> str:
    prefix = ""
    if include_inventory_id and it.inventory_item_id is not None and it.inventory_item_id > 0:
        prefix = f"id={it.inventory_item_id} | "
    parts = [f"{prefix}{it.name}: {it.quantity} {it.unit}"]
    if it.category_name:
        parts.append(f"kategori={it.category_name}")
    if it.expiry_date is not None:
        exp_local, status = _expiry_local_date_and_status(it, tz)
        if exp_local is not None:
            parts.append(f"SKT = {exp_local.isoformat()}")
            if status:
                parts.append(f"SKT durumu: {status}")
        else:
            parts.append(f"SKT_epoch={it.expiry_date}")
    return " | ".join(parts)


def inventory_context_public(body: SuggestRequest) -> str:
    """Kullanıcıya gidecek metinlerde kullanılır; stok kayıt numarası yok."""
    if not body.items:
        return "(Envanter listesi boş.)"
    tz = _client_tz(body)
    today_s = datetime.now(tz).date().isoformat()
    lines = [
        _format_item_line(x, include_inventory_id=False, tz=tz) for x in _sorted_inventory_items(body)
    ]
    return (
        f"(Kullanıcı yerel tarihi BUGÜN={today_s}; satırlar önce süresi dolmuş / acil SKT, sonra diğerleri.)\n"
        + "\n".join(lines)
    )


def inventory_context_with_ids(body: SuggestRequest) -> str:
    """Yalnızca şef görevi (CONSUMPTION_JSON) için; satır başı id=... içerir."""
    if not body.items:
        return "(Envanter listesi boş.)"
    tz = _client_tz(body)
    today_s = datetime.now(tz).date().isoformat()
    lines = [
        _format_item_line(x, include_inventory_id=True, tz=tz) for x in _sorted_inventory_items(body)
    ]
    return (
        f"(Kullanıcı yerel tarihi BUGÜN={today_s}; satırlar SKT önceliğine göre sıralı.)\n"
        + "\n".join(lines)
    )


def inventory_context_block(body: SuggestRequest) -> str:
    """Geriye dönük uyumluluk: dahili stok id’li blok."""
    return inventory_context_with_ids(body)


def _marker_regex(marker_name: str) -> re.Pattern[str]:
    """Örn. **MISSING_ITEMS_JSON:** veya MISSING_ITEMS_JSON:"""
    return re.compile(
        rf"\*{{0,2}}\s*{re.escape(marker_name)}\s*:\s*\*{{0,2}}",
        re.IGNORECASE | re.MULTILINE,
    )


def _extract_json_array_after_marker(text: str, marker_name: str) -> str | None:
    m = _marker_regex(marker_name).search(text)
    if not m:
        return None
    rest = text[m.end() :]
    i = 0
    while i < len(rest) and rest[i] in " \t\r\n":
        i += 1
    if i >= len(rest) or rest[i] != "[":
        return None
    depth = 0
    for j in range(i, len(rest)):
        if rest[j] == "[":
            depth += 1
        elif rest[j] == "]":
            depth -= 1
            if depth == 0:
                return rest[i : j + 1]
    return None


def _consume_json_array_suffix(text: str, from_idx: int) -> int | None:
    """from_idx sonrasında (boşluk atlayarak) [...] bitiş indeksini döndür (exclusive)."""
    k = from_idx
    while k < len(text) and text[k] in " \t\r\n":
        k += 1
    if k >= len(text) or text[k] != "[":
        return None
    depth = 0
    for j in range(k, len(text)):
        if text[j] == "[":
            depth += 1
        elif text[j] == "]":
            depth -= 1
            if depth == 0:
                return j + 1
    return None


def _strip_json_marker_blocks(text: str) -> str:
    """**MISSING_ITEMS_JSON:** / **CONSUMPTION_JSON:** ve ardından gelen JSON dizisini tamamen sil."""
    names = ("MISSING_ITEMS_JSON", "CONSUMPTION_JSON")
    s = text
    while True:
        best_start: int | None = None
        best_end_marker: int | None = None
        for name in names:
            m = _marker_regex(name).search(s)
            if m and (best_start is None or m.start() < best_start):
                best_start = m.start()
                best_end_marker = m.end()
        if best_start is None or best_end_marker is None:
            break
        arr_end = _consume_json_array_suffix(s, best_end_marker)
        if arr_end is not None:
            remove_to = arr_end
            while remove_to < len(s) and s[remove_to] in "\r\n":
                remove_to += 1
            s = s[:best_start] + s[remove_to:]
        else:
            line_start = s.rfind("\n", 0, best_start) + 1
            line_end = s.find("\n", best_start)
            if line_end < 0:
                s = s[:line_start]
            else:
                s = s[:line_start] + s[line_end + 1 :]
    return s


def _parse_missing_items_json(chef_raw: str) -> list[MissingItemOut]:
    raw = _extract_json_array_after_marker(chef_raw, "MISSING_ITEMS_JSON")
    if not raw:
        raw = _extract_first_structured_json_array(
            chef_raw,
            require_keys={"name"},
            exclude_keys={"inventory_item_id"},
        )
    if not raw:
        return []
    try:
        data: Any = json.loads(raw)
        if not isinstance(data, list):
            return []
        out: list[MissingItemOut] = []
        for row in data:
            if not isinstance(row, dict):
                continue
            name = str(row.get("name", "")).strip()
            if not name:
                continue
            qty = row.get("suggested_quantity", row.get("quantity", 1))
            unit = str(row.get("unit", "adet")).strip() or "adet"
            try:
                fq = float(qty)
            except (TypeError, ValueError):
                fq = 1.0
            out.append(
                MissingItemOut(name=name, suggested_quantity=fq, unit=unit[:32])
            )
        return out
    except json.JSONDecodeError:
        return []


def _parse_consumption_json(chef_raw: str) -> list[dict[str, Any]]:
    raw = _extract_json_array_after_marker(chef_raw, "CONSUMPTION_JSON")
    if not raw:
        raw = _extract_first_structured_json_array(
            chef_raw, require_keys={"inventory_item_id", "delta"}
        )
    if not raw:
        return []
    try:
        data: Any = json.loads(raw)
        if not isinstance(data, list):
            return []
        return [x for x in data if isinstance(x, dict)]
    except json.JSONDecodeError:
        return []


def _extract_first_structured_json_array(
    text: str,
    *,
    require_keys: set[str],
    exclude_keys: set[str] | None = None,
) -> str | None:
    """Etiket yoksa bile gövdede ilk uygun [...] dizisini bul (model formatı bozduysa)."""
    ex = exclude_keys or set()
    decoder = json.JSONDecoder()
    i = 0
    while i < len(text):
        if text[i] != "[":
            i += 1
            continue
        try:
            obj, end = decoder.raw_decode(text, i)
        except json.JSONDecodeError:
            i += 1
            continue
        if isinstance(obj, list) and obj and all(isinstance(x, dict) for x in obj):
            k0 = set(obj[0].keys())
            if require_keys <= k0 and not (ex & k0):
                return text[i:end]
        i += 1
    return None


def _strip_machine_json_lines_anywhere(text: str) -> str:
    """MISSING_ITEMS_JSON / CONSUMPTION_JSON satırlarını (**, satır içi) kullanıcı metninden at."""
    lines = text.splitlines()
    out: list[str] = []
    for ln in lines:
        st = ln.strip()
        if re.match(r"\*{0,2}\s*MISSING_ITEMS_JSON\s*:\s*\*{0,2}", st, re.IGNORECASE):
            continue
        if re.match(r"\*{0,2}\s*CONSUMPTION_JSON\s*:\s*\*{0,2}", st, re.IGNORECASE):
            continue
        if re.match(r"MISSING_ITEMS_JSON:\s*", st, re.IGNORECASE):
            continue
        if re.match(r"CONSUMPTION_JSON:\s*", st, re.IGNORECASE):
            continue
        out.append(ln)
    return "\n".join(out).strip()


def _strip_internal_id_markers(text: str) -> str:
    """Model yanlışlıkla id sızdırırsa temizle."""
    s = re.sub(r"\(envanterde\s*id\s*=\s*\d+\)", "", text, flags=re.IGNORECASE)
    s = re.sub(r"\bid\s*=\s*\d+\s*\|\s*", "", s, flags=re.IGNORECASE)
    s = re.sub(r"\binventory_item_id\s*[:=]\s*\d+", "", s, flags=re.IGNORECASE)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def _strip_markdown_section_noise(text: str) -> str:
    """Modelin kullanıcıya sızdırdığı başlık + JSON öncesi gürültü."""
    lines = text.splitlines()
    noise = re.compile(
        r"^\*{0,2}\s*(Eksik\s+Malzemeler|Stok\s+Düşümü|MISSING_ITEMS|CONSUMPTION)\s*:?\s*\*{0,2}\s*$",
        re.IGNORECASE,
    )
    combined = re.compile(
        r"^\*{0,2}\s*Eksik\s+Malzemeler\s*:\s*\*{0,2}\s*Stok\s+Düşümü\s*:?\s*\*{0,2}\s*$",
        re.IGNORECASE,
    )
    combined_tight = re.compile(
        r"^\s*\*{0,2}\s*Eksik\s+Malzemeler\s*:\s*Stok\s+Düşümü\s*:?\s*\*{0,2}\s*$",
        re.IGNORECASE,
    )
    out: list[str] = []
    for ln in lines:
        st = ln.strip()
        if noise.match(st) or combined.match(st) or combined_tight.match(st):
            continue
        out.append(ln)
    joined = "\n".join(out)
    return re.sub(
        r"(?i)\s*Eksik\s+Malzemeler\s*:\s*Stok\s+Düşümü\s*:?\s*",
        "",
        joined,
    ).strip()


def _strip_json_object_arrays_from_text(text: str) -> str:
    """
    İşaret satırı silindikten sonra kalan ham [{...},...] dizilerini kaldırır
    (eksik listesi veya consumption — kullanıcıya gösterilmez, API alanlarında kalır).
    """
    decoder = json.JSONDecoder()
    s = text
    for _ in range(40):
        removed = False
        i = 0
        while i < len(s):
            if s[i] != "[":
                i += 1
                continue
            try:
                obj, end = decoder.raw_decode(s, i)
            except json.JSONDecodeError:
                i += 1
                continue
            if not isinstance(obj, list) or not obj:
                i += 1
                continue
            if not all(isinstance(x, dict) for x in obj):
                i += 1
                continue
            keys0 = set(obj[0].keys())
            is_missing = "name" in keys0
            is_consumption = "inventory_item_id" in keys0 and "delta" in keys0
            if not (is_missing or is_consumption):
                i += 1
                continue
            tail = end
            while tail < len(s) and s[tail] in " \t\r\n,":
                tail += 1
            s = s[:i] + s[tail:]
            removed = True
            break
        if not removed:
            break
    return s


def _sanitize_user_display_message(text: str) -> str:
    s = _strip_json_marker_blocks(text)
    s = _strip_machine_json_lines_anywhere(s)
    s = _strip_markdown_section_noise(s)
    s = _strip_json_object_arrays_from_text(s)
    s = _strip_internal_id_markers(s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"\1", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def _strip_chef_machine_lines(chef_raw: str) -> str:
    """Şef ham çıktısı: makine JSON'u ve sızıntı dizilerini tamamen ayıkla."""
    s = _strip_json_marker_blocks(chef_raw)
    s = _strip_machine_json_lines_anywhere(s)
    s = _strip_markdown_section_noise(s)
    s = _strip_json_object_arrays_from_text(s)
    s = _strip_internal_id_markers(s)
    return s.strip()


def resolve_suggest_mode(body: SuggestRequest) -> SuggestMode:
    if body.suggest_mode is not None:
        return body.suggest_mode
    msg = (body.user_message or "").strip()
    if not msg:
        return SuggestMode.inventory_only
    return SuggestMode.chat


def run_kitchen_suggestion(body: SuggestRequest) -> SuggestResponse:
    mode = resolve_suggest_mode(body)
    if mode == SuggestMode.chat:
        return _run_chat_mode(body)
    if mode == SuggestMode.inventory_only:
        return _run_inventory_only(body)
    return _run_recipe_crew(body)


def _run_chat_mode(body: SuggestRequest) -> SuggestResponse:
    llm = build_llm()
    inv = inventory_context_public(body)
    user_note = (body.user_message or "").strip() or "(boş mesaj)"
    ctx = (body.recipe_followup_context or "").strip()
    ctx_block = ""
    if ctx:
        ctx_block = (
            "\n\nÖNCEKİ TARİF ÖNERİSİ BAĞLAMI (kullanıcı şimdi yazıyor; tutarlı ve samimi kal):\n"
            f"{ctx}\n"
        )
    host = Agent(
        role="Mutfak sohbet asistanı",
        goal="Kullanıcıya Türkçe, kısa ve net yardımcı olmak.",
        backstory=(
            "KitchenMind uygulamasının asistanısın; tek bir mesaj kutusu var. "
            "Kullanıcı selam, teşekkür veya sohbet ediyorsa samimi ve doğal karşıla; "
            "bu durumda envanter listesini yanıtta okuma, numaralı stok dökme veya SKT ile boğma. "
            "Envanter yalnızca kullanıcı özellikle stok/SKT/israf sorarsa veya ne pişireceğine "
            "dair bağlam isterse kısaca kullanılır. "
            "Tam tarif + malzeme uyumu + stok düşümü için uygulama ayrı bir tam akış kullanır; "
            "kullanıcıya bunun için aynı kutuya örnek cümle yazmasını söyle: "
            "«Bu akşam ne pişireyim?», «Elimdekilerle ne yemek?» veya alttaki «Tam tarif (Crew)»."
        ),
        llm=llm,
        allow_delegation=False,
        verbose=False,
    )
    chat_task = Task(
        description=(
            f"ENVANTER (yalnızca bağlam; kullanıcıya madde madde zorla okutma):\n{inv}\n\n"
            f"KULLANICI MESAJI:\n{user_note}"
            f"{ctx_block}\n"
            "Kullanıcı yalnızca selam/nasılsın gibi kısa bir şey yazdıysa: kısa selamla, envanterden bahsetme.\n"
            "Yemek istiyorsa ama tam tarif istemiyorsa: nazikçe tam tarif için yukarıdaki örnek cümleleri veya "
            "«Tam tarif (Crew)» düğmesini hatırlat; kendin uzun tarif ve eksik liste üretme (bu sohbet modu).\n"
            "Yanıtın yalnızca düz Türkçe metin olsun (birkaç cümle). "
            "MISSING_ITEMS_JSON, CONSUMPTION_JSON, ** ile başlayan makine etiketi veya ham JSON yazma."
        ),
        expected_output="Kısa Türkçe sohbet yanıtı.",
        agent=host,
    )
    crew = Crew(
        agents=[host],
        tasks=[chat_task],
        process=Process.sequential,
        verbose=False,
        memory=False,
    )
    output = crew.kickoff()
    text = (output.raw or "").strip()
    if output.tasks_output:
        text = (output.tasks_output[0].raw or text).strip()
    text = _sanitize_user_display_message(text)
    return SuggestResponse(message=text, consumption=[], missing_items=[])


_EMPTY_INVENTORY_HINT_TR = (
    "Envanterinizde kayıtlı ürün yok. Envanter sekmesinden malzeme ekledikten sonra burada "
    "«SKT» ile özet veya «Bu akşam ne pişireyim?» ile tarif isteyebilirsiniz.\n\n"
    "İpucu: Önce dolabınızdaki ürünleri ekleyin; böylece SKT uyarıları ve tarif önerileri doğru çalışır."
)

_SKT_ANALYST_RULES_TR = (
    "SKT yorum kuralları (zorunlu):\n"
    "- Her satırdaki «SKT = YYYY-MM-DD» ve «SKT durumu: …» metnine uy; envanter üstte BUGÜN= tarihi verilmiştir.\n"
    "- «SKT durumu» içinde «Süresi dolmuş» geçen ürünler gerçekten geçmiştir; 1) bölümünde önce bunları yaz. "
    "Tüketilmemeli; çöpe ayırmayı öner. Bunları «yakında bitecek» veya «önce bunu tüketin» diye anlatma.\n"
    "- Henüz geçmemiş ama «Kritik» veya «Son gün» yazanlar ikinci öncelik.\n"
    "- «SKT durumu» içinde «Normal» veya «Yakın» ve uzun süre kalan (ör. salça, konserve) ürünleri acil sayma; "
    "özet ve öncelikte süresi dolmuş ve kritik satırların altında tut.\n"
    "- Listede SKT yazmayan satır için tarih uydurma.\n"
    "- Kullanıcıya teknik anahtar (SKT_durum=, GECMIS_1_gun gibi) yazma; düzgün Türkçe cümle ve madde işaretleri kullan.\n"
)


def _run_inventory_only(body: SuggestRequest) -> SuggestResponse:
    if not body.items:
        return SuggestResponse(
            message=_EMPTY_INVENTORY_HINT_TR,
            consumption=[],
            missing_items=[],
        )
    llm = build_llm()
    inv = inventory_context_public(body)
    user_note = body.user_message if body.user_message else "(Kullanıcı ek notu yok.)"
    analyst = Agent(
        role="Envanter Analisti",
        goal=(
            "Kullanıcının mutfak envanterini SKT ve israf riskine göre özetlemek; "
            "öncelikli tüketilmesi gereken ürünleri net biçimde sıralamak."
        ),
        backstory=(
            "Gıda güvenliği ve israf azaltma konusunda titiz bir veri analistisin. "
            "Abartılı vaatler vermez, yalnızca verilen listeye dayanırsın."
        ),
        llm=llm,
        allow_delegation=False,
        verbose=False,
    )
    forced_expired = _forced_expired_skus_block(body)
    forced_prefix = f"{forced_expired}\n\n" if forced_expired else ""

    analyze_task = Task(
        description=(
            f"{forced_prefix}"
            "Aşağıdaki envanter satırlarını ve kullanıcı notunu kullan.\n\n"
            f"ENVANTER:\n{inv}\n\n"
            f"{_SKT_ANALYST_RULES_TR}\n"
            f"KULLANICI NOTU:\n{user_note}\n\n"
            "Türkçe çıktı üret:\n"
            "1) Süresi dolmuş ürünler (varsa en önce), ardından kritik / son gün yaklaşanlar\n"
            "2) Öncelik sırası (madde madde; mümkünse tarih ve durumu düzgün Türkçe yaz)\n"
            "3) Kısa genel yorum (2-3 cümle)\n"
            "Liste boşsa: kullanıcıya envanter eklemesini öner ve genel, risksiz ipuçları ver.\n\n"
            "ÇOK ÖNEMLİ: Envanter satırı yoksa madde numaralı liste üretme; «Ürün Adı:» gibi "
            "boş yer tutucu yazma; en fazla 3-4 kısa cümle.\n"
            "Envanter doluysa yalnızca verilen satırları kullan; listede olmayan ürün uydurma.\n\n"
            "Kullanıcıya asla id=, stok numarası, envanterde id= veya teknik tanımlayıcı yazma; "
            "sadece ürün adı, miktar ve birim kullan.\n"
            "Tarif önerme; MISSING_ITEMS_JSON veya CONSUMPTION_JSON yazma."
        ),
        expected_output="Türkçe envanter özeti, düz metin.",
        agent=analyst,
    )
    crew = Crew(
        agents=[analyst],
        tasks=[analyze_task],
        process=Process.sequential,
        verbose=False,
        memory=False,
    )
    output = crew.kickoff()
    analyst_text = (output.raw or "").strip()
    if output.tasks_output:
        analyst_text = (output.tasks_output[0].raw or analyst_text).strip()
    analyst_text = _sanitize_user_display_message(analyst_text)
    if forced_expired:
        analyst_text = f"{forced_expired}\n\n---\n\n{analyst_text}".strip()
    return SuggestResponse(message=analyst_text, consumption=[], missing_items=[])


def _run_recipe_crew(body: SuggestRequest) -> SuggestResponse:
    if not body.items:
        return SuggestResponse(
            message=(
                "Envanter boşken tam tarif ve stok düşümü önerilemez. Önce Envanter sekmesinden "
                "malzeme ekleyin; sonra «Bu akşam ne pişireyim?» yazıp Gönder veya Tarif ile devam edin. "
                "Stok kaydı oluşunca «bu yemeği yap» ile düşüm onayı da çalışır."
            ),
            consumption=[],
            missing_items=[],
        )
    llm = build_llm()
    inv_public = inventory_context_public(body)
    inv_ids = inventory_context_with_ids(body)
    user_note = body.user_message if body.user_message else "(Kullanıcı ek notu yok.)"
    forced_expired_recipe = _forced_expired_skus_block(body)
    forced_recipe_prefix = f"{forced_expired_recipe}\n\n" if forced_expired_recipe else ""

    analyst = Agent(
        role="Envanter Analisti",
        goal=(
            "Kullanıcının mutfak envanterini SKT ve israf riskine göre özetlemek; "
            "öncelikli tüketilmesi gereken ürünleri net biçimde sıralamak."
        ),
        backstory=(
            "Gıda güvenliği ve israf azaltma konusunda titiz bir veri analistisin. "
            "Abartılı vaatler vermez, yalnızca verilen listeye dayanırsın."
        ),
        llm=llm,
        allow_delegation=False,
        verbose=False,
    )

    chef = Agent(
        role="Mutfak Şefi",
        goal=(
            "Analist özetini ve envanteri dikkate alarak, mümkün olan en uygun yemek fikrini "
            "Türkçe ve ev kullanıcısı için anlaşılır şekilde önermek."
        ),
        backstory=(
            "Deneyimli bir ev şefisin; pratik tarifler, porsiyon ve adımlar sunarsın. "
            "Listede olmayan malzemeyi varsaymaktan kaçınırsın; gerekiyorsa eksikleri "
            "açıkça belirtirsin."
        ),
        llm=llm,
        allow_delegation=False,
        verbose=False,
    )

    analyze_task = Task(
        description=(
            f"{forced_recipe_prefix}"
            "Aşağıdaki envanter satırlarını ve kullanıcı notunu kullan.\n\n"
            f"ENVANTER:\n{inv_public}\n\n"
            f"{_SKT_ANALYST_RULES_TR}\n"
            f"KULLANICI NOTU:\n{user_note}\n\n"
            "Türkçe çıktı üret:\n"
            "1) Süresi dolmuş ürünler (varsa en önce), ardından kritik / son gün yaklaşanlar\n"
            "2) Öncelik sırası (madde madde; yalnızca envanterde gerçekten olan ürünler; tarih ve durumu düzgün Türkçe yaz)\n"
            "3) Kısa genel yorum (2-3 cümle)\n"
            "Envanter satırı yoksa uzun liste veya «Ürün Adı:» yer tutucu yazma.\n\n"
            "Kullanıcıya asla id=, stok numarası, envanterde id= veya teknik tanımlayıcı yazma; "
            "sadece ürün adı, miktar ve birim kullan."
        ),
        expected_output="Türkçe, madde işaretli envanter özeti ve öncelik yorumu.",
        agent=analyst,
    )

    cook_task = Task(
        description=(
            "Önceki görevdeki analist çıktısını temel al.\n\n"
            f"ENVANTER (tekrar referans; stok düşümü için id satır başında):\n{inv_ids}\n\n"
            "Görevler:\n"
            "- Analistin vurguladığı önceliklere uygun 1 ana yemek önerisi (başlık + malzemeler "
            "  envanterle uyumlu + kısa hazırlık adımları).\n"
            "- Envanter özeti, SKT listesi, «Öncelik sırası» veya «Genel yorum» başlıklarını "
            "  TEKRARLAMA; analist çıktısı kullanıcıya ayrı iletiliyor. Yalnızca tarif metnini yaz.\n"
            "- Tarif için envanterde olmayan ama gerçekten gerekli malzemeler varsa bunları "
            "  belirt.\n"
            "Dil: Türkçe, samimi ama net.\n\n"
            "Kullanıcıya gösterilecek tarif ve malzeme listesinde id=, envanterde id= veya "
            "inventory_item_id yazma; yalnızca Türkçe ürün adı ve miktar kullan.\n\n"
            "ENVANTERDE her satırda id=... ile verilen sayı, uygulamanın stok kaydıdır. "
            "Önerdiğin tarifte hangi kayıtlardan ne kadar düşüleceğini sadece bu id'ler ile belirt.\n\n"
            "MISSING_ITEMS_JSON kuralları (çok önemli):\n"
            "- ENVANTER listesinde ada göre bulunmayan HER malzemeyi ayrı nesne olarak ekle: "
            "ör. tereyağı, margarin, zeytinyağı, sıvı yağ, ayçiçek yağı, tuz, karabiber, "
            "pul biber, kimyon, şeker, limon, sarımsak vb.\n"
            "- Baharat ve yağlar için uygun birim: çay kaşığı, yemek kaşığı, ml veya L; "
            "kütle için g veya kg; adet için adet.\n"
            "- Yalnızca bir ürünü eksik sayma; tarifte geçen ve envanterde OLMAYAN tüm "
            "kalemleri eksiksiz listele.\n"
            "- Eksik yoksa: MISSING_ITEMS_JSON: []\n\n"
            "Çıktının sondan bir önceki satırı (tek satır, geçerli JSON dizi):\n"
            'MISSING_ITEMS_JSON: [{"name":"Ürün Adı","suggested_quantity":1,"unit":"adet"}]\n\n'
            "Çıktının EN SON satırı (tek satır, geçerli JSON dizi):\n"
            'CONSUMPTION_JSON: [{"inventory_item_id":5,"delta":1}]\n'
            "CONSUMPTION_JSON kuralları (çok önemli — uygulama stok düşümü buna bağlı):\n"
            "- Envanter listesi boş değilse ve tarif o envanterden malzeme kullanıyorsa "
            "CONSUMPTION_JSON mutlaka en az bir nesne içermelidir; her kullanılan stok satırı "
            "için {\"inventory_item_id\": <satırdaki id>, \"delta\": <kullanılan miktar>} yaz.\n"
            "- delta, o envanter satırının birimi ile aynı olmalı (adet, L, kg …). "
            "Stokta yetmeyen ürün için delta yazma; onu MISSING_ITEMS_JSON'a koy.\n"
            "- Yalnızca envanter tamamen boşsa veya tarif gerçekten hiç stok kullanmıyorsa "
            "CONSUMPTION_JSON: [] kullan.\n"
            "Boş bırakma veya unutma: kullanıcı «yap» dediğinde uygulama bu JSON ile düşüm yapar.\n\n"
            "ÖNEMLİ: Bu iki JSON satırında ** veya markdown kullanma; kullanıcı metninde "
            "görünmemeli. Tam olarak şu biçimde düz yaz: MISSING_ITEMS_JSON: [...] ve "
            "CONSUMPTION_JSON: [...] (tek satır veya etiketten hemen sonra dizi).\n"
        ),
        expected_output=(
            "Türkçe tarif; sondan ikinci satır MISSING_ITEMS_JSON, son satır CONSUMPTION_JSON."
        ),
        agent=chef,
        context=[analyze_task],
    )

    crew = Crew(
        agents=[analyst, chef],
        tasks=[analyze_task, cook_task],
        process=Process.sequential,
        verbose=False,
        memory=False,
    )

    output = crew.kickoff()
    analyst_text = ""
    chef_text = (output.raw or "").strip()
    if output.tasks_output:
        analyst_text = (output.tasks_output[0].raw or "").strip()
        if len(output.tasks_output) > 1:
            chef_text = (output.tasks_output[-1].raw or chef_text).strip()

    missing = _parse_missing_items_json(chef_text)
    raw_consumption = _parse_consumption_json(chef_text)
    consumption = merge_and_validate_consumption(raw_consumption, body)
    chef_clean = _strip_chef_machine_lines(chef_text)

    if analyst_text:
        analyst_text = _sanitize_user_display_message(analyst_text)
    chef_clean = _sanitize_user_display_message(chef_clean)
    message = chef_clean if chef_clean else analyst_text
    message = _sanitize_user_display_message(message)

    return SuggestResponse(
        message=message,
        consumption=consumption,
        missing_items=missing,
    )
