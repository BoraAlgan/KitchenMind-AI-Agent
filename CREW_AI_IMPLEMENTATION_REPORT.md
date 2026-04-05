# Crew AI Implementation Report — KitchenMind

**Öğrenci:** [Ad Soyad]  
**Ders:** [Ders kodu]  
**Teslim tarihi:** [Tarih]  
**Git deposu (inceleme için):** [https://github.com/kullanici/KitchenMind veya GitLab URL]

---

## 1. Özet

KitchenMind, mutfak envanterini takip eden bir Android uygulamasıdır (önceki ödev). Bu çalışmada uygulamaya **CrewAI** tabanlı bir **FastAPI** backend eklendi. Model telefonda çalışmaz; uygulama envanteri JSON ile `POST /api/v1/agent/suggest` uç noktasına gönderir, sunucu Crew’u çalıştırır, dönen metin ve yapılandırılmış alanlar (`consumption`, `missing_items`) istemcide gösterilir veya onay sonrası veritabanına yansır.

---

## 2. Mimari

1. **Android:** `InventoryViewModel` → Retrofit ile `SuggestRequestDto` (envanter + `suggestMode` + isteğe bağlı mesaj + `clientTimeZone`).
2. **FastAPI:** `app/main.py` içinde `agent_suggest`; `asyncio.to_thread(run_kitchen_suggestion, body)` ile Crew bloklayıcı çağrı ayrı iş parçacığında çalışır (120 sn zaman aşımı).
3. **CrewAI:** `app/crew_kitchen.py` içinde moda göre `_run_chat_mode`, `_run_inventory_only` veya `_run_recipe_crew`.

**Kaynak dosyalar:** `backend/app/crew_kitchen.py`, `backend/app/main.py`, `backend/app/schemas.py`, `backend/app/consumption_validate.py`.

---

## 3. Ajanlar (Agents)

| Ajan | `role` | Görevi |
|------|--------|--------|
| Sohbet asistanı | `Mutfak sohbet asistanı` | `chat` modunda kısa Türkçe yanıt; zorunlu değilse envanteri uzun listelemez. |
| Envanter analisti | `Envanter Analisti` | SKT / öncelik özeti (`inventory_only` ve `recipe`’nin ilk adımı). |
| Mutfak şefi | `Mutfak Şefi` | `recipe` modunda tarif; çıktıda `MISSING_ITEMS_JSON` ve `CONSUMPTION_JSON` satırları. |

Tüm ajanlarda `allow_delegation=False`, `verbose=False`. LLM örneği `build_llm()` ile CrewAI `LLM` sınıfından üretilir (OpenAI uyumlu uç, Groq/OpenRouter desteği ortam değişkenleriyle).

---

## 4. LLM oluşturma (kısa snippet)

`backend/app/crew_kitchen.py` — ortam anahtarı ve model:

```python
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
```

---

## 5. Giriş noktası ve mod seçimi

`run_kitchen_suggestion` isteğe göre üç akıştan birini çağırır:

```python
def run_kitchen_suggestion(body: SuggestRequest) -> SuggestResponse:
    mode = resolve_suggest_mode(body)
    if mode == SuggestMode.chat:
        return _run_chat_mode(body)
    if mode == SuggestMode.inventory_only:
        return _run_inventory_only(body)
    return _run_recipe_crew(body)
```

`resolve_suggest_mode`: gövdede `suggest_mode` doluysa onu kullanır; boşsa mesaj yoksa `inventory_only`, varsa `chat`.

---

## 6. Mod A — Sohbet (`chat`): ajan, görev, Crew, kickoff

```python
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
```

---

## 7. Mod B — SKT / envanter özeti (`inventory_only`): ajan, görev, Crew, kickoff

Analist tek ajan; süresi dolmuş ürünler için sunucu tarafı metin (`_forced_expired_skus_block`) görev açıklamasına ve kullanıcı yanıtına eklenebilir.

```python
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
```

---

## 8. Mod C — Tarif (`recipe`): iki ajan, iki görev, bağlam, Crew, kickoff

**Ajanlar**

```python
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
```

**Görev 1 — analist** (`analyze_task`, tarif modunda; `inv_public`, `user_note`, `forced_recipe_prefix` çalışma anında dolar):

```python
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
```

**Görev 2 — şef** (`cook_task`): `context=[analyze_task]` ile analist çıktısına erişir; `inv_ids` envanterde satır başı `id=` içerir.

```python
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
```

**Crew ve kickoff; yanıt işleme**

```python
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
```

Kullanıcıya gösterilen metin esasen şefin temizlenmiş tarif metnidir; analist özeti ayrıca kullanıcıya iletilmez (şef görevi analist bağlamını içeriden kullanır).

---

## 9. FastAPI uç noktası (Crew çağrısı)

`backend/app/main.py`:

```python
@app.post(
    "/api/v1/agent/suggest",
    response_model=SuggestResponse,
    status_code=status.HTTP_200_OK,
)
async def agent_suggest(body: SuggestRequest) -> SuggestResponse:
    """
    Envanter + isteğe bağlı kullanıcı mesajı → sıralı CrewAI (Analist → Şef).
    Zaman aşımı: 120 sn. Anahtarlar yalnızca ortam değişkenlerinden.
    """
    if not _api_key():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "LLM yapılandırılmadı. `LLM_API_KEY`, `GROQ_API_KEY` veya "
                "`OPENAI_API_KEY` ayarlayın (bkz. backend/.env.example)."
            ),
        )

    try:
        return await asyncio.wait_for(
            asyncio.to_thread(run_kitchen_suggestion, body),
            timeout=SUGGEST_TIMEOUT_SEC,
        )
    except asyncio.TimeoutError:
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail=f"Öneri {int(SUGGEST_TIMEOUT_SEC)} saniye içinde tamamlanamadı.",
        ) from None
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(e),
        ) from e
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"CrewAI/LLM çağrısı başarısız: {e!s}",
        ) from e
```

---

## 10. Yapılandırma dosyaları (cfg)

### 10.1 `backend/requirements.txt`

```
# Python 3.10+ önerilir. tzdata: Windows'ta IANA saat dilimleri (ör. Europe/Istanbul).
tzdata>=2024.1
fastapi==0.135.3
uvicorn[standard]==0.34.0
python-dotenv==1.1.1
crewai[litellm]==1.13.0
langchain-openai==0.3.35
```

### 10.2 `backend/.env.example`

Gerçek anahtarları repoya koyma; yerelde `.env` olarak kopyala.

```
# Bu dosyayı kopyalayıp `backend/.env` yapın; gerçek anahtarları repoya eklemeyin.

# OpenAI uyumlu API anahtarı: LLM_API_KEY (tercih), veya GROQ_API_KEY / OPENAI_API_KEY.
LLM_API_KEY=

# Örnek: gpt-4o-mini | Groq: llama-3.1-8b-instant veya qwen/qwen3-32b (konsoldaki tam id)
LLM_MODEL=gpt-4o-mini

# Groq: https://api.groq.com/openai/v1 (veya GROQ_API_BASE). Anahtar gsk_ ile başlıyorsa boş bırakılabilir.
# OPENAI_API_BASE=
```

---

## 11. Ekran görüntüleri ve açıklamalar

*[Aşağıya PDF/Word aktarırken kendi görsellerini yapıştır. Her görselin altına 2–4 cümle yaz.]*

### Görsel 1 — [Örn. Android Asistan, SKT veya tarif yanıtı]

**Açıklama:** [Bu ekranda … görünüyor. İstek `inventory_only` / `recipe` / `chat` modunda gitti. Mor balon Crew/LLM yanıtıdır.]

### Görsel 2 — [Örn. Swagger `/docs` veya çalışan backend terminali]

**Açıklama:** [Sunucunun ayakta olduğunu ve `POST /api/v1/agent/suggest` ile test edildiğini gösterir.]

---

## 12. Sonuç

CrewAI, KitchenMind backend’inde üç modla entegre edildi: sohbet (tek ajan), envanter/SKT özeti (tek ajan), tarif (analist → şef sıralı, JSON ayrıştırma). Yapılandırma `requirements.txt` ve `.env` ile yapılır; Android istemcisi aynı API’yi kullanır.

**Tam kaynak:** `backend/app/crew_kitchen.py` (`_SKT_ANALYST_RULES_TR` ve yardımcı fonksiyonlar dahil).
