"""LangChain ChatOpenAI — CrewAI `LLM` sınıfı kullanılmaz; ortam değişkenleri crew ile uyumludur."""

from __future__ import annotations

import os
from typing import Any
from urllib.parse import urlparse

from langchain_openai import ChatOpenAI


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


def _normalize_groq_base_url(base: str) -> str:
    raw = base.strip().rstrip("/")
    if "api.groq.com" not in raw.lower():
        return base.strip()
    if raw.lower().endswith("/openai/v1"):
        return raw
    p = urlparse(raw if "://" in raw else f"https://{raw}")
    scheme = p.scheme if p.scheme in ("http", "https") else "https"
    return f"{scheme}://{p.netloc}/openai/v1"


def _llm_model() -> str:
    return (os.getenv("LLM_MODEL") or "gpt-4o-mini").strip()

#16. Adım
#api key ve / base model burada set ediliyor
def build_chat_llm() -> ChatOpenAI:
    """OpenAI uyumlu uç (Groq, OpenRouter vb.) — anahtar yoksa ValueError."""
    key = _api_key()
    if not key:
        raise ValueError(
            "LLM anahtarı yok. Ortam: LLM_API_KEY, GROQ_API_KEY veya OPENAI_API_KEY.",
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
    model = _llm_model()
    kw: dict[str, Any] = {
        "model": model,
        "api_key": key,
        "temperature": 0.2,
    }
    if base:
        kw["base_url"] = base
        #17. Adım
        #parsing.py, llm.py de kurulan ChatOpenAI istemcisini kullanarak parse aşamasında LLM'e istek atıyor
    return ChatOpenAI(**kw)


def has_llm_configured() -> bool:
    return _api_key() is not None
