"""LangGraph düğümleri — isimler akışı anlatır: parse → (onay / netleştir / özet)."""

from __future__ import annotations

from typing import Any, Literal

from langchain_core.messages import AIMessage, BaseMessage

from app.order_flow.parsing import run_structured_parse
from app.order_flow.state import OrderFlowState

#11. Adım
#İlk node olan node_parse_order_lines tetikleniyor.
#nodes.py çalışmaya başlıyor
#nodes.py parsing.py tetikliyor
def node_parse_order_lines(state: OrderFlowState) -> dict[str, Any]:
    """Serbest metinden yapılandırılmış satırlar ve yönlendirme bayrakları."""
    #12. Adım
    #parinsg.py tetiklendiği kısım
    return run_structured_parse(state)


#node_parse_order_lines bitince route after parse çalışır.
#bu bütün nodeları gerçekleştirir ve graph.py END' gider
def route_after_parse(
    state: OrderFlowState,
        #emit_clarification -> netleştirme sorusu
        #compose_confirmation_prompt -> tasklak + onay sorusu
        #emit_cancellation -> iptal mesajı
        #finalize_confirmed -> tamamlandı mesajı
) -> Literal[
    "emit_cancellation",
    "finalize_confirmed",
    "emit_clarification",
    "compose_confirmation_prompt",
]:
    """Koşullu kenar: önceki düğüm çıktısına göre sonraki düğüm adı."""
    if state.get("user_cancelled_order"):
        return "emit_cancellation"
    if state.get("user_confirmed_order"):
        return "finalize_confirmed"
    if state.get("needs_clarification"):
        return "emit_clarification"
    return "compose_confirmation_prompt"


def node_emit_cancellation(_state: OrderFlowState) -> dict[str, Any]:
    """Onay beklenirken iptal; taslak temizlenir, diyalog sonlanır."""
    msg = (
        "Sipariş iptal edildi. İstediğiniz zaman yeni ürünleri yazarak yeniden başlayabilirsiniz."
    )
    return {
        "messages": [AIMessage(content=msg)],
        "dialogue_phase": "cancelled",
        "draft_lines": [],
        "user_confirmed_order": False,
        "user_cancelled_order": False,
    }


def node_emit_clarification(state: OrderFlowState) -> dict[str, Any]:
    """Netleştirme sorusunu kullanıcıya iletir; sepet henüz kesin değil."""
    q = (state.get("clarification_question") or "").strip()
    if not q:
        q = "Ne sipariş etmek istediğinizi yazabilir misiniz?"
    return {
        "messages": [AIMessage(content=q)],
        "dialogue_phase": "collecting",
    }


def node_compose_confirmation_prompt(state: OrderFlowState) -> dict[str, Any]:
    """Taslak satırları listeler ve onay ister."""
    drafts: list[dict[str, Any]] = state.get("draft_lines") or []
    lines_txt = "\n".join(
        f"- {d.get('name', '?')}: {d.get('quantity', 0)} {d.get('unit', '')}".strip()
        for d in drafts
    )
    body = (
        f"Sipariş taslağınız:\n{lines_txt}\n\n"
        "Bu listeyi onaylıyor musunuz? "
        "(evet / onaylıyorum / sipariş et — veya listeyi yazarak düzeltin.)"
    )
    return {
        "messages": [AIMessage(content=body)],
        "dialogue_phase": "awaiting_confirmation",
    }


def node_finalize_confirmed_order(state: OrderFlowState) -> dict[str, Any]:
    """Kullanıcı onayı alındı; istemci aynı draft_lines ile envantere yazabilir."""
    drafts: list[dict[str, Any]] = state.get("draft_lines") or []
    lines_txt = "\n".join(
        f"- {d.get('name', '?')}: {d.get('quantity', 0)} {d.get('unit', '')}".strip()
        for d in drafts
    )
    msg = (
        f"Siparişiniz alındı (demo).\n{lines_txt}\n\n"
        "Uygulama bu kalemleri envantere ekleyebilir."
    )
    return {
        "messages": [AIMessage(content=msg)],
        "dialogue_phase": "completed",
        "user_confirmed_order": False,
        "user_cancelled_order": False,
    }


def last_assistant_text(messages: list[BaseMessage]) -> str:
    """API yanıtı için son asistan mesajı."""
    for m in reversed(messages or []):
        if isinstance(m, AIMessage):
            c = m.content
            return c if isinstance(c, str) else str(c)
    return ""
