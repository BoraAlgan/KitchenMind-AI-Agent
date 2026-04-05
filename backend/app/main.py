"""KitchenMind FastAPI: /health ve /api/v1/agent/suggest."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.crew_kitchen import _api_key, run_kitchen_suggestion
from app.schemas import SuggestRequest, SuggestResponse

load_dotenv()

SUGGEST_TIMEOUT_SEC = 120.0


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
