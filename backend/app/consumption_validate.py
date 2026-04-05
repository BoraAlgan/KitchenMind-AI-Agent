"""İstek envanteri ile şef çıktısındaki CONSUMPTION_JSON satırlarını birleştirip doğrular."""

from __future__ import annotations

from collections import defaultdict
from typing import Any

from app.schemas import ConsumptionLineOut, SuggestRequest


def _allowed_quantities(body: SuggestRequest) -> dict[int, float]:
    out: dict[int, float] = {}
    for it in body.items:
        if it.inventory_item_id is None or it.inventory_item_id < 1:
            continue
        out[it.inventory_item_id] = float(it.quantity)
    return out


def merge_and_validate_consumption(
    raw_rows: list[dict[str, Any]],
    body: SuggestRequest,
) -> list[ConsumptionLineOut]:
    """
    Aynı id için deltaları toplar; yalnızca istekteki id ve miktar sınırlarına uyan satırları döner.
    """
    allowed = _allowed_quantities(body)
    if not allowed or not raw_rows:
        return []

    merged: dict[int, float] = defaultdict(float)
    for row in raw_rows:
        if not isinstance(row, dict):
            continue
        try:
            iid = int(row["inventory_item_id"])
            delta = float(row["delta"])
        except (KeyError, TypeError, ValueError):
            continue
        if iid not in allowed or delta <= 0:
            continue
        merged[iid] += delta

    # Stok yetersiz olsa bile satırı tut; istemci sipariş sonrası aynı id ile yeniden dener.
    result: list[ConsumptionLineOut] = []
    for iid, total in merged.items():
        cap = allowed.get(iid)
        if cap is None or total <= 0:
            continue
        result.append(ConsumptionLineOut(inventory_item_id=iid, delta=total))
    return result
