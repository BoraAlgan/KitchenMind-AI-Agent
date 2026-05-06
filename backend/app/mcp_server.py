"""KitchenMind MCP sunucusu (stdio): envanter özeti + sipariş satırı taslağı."""

from __future__ import annotations

import re
from typing import Any

from mcp.server.fastmcp import FastMCP

#mcp server adı ve oluşturulma adımı
mcp = FastMCP("kitchenmind")

_UNIT_ALIASES: dict[str, str] = {
    "adet": "adet",
    "tane": "adet",
    "ad": "adet",
    "kg": "kg",
    "kilo": "kg",
    "kilogram": "kg",
    "g": "g",
    "gr": "g",
    "gram": "g",
    "l": "L",
    "lt": "L",
    "litre": "L",
    "liter": "L",
    "ml": "ml",
    "paket": "paket",
    "pk": "paket",
}

#ilk tool adımı
@mcp.tool()
def get_inventory_summary(items: list[dict[str, Any]]) -> dict[str, Any]:
    """
    Envanter için hızlı özet döner.

    Args:
        items: [{name, quantity, unit, days_to_expiry?}, ...]
    """
    total_items = 0
    critical_items: list[str] = []
    expiring_soon: list[str] = []

    for row in items or []:
        if not isinstance(row, dict):
            continue
        name = str(row.get("name", "")).strip()
        if not name:
            continue
        total_items += 1
        qty = float(row.get("quantity", 0) or 0)
        dte = row.get("days_to_expiry", None)
        if qty <= 0:
            critical_items.append(name)
        try:
            if dte is not None and int(dte) <= 2:
                expiring_soon.append(name)
        except Exception:
            pass

    return {
        "total_items": total_items,
        "critical_items": sorted(set(critical_items)),
        "expiring_soon": sorted(set(expiring_soon)),
    }

#ikinci tool adımı
@mcp.tool()
def draft_order_from_text(user_message: str) -> dict[str, Any]:
    """
    Serbest metinden sipariş satırı taslağı üretir (kural tabanlı hızlı sürüm).

    Args:
        user_message: Örn "2 ayran, 1 lahmacun"
    """
    text = (user_message or "").strip()
    if not text:
        return {
            "lines": [],
            "needs_clarification": True,
            "clarification_question": "Ne sipariş etmek istediğinizi yazabilir misiniz?",
        }

    parts = re.split(r"[\n,;]+|\s+ve\s+", text, flags=re.IGNORECASE)
    lines: list[dict[str, Any]] = []
    unit_pattern = "|".join(sorted((re.escape(x) for x in _UNIT_ALIASES), key=len, reverse=True))

    for raw in parts:
        chunk = raw.strip()
        if len(chunk) < 2:
            continue
        # "2 ayran", "1.5 kg domates", "3 litre süt", "5 kilo dana eti"
        m = re.match(
            rf"^\s*(?P<qty>\d+(?:[.,]\d+)?)?\s*(?P<unit>{unit_pattern})?\s*(?P<name>.+?)\s*$",
            chunk,
            flags=re.IGNORECASE,
        )
        if not m:
            continue
        qty_raw = m.group("qty")
        unit_raw = m.group("unit")
        name = (m.group("name") or "").strip()
        if not name:
            continue
        qty = float((qty_raw or "1").replace(",", "."))
        unit_key = (unit_raw or "adet").strip().lower()
        unit = _UNIT_ALIASES.get(unit_key, "adet")
        if unit_raw is None and qty != 1.0:
            # "2 elma" gibi ifadelerde miktar varsa birimi adet varsay.
            unit = "adet"
        lines.append({"name": name[:200], "quantity": qty, "unit": unit})

    if not lines:
        return {
            "lines": [],
            "needs_clarification": True,
            "clarification_question": "Ürün isimlerini net yazar mısınız? Örn: 2 ayran, 1 lahmacun",
        }

    return {
        "lines": lines,
        "needs_clarification": False,
        "clarification_question": "",
    }


if __name__ == "__main__":
    mcp.run(transport="stdio")
