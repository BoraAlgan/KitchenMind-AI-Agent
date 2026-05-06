"""FastAPI ile köprü: thread_id + kullanıcı mesajı → graf çağrısı → API yanıtı."""

from __future__ import annotations

from langchain_core.messages import HumanMessage
from langchain_core.runnables import RunnableConfig

from app.order_flow.graph import get_compiled_order_flow_graph
from app.order_flow.nodes import last_assistant_text
from app.schemas import OrderDraftLineOut, OrderFlowStepResponse, OrderFlowStatus

#20. Adım
#dialog_phase -> API Status maplenir
def _phase_to_api_status(phase: str | None) -> OrderFlowStatus:
    p = (phase or "collecting").strip().lower()
    if p == "awaiting_confirmation":
        return OrderFlowStatus.awaiting_confirmation
    if p == "completed":
        return OrderFlowStatus.completed
    if p == "cancelled":
        return OrderFlowStatus.cancelled
    return OrderFlowStatus.collecting


def _draft_lines_from_state(raw: list | None) -> list[OrderDraftLineOut]:
    out: list[OrderDraftLineOut] = []
    for row in raw or []:
        if not isinstance(row, dict):
            continue
        try:
            name = str(row.get("name", "")).strip()
            qty = float(row.get("quantity", 0))
            unit = str(row.get("unit", "adet")).strip() or "adet"
            if len(name) < 1 or qty <= 0:
                continue
            out.append(OrderDraftLineOut(name=name, quantity=qty, unit=unit))
        except Exception:
            continue
    return out

#main.py buraya pasladı veriyi
def run_order_flow_step(thread_id: str, user_message: str) -> OrderFlowStepResponse:
    """
    Aynı thread_id ile ardışık çağrılar checkpointer'da birikir.
    draft_lines ve status graf durumundan okunur.
    """
    text = user_message.strip()
    graph = get_compiled_order_flow_graph()
    config: RunnableConfig = {
        "configurable": {"thread_id": thread_id},
        "tags": ["order-flow", "langgraph", "kitchenmind"],
        "metadata": {
            "feature": "order_flow",
            "thread_id": thread_id,
        },
    }
    #9. Adım
    #graphın çalışması için INVOKE ediyoruz! graph.py dosyamızı tetikliyoruz
    result = graph.invoke(
        {"messages": [HumanMessage(content=text)]},
        config,
    )
    #19. Adım
    #runner.py resultı API cevabına çevirir ve draftlines dönüştürülür
    messages = result.get("messages") or []
    reply = last_assistant_text(messages)
    phase = result.get("dialogue_phase")
    drafts = _draft_lines_from_state(result.get("draft_lines"))

    #21. Adım
    #Viewmodele paslanır InventoryViewModel.kt -> sendOrderFlowMessage
    return OrderFlowStepResponse(
        thread_id=thread_id,
        message=reply or "(Yanıt üretilemedi.)",
        draft_lines=drafts,
        status=_phase_to_api_status(phase if isinstance(phase, str) else None),
    )
