# KitchenMind

Mutfak envanterini takip etmeyi, SKT’ye göre uyarı vermeyi ve eldeki malzemelerle **CrewAI** destekli öneri (SKT özeti, tarif, sohbet) sunmayı amaçlayan Android uygulaması ve **FastAPI** backend’i. Aynı backend’de, sipariş ekranı için **LangGraph** tabanlı doğal dil → taslak sipariş → onay akışı da vardır (`/api/v1/order-flow/step`); CrewAI kodundan ayrı pakettedir (`backend/app/order_flow/`).

## Bu repoda ne var?

| Bölüm | Açıklama |
|-------|----------|
| `app/` | Kotlin, Jetpack Compose, Room, MVI — telefon uygulaması |
| `backend/` | FastAPI: CrewAI (`/api/v1/agent/suggest`) + LangGraph sipariş (`/api/v1/order-flow/step`) |
| `CREW_AI_IMPLEMENTATION.md` | Crew tarafının kısa özeti |
| `CREW_AI_IMPLEMENTATION_REPORT.md` | Crew teslim / rapor |
| `LANGGRAPH_IMPLEMENTATION_REPORT.md` | LangGraph sipariş akışı — kısa rapor |
| `AI_Agent_Planning_Document.md` | İlk planlama belgesi (İngilizce) |

## Teknolojiler (uygulama)

- Kotlin, Jetpack Compose (Material 3), MVI, Room, Coroutines / Flow, Navigation, Retrofit (CrewAI + sipariş LangGraph API)

## Android’i çalıştırma

1. **Android Studio** (JDK 17) ile bu klasörü açın.
2. Gradle senkronize olsun; emülatör veya cihaz seçin (**Run**).

**Asistan / tarif / SKT** ve **Sipariş → AI ile sipariş** için backend’in ayakta olması gerekir.

### API adresi

Emülatör varsayılanı genelde `http://10.0.2.2:8000` olacak şekilde yapılandırılabilir. Kendi IP’niz için proje kökünde `local.properties`:

```properties
kitchenmind.api.baseUrl=http://192.168.x.x:8000
```

(Sonunda `/` olmasın.)

## Backend’i çalıştırma

Ayrıntılı kurulum, `.env`, Windows notları:

**[backend/README.md](./backend/README.md)**

Kısa özet:

```bash
cd backend
python -m venv .venv
# sanal ortamı açıp:
pip install -r requirements.txt
cp .env.example .env   # anahtarı doldurun
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Dokümantasyon

- **[backend/README.md](./backend/README.md)** — API, CrewAI, LangGraph sipariş, ortam (LangSmith dahil)  
- **[CREW_AI_IMPLEMENTATION.md](./CREW_AI_IMPLEMENTATION.md)** — Crew mimari özeti  
- **[CREW_AI_IMPLEMENTATION_REPORT.md](./CREW_AI_IMPLEMENTATION_REPORT.md)** — Crew raporu  
- **[LANGGRAPH_IMPLEMENTATION_REPORT.md](./LANGGRAPH_IMPLEMENTATION_REPORT.md)** — LangGraph düğümler / kenarlar özeti  
- **[AI_Agent_Planning_Document.md](./AI_Agent_Planning_Document.md)** — erken dönem planlama  
