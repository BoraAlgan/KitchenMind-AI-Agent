"""Sipariş LangGraph durum şeması — mesajlar + taslak satırlar + diyalog aşaması."""

from __future__ import annotations

from typing import Annotated, Any

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages
from typing_extensions import TypedDict


class OrderFlowState(TypedDict, total=False):
    """
    Checkpointer ile çok turlu konuşma.
    `messages`: add_messages ile birikir.
    Diğer alanlar düğüm çıktılarında güncellenir (reducer yok → son yazım geçerli).
    """

    messages: Annotated[list[BaseMessage], add_messages]
    needs_clarification: bool
    clarification_question: str
    draft_lines: list[dict[str, Any]]
    dialogue_phase: str
    user_confirmed_order: bool
    user_cancelled_order: bool
