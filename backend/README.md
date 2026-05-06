# KitchenMind backend — CrewAI + Langraph

FastAPI sunucusu. CrewAI ile sıralı ajan akışı: envanter özeti (analist), isteğe bağlı tarif + JSON (şef). Sohbet modunda tek ajan. LLM, OpenAI uyumlu uç noktalar (Groq, OpenRouter vb.) ile bağlanır.

Özet mimari ve ajan tablosu için proje kökündeki `CREW_AI_IMPLEMENTATION.md` dosyasına bakın.

## Gereksinimler

Python 3.10 veya 3.11.

## Kurulum

```bash
cd backend
python -m venv .venv
```

Windows (PowerShell):

```powershell
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

`Activate.ps1` çalışmıyorsa (ExecutionPolicy):

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

İsteğe bağlı: `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`

## Çalıştırma

Sanal ortam açıkken:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Aktif değilse (Windows):

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Sağlık: `GET http://127.0.0.1:8000/health`
- Öneri (CrewAI): `POST http://127.0.0.1:8000/api/v1/agent/suggest`
- Sipariş diyaloğu (LangGraph, CrewAI’den ayrı kod: `app/order_flow/`): `POST http://127.0.0.1:8000/api/v1/order-flow/step`
- OpenAPI: `http://127.0.0.1:8000/docs`

### `suggest` gövdesi (özet)

`items`: envanter satırları (isim, miktar, birim, isteğe bağlı SKT, `inventoryItemId`).

`suggestMode`: `chat` | `inventory_only` | `recipe`. Boşsa: kullanıcı mesajı yoksa `inventory_only`, varsa `chat`.

Tarif modunda yanıtta `consumption` ve `missing_items` dolabilir.

**Stok düşümü:** İstekteki `inventoryItemId` (Room id) ile şefin `CONSUMPTION_JSON` çıktısı sunucuda doğrulanır; yanıttaki `consumption` güvenilir satırları içerir.

**Eksik malzemeler:** `missing_items` şefin `MISSING_ITEMS_JSON` satırından gelir. Android’de liste, kopyala/paylaş ve demo sipariş sepeti ile kullanılır.

### `order-flow/step` gövdesi (LangGraph)

Kod `app/order_flow/` altında; CrewAI (`app/crew_kitchen.py`) ile aynı dosyada değildir.

- `userMessage` (zorunlu): kullanıcı mesajı.
- `threadId` (isteğe bağlı): aynı sohbet için sabit UUID; gönderilmezse sunucu yeni `threadId` üretir ve yanıtta döner.

**Graf:** `parse_order_lines` → koşullu kenar → `emit_cancellation` | `finalize_confirmed_order` | `emit_clarification` | `compose_confirmation_prompt` → END.

- Ürün listesi çıkarımı: `LLM_API_KEY` (veya Groq/OpenAI) varsa yapılandırılmış LLM çıktısı; yoksa basit metin bölme (virgül / ` ve ` / satır sonu).
- MCP (opsiyonel): `ORDER_FLOW_USE_MCP=1` ise parse adımı önce `app.mcp_server` içindeki `draft_order_from_text` tool'unu stdio ile çağırır; başarısızlıkta mevcut parse mantığına geri düşer (akış bozulmaz).
- `draftLines`: taslak sepet satırları (`name`, `quantity`, `unit`).
- `status`: `collecting` | `awaiting_confirmation` | `completed` (onay sonrası) | `cancelled` (onay sorusunda iptal: hayır, iptal, vazgeç vb.).
- `completed` veya `cancelled` sonrası yeni mesaj yeni sipariş turu olarak işlenir.

Zaman aşımı **60 saniye**; hata **502**.

## Android emülatör

Emülatörde `localhost` → `10.0.2.2`. Örnek: `http://10.0.2.2:8000`

Fiziksel cihazda aynı ağdaki bilgisayarın LAN IP’sini kullanın.

Uygulama varsayılan olarak emülatör adresini kullanır. Değiştirmek için proje kökünde `local.properties`:

`kitchenmind.api.baseUrl=http://192.168.x.x:8000`  
(sonunda `/` olmasın)

## Ortam değişkenleri

`.env.example` dosyasını `.env` olarak kopyalayın.

- `LLM_API_KEY` veya `OPENAI_API_KEY`: zorunlu; yoksa öneri uç noktası **503** döner.
- `LLM_MODEL`: isteğe bağlı (ör. `gpt-4o-mini`).
- `ORDER_FLOW_USE_MCP`: `1/true/yes/on` yaparsanız sipariş parse adımı MCP tool katmanını dener.
- `ORDER_FLOW_MCP_PYTHON`: MCP stdio sunucusu için Python komutu (varsayılan: `python`).
- `crewai[litellm]` ile OpenRouter / `qwen/...` gibi modeller kullanılabilir; paket eksikse CrewAI LiteLLM uyarısı verebilir.

İstek süresi yaklaşık **120 saniye**; aşımda **504**. Crew/LLM hatasında **502** ve `detail` mesajı.

## CORS

Geliştirmede tüm kökenlere izin var. Canlı ortamda `app/main.py` içindeki `CORSMiddleware` kısıtlanmalı.
