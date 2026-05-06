"""KitchenMind FastAPI: /health, /api/v1/agent/suggest (CrewAI), /api/v1/order-flow/step (LangGraph)."""

from __future__ import annotations

from dotenv import load_dotenv

# LangSmith ve LLM anahtarları; `app.order_flow` içe aktarılmadan önce yüklensin.
load_dotenv()

import asyncio
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.crew_kitchen import _api_key, run_kitchen_suggestion
from app.order_flow import run_order_flow_step
from app.schemas import OrderFlowStepRequest, OrderFlowStepResponse, SuggestRequest, SuggestResponse

SUGGEST_TIMEOUT_SEC = 120.0
ORDER_FLOW_TIMEOUT_SEC = 60.0


@asynccontextmanager
async def lifespan(_app: FastAPI):
    yield


app = FastAPI(
    title="KitchenMind Agent API",
    version="0.2.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", status_code=status.HTTP_200_OK)
def health() -> dict[str, str]:
    return {"status": "ok"}


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

#8. Adım
#Backend gelen isteği buradan karşılıyor.
@app.post(
    "/api/v1/order-flow/step",
    response_model=OrderFlowStepResponse,
    response_model_by_alias=True,
    status_code=status.HTTP_200_OK,
)
async def order_flow_step(body: OrderFlowStepRequest) -> OrderFlowStepResponse:
    """
    LangGraph sipariş diyaloğu (Sprint 1: stub düğüm, LLM zorunlu değil).
    `threadId` ile aynı oturumda çok turlu konuşma (bellek içi checkpoint).
    """
    raw_tid = body.thread_id
    tid = raw_tid.strip() if isinstance(raw_tid, str) else ""
    thread_id = tid if tid else str(uuid.uuid4())

    try:
        #9. Adım
        #gelen request burada runner.py dosyası içindeki run_order_flow_step e devrediliyor.
        return await asyncio.wait_for(
            asyncio.to_thread(run_order_flow_step, thread_id, body.user_message),
            timeout=ORDER_FLOW_TIMEOUT_SEC,
        )
    except asyncio.TimeoutError:
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail=f"Sipariş akışı {int(ORDER_FLOW_TIMEOUT_SEC)} saniye içinde tamamlanamadı.",
        ) from None
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"LangGraph çağrısı başarısız: {e!s}",
        ) from e
