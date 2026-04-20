"""LangGraph derlemesi — CrewAI'den bağımsız; düğümler ve koşullu kenarlar."""

from __future__ import annotations

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from app.order_flow.nodes import (
    node_compose_confirmation_prompt,
    node_emit_cancellation,
    node_emit_clarification,
    node_finalize_confirmed_order,
    node_parse_order_lines,
    route_after_parse,
)
from app.order_flow.state import OrderFlowState


def build_order_flow_graph():
    """
    Akış:
      START → parse_order_lines → [koşullu] → emit_cancellation | finalize_confirmed_order | emit_clarification | compose_confirmation_prompt → END
    """
    g = StateGraph(OrderFlowState)
    g.add_node("parse_order_lines", node_parse_order_lines)
    g.add_node("emit_cancellation", node_emit_cancellation)
    g.add_node("emit_clarification", node_emit_clarification)
    g.add_node("compose_confirmation_prompt", node_compose_confirmation_prompt)
    g.add_node("finalize_confirmed_order", node_finalize_confirmed_order)

    g.add_edge(START, "parse_order_lines")
    g.add_conditional_edges(
        "parse_order_lines",
        route_after_parse,
        {
            "emit_cancellation": "emit_cancellation",
            "finalize_confirmed": "finalize_confirmed_order",
            "emit_clarification": "emit_clarification",
            "compose_confirmation_prompt": "compose_confirmation_prompt",
        },
    )
    g.add_edge("emit_cancellation", END)
    g.add_edge("emit_clarification", END)
    g.add_edge("compose_confirmation_prompt", END)
    g.add_edge("finalize_confirmed_order", END)

    checkpointer = MemorySaver()
    return g.compile(checkpointer=checkpointer)


_compiled = build_order_flow_graph()


def get_compiled_order_flow_graph():
    """Tekil derlenmiş graf (uvicorn worker başına bir örnek)."""
    return _compiled
