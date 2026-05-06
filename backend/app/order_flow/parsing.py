"""Sipariş metninden yapılandırılmış satır çıkarımı (LLM + anahtarsız geri dönüş)."""

from __future__ import annotations

import re
import os
import json
import asyncio
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.order_flow.llm import build_chat_llm, has_llm_configured
from app.schemas import OrderDraftLineOut


class OrderParseResult(BaseModel):
    """LLM yapılandırılmış çıktısı — `with_structured_output` ile üretilir."""

    lines: list[OrderDraftLineOut] = Field(default_factory=list)
    needs_clarification: bool = False
    clarification_question: str = ""
    """Önceki özet sonrası kullanıcı siparişi onaylıyorsa True (veya kural tabanlı kısayol)."""
    user_confirmed_order: bool = False
    """Onay sorusundayken kullanıcı vazgeçiyorsa True."""
    user_cancelled_order: bool = False


_CONFIRM_RE = re.compile(
    r"^(evet|tamam|onay|onaylıyorum|onayliyorum|sipariş\s*et|siparis\s*et|buyur|olur|ok)\s*[!.]?$",
    re.IGNORECASE,
)

_CANCEL_RE = re.compile(
    r"^(hayır|hayir|yok|iptal|vazgeç|vazgec|geri|olmaz|istemiyorum|no)\s*[!.]?$",
    re.IGNORECASE,
)


def _last_human_text(messages: list[BaseMessage]) -> str:
    for m in reversed(messages or []):
        if isinstance(m, HumanMessage):
            c = m.content
            return c if isinstance(c, str) else str(c)
    return ""


def _format_history_for_prompt(messages: list[BaseMessage], max_turns: int = 8) -> str:
    lines: list[str] = []
    for m in messages[-max_turns:]:
        if isinstance(m, HumanMessage):
            role = "Kullanıcı"
        elif isinstance(m, AIMessage):
            role = "Asistan"
        else:
            role = m.type or "?"
        c = m.content
        text = c if isinstance(c, str) else str(c)
        lines.append(f"{role}: {text}")
    return "\n".join(lines) if lines else "(mesaj yok)"


def _fallback_parse_lines(user_text: str) -> OrderParseResult:
    """API anahtarı yok veya LLM hata verirse: virgül / satır / ' ve ' ile böl."""
    raw = user_text.strip()
    if not raw:
        return OrderParseResult(
            needs_clarification=True,
            clarification_question="Ne sipariş etmek istediğinizi yazabilir misiniz?",
        )
    tmp = re.split(r"\s+ve\s+", raw, flags=re.IGNORECASE)
    parts: list[str] = []
    for chunk in tmp:
        parts.extend(re.split(r"[\n,;]+", chunk))
    lines: list[OrderDraftLineOut] = []
    for p in parts:
        name = p.strip()
        if len(name) < 2:
            continue
        lines.append(OrderDraftLineOut(name=name[:200], quantity=1.0, unit="adet"))
    if not lines:
        return OrderParseResult(
            needs_clarification=True,
            clarification_question="Ürün isimlerini net yazabilir misiniz? Örn: 2 kg domates, süt 1 L",
        )
    return OrderParseResult(lines=lines, needs_clarification=False)

#mcp adımları -------------------------------------------------------------------------------------------------------------
#MCP 4. ADIM - mcp açık mı değil mi kontrolü yapıyor.
def _use_mcp_for_parse() -> bool:
    return (os.getenv("ORDER_FLOW_USE_MCP") or "").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }

#mcp tool çağrısı adımı
async def _call_mcp_draft_order_from_text(user_text: str) -> dict[str, Any]:
    from mcp import ClientSession, StdioServerParameters
    from mcp.client.stdio import stdio_client

    python_bin = (os.getenv("ORDER_FLOW_MCP_PYTHON") or "python").strip()
    params = StdioServerParameters(
        command=python_bin,
        #mcp server dosyasının bulunduğu yer ve bu fonksiyon python -m app.mcp_server ile MCP server’ı stdio üzerinden açıyor,
        args=["-m", "app.mcp_server"],
    )
    async with stdio_client(params) as (read_stream, write_stream):
        async with ClientSession(read_stream, write_stream) as session:
            await session.initialize()
            #MCP 7. ADIM
            #mcp server'ın mcp_server.py dosyasındaki draft_order_from_text tool'unu çağırıyor ve payload alınır.
            result = await session.call_tool(
                "draft_order_from_text",
                {"user_message": user_text},
            )
            content = getattr(result, "content", []) or []
            for item in content:
                text = getattr(item, "text", None)
                if isinstance(text, str) and text.strip():
                    parsed = json.loads(text)
                    if isinstance(parsed, dict):
                        return parsed
            structured = getattr(result, "structured_content", None)
            if isinstance(structured, dict):
                return structured
    return {}

#MCP 5. ADIM
#mcp parse lines adımı
def _mcp_parse_lines(user_text: str) -> OrderParseResult | None:
    #_use_mcp_for_parse() ile ORDER_FLOW_USE_MCP kontrolü yapıyor, env dosyasında ORDER_FLOW_USE_MCP=1 ise MCP kullanılır.
    if not _use_mcp_for_parse():
        return None
    try:
        #MCP 6. ADIM
        #eğer MCP kullanılıyorsa, _call_mcp_draft_order_from_text fonksiyonu çağrılır ve payload alınır.
        payload = asyncio.run(_call_mcp_draft_order_from_text(user_text))
        lines_raw = payload.get("lines") if isinstance(payload, dict) else None
        lines: list[OrderDraftLineOut] = []
        for row in lines_raw or []:
            if not isinstance(row, dict):
                continue
            name = str(row.get("name", "")).strip()
            if not name:
                continue
            qty = float(row.get("quantity", 1.0) or 1.0)
            unit = str(row.get("unit", "adet") or "adet").strip()
            lines.append(OrderDraftLineOut(name=name, quantity=qty, unit=unit))

        #MCP 8. ADIM OrdersParseResult return ediliyor ve nodes.py da state güncellenir.
        return OrderParseResult(
            #mcp parse lines adımı sonucu OrderParseResult return ediliyor ve
            # _call_mcp_draft_order_from_text fonksiyonundan gelen payload ile birleştiriliyor.
            lines=lines,
            needs_clarification=bool(payload.get("needs_clarification", False)),
            clarification_question=str(payload.get("clarification_question", "") or ""),
            user_confirmed_order=False,
            user_cancelled_order=False,
        )
    except Exception:
        return None
#mcp parse lines adımı bitiş -------------------------------------------------------------------------------------------------------------



#MCP 9. ADIM - langraphdaki nodes.py run_structured_parse 'ı tetikliyor ve llm bağlantıları devam ediyor ya da mcp kullanılıyor.
#13. Adım
#nodes.py run_structured_parse 'ı tetikliyor.
#Parse ediyor verimizi.
#LLM bağlantısı burada yapılıyor.
#run_structured_parse bitince node.py da state güncellenir.
def run_structured_parse(state: dict[str, Any]) -> dict[str, Any]:
    """
    Graf durumundan son kullanıcı mesajını ve bağlamı okuyup
    needs_clarification, draft_lines, user_confirmed_order alanlarını üretir.
    AIMessage eklemez (sonraki düğümler ekler).
    """
    messages: list[BaseMessage] = state.get("messages") or []
    text = _last_human_text(messages)
    phase = (state.get("dialogue_phase") or "collecting").strip()
    prior_drafts: list[dict] = state.get("draft_lines") or []

    # Tamamlandı / iptal sonrası yeni mesaj = yeni sipariş turu (eski taslakları yok say)
    if phase in ("completed", "cancelled") and text.strip():
        prior_drafts = []

    effective_phase = phase
    if phase in ("completed", "cancelled") and text.strip():
        effective_phase = "collecting"

    if (
        effective_phase == "awaiting_confirmation"
        and prior_drafts
        and _CANCEL_RE.match(text.strip())
    ):
        return {
            "needs_clarification": False,
            "clarification_question": "",
            "draft_lines": [],
            "user_confirmed_order": False,
            "user_cancelled_order": True,
        }

    if (
        effective_phase == "awaiting_confirmation"
        and prior_drafts
        and _CONFIRM_RE.match(text.strip())
    ):
        return {
            "needs_clarification": False,
            "clarification_question": "",
            "draft_lines": prior_drafts,
            "user_confirmed_order": True,
            "user_cancelled_order": False,
        }

    
    # MCP etkinse önce tool katmanını dene FAKAT başarısız olursa normal yolla ilerle
    parsed_from_mcp = _mcp_parse_lines(text)
    if parsed_from_mcp is not None:
        #MCP 10. ADIM _result_to_state_patch ile patch’e çevrilir
        patch = _result_to_state_patch(parsed_from_mcp)
        patch.setdefault("user_confirmed_order", False)
        patch.setdefault("user_cancelled_order", False)
        return patch

    if not has_llm_configured():
        parsed = _fallback_parse_lines(text)
        patch = _result_to_state_patch(parsed)
        patch.setdefault("user_confirmed_order", False)
        patch.setdefault("user_cancelled_order", False)
        return patch

    system = (
        "Sen mutfak sipariş asistanısın. Kullanıcı Türkçe yazar. "
        "Görev: mesajdan satın alınacak ürünleri çıkar (isim, miktar, birim). "
        "Birim: adet, kg, g, L, ml, paket gibi kısa Türkçe. "
        "Belirsiz veya ürün yoksa needs_clarification=true yap ve tek cümle Türkçe soru yaz. "
        "Konuşmada son asistan mesajı sipariş özeti ve onay sorusuysa: "
        "kullanıcı onaylıyorsa (evet, onaylıyorum, sipariş et, tamam) user_confirmed_order=true; "
        "kullanıcı reddediyorsa veya iptal ediyorsa (hayır, iptal, vazgeç, yok) user_cancelled_order=true, lines boş. "
        "Onay/iptal dışında yeni ürün yazıyorsa normal parse yap."
    )
    human = (
        f"Konuşma:\n{_format_history_for_prompt(messages)}\n\n"
        f"Son kullanıcı mesajı (odak): {text!r}"
    )
    #14. Adım
    try:
        llm = build_chat_llm()
        structured = llm.with_structured_output(OrderParseResult)
        #15. Adım
        #structured.invoke ile modele promt gönderiyor.
        #18. Adım
        # VE GELEN PROMPTU ALIYOR.
        #Nodelar bu şekilde sırayla tetiklenmeye devam ediyor. Ve -> runner.py sonucu API cevabına çevirir
        parsed: OrderParseResult = structured.invoke(
            [SystemMessage(content=system), HumanMessage(content=human)],
        )
        if not isinstance(parsed, OrderParseResult):
            parsed = OrderParseResult.model_validate(parsed)
    except Exception:
        parsed = _fallback_parse_lines(text)

    patch = _result_to_state_patch(parsed)
    if patch.get("user_cancelled_order"):
        patch["draft_lines"] = []
        patch["user_confirmed_order"] = False
        return patch
    if patch.get("user_confirmed_order") and prior_drafts and not patch.get("draft_lines"):
        patch["draft_lines"] = list(prior_drafts)
    if (
        not patch.get("user_confirmed_order")
        and not patch.get("needs_clarification")
        and not patch.get("draft_lines")
    ):
        patch["needs_clarification"] = True
        patch["clarification_question"] = (
            "Hangi ürünleri sipariş etmek istediğinizi net yazabilir misiniz?"
        )
    patch.setdefault("user_confirmed_order", False)
    patch.setdefault("user_cancelled_order", False)
    return patch


def _result_to_state_patch(parsed: OrderParseResult) -> dict[str, Any]:
    if parsed.user_cancelled_order:
        return {
            "needs_clarification": False,
            "clarification_question": "",
            "draft_lines": [],
            "user_confirmed_order": False,
            "user_cancelled_order": True,
        }
    drafts = [x.model_dump(mode="json") for x in parsed.lines]
    return {
        "needs_clarification": parsed.needs_clarification,
        "clarification_question": (parsed.clarification_question or "").strip(),
        "draft_lines": drafts,
        "user_confirmed_order": parsed.user_confirmed_order,
        "user_cancelled_order": False,
    }
