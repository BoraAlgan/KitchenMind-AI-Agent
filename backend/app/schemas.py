"""API istek/yanıt modelleri (Android DTO ile uyumlu)."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class SuggestMode(str, Enum):
    """POST suggest isteğinde hangi akışın çalışacağı."""

    chat = "chat"
    inventory_only = "inventory_only"
    recipe = "recipe"


class InventoryItemIn(BaseModel):
    """Tek envanter satırı (Android ile camelCase uyumlu)."""

    model_config = ConfigDict(populate_by_name=True)

    name: str = Field(..., min_length=1, max_length=200)
    quantity: float = Field(..., gt=0)
    unit: str = Field(..., min_length=1, max_length=32)
    expiry_date: int | None = Field(
        default=None,
        alias="expiryDate",
        description="SKT: Unix epoch saniye veya ms (ms ise sunucu saniyeye çevirir)",
    )
    category_name: str | None = Field(
        default=None,
        alias="categoryName",
        max_length=100,
    )
    inventory_item_id: int | None = Field(
        default=None,
        alias="inventoryItemId",
        ge=1,
        description="İstemci (Room) birincil anahtarı; tüketim satırlarında doğrulama için.",
    )

    @field_validator("name", "unit", "category_name", mode="before")
    @classmethod
    def strip_strings(cls, v: str | None) -> str | None:
        if v is None:
            return None
        if isinstance(v, str):
            s = v.strip()
            return s if s else None
        return v


class SuggestRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    items: list[InventoryItemIn] = Field(default_factory=list)
    user_message: str | None = Field(default=None, max_length=4000)
    suggest_mode: SuggestMode | None = Field(
        default=None,
        alias="suggestMode",
        description="chat | inventory_only | recipe. Boşsa: mesaj yoksa inventory_only, varsa chat.",
    )
    recipe_followup_context: str | None = Field(
        default=None,
        alias="recipeFollowupContext",
        max_length=6000,
        description="chat modunda: son «Tarif öner» metni özeti; model bağlamı korur.",
    )
    client_time_zone: str | None = Field(
        default=None,
        alias="clientTimeZone",
        max_length=64,
        description="IANA saat dilimi (örn. Europe/Istanbul); SKT günü kullanıcı yereline göre hesaplanır.",
    )

    @field_validator("user_message", mode="before")
    @classmethod
    def strip_user_message(cls, v: str | None) -> str | None:
        if v is None:
            return None
        if isinstance(v, str):
            s = v.strip()
            return s if s else None
        return v

    @field_validator("recipe_followup_context", mode="before")
    @classmethod
    def strip_recipe_context(cls, v: str | None) -> str | None:
        if v is None:
            return None
        if isinstance(v, str):
            s = v.strip()
            return s if s else None
        return v


class MissingItemOut(BaseModel):
    name: str
    suggested_quantity: float
    unit: str


class ConsumptionLineOut(BaseModel):
    inventory_item_id: int
    delta: float


class SuggestResponse(BaseModel):
    message: str
    consumption: list[ConsumptionLineOut] = Field(default_factory=list)
    missing_items: list[MissingItemOut] = Field(default_factory=list)


# --- LangGraph sipariş akışı (CrewAI suggest’ten ayrı; app/order_flow/) ---


class OrderFlowStatus(str, Enum):
    """Sipariş diyaloğu aşaması (Android ile aynı string değerler)."""

    collecting = "collecting"
    awaiting_confirmation = "awaiting_confirmation"
    completed = "completed"
    cancelled = "cancelled"


class OrderDraftLineOut(BaseModel):
    """Sepet satırı — Android `OrderCartLine` ile uyumlu."""

    model_config = ConfigDict(populate_by_name=True)

    name: str = Field(..., min_length=1, max_length=200)
    quantity: float = Field(..., gt=0)
    unit: str = Field(..., min_length=1, max_length=32)


class OrderFlowStepRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str | None = Field(
        default=None,
        alias="threadId",
        max_length=128,
        description="İstemci UUID; boşsa sunucu ilk yanıtta thread_id üretir (önerilir: istemci üretsin).",
    )
    user_message: str = Field(
        ...,
        alias="userMessage",
        min_length=1,
        max_length=4000,
        description="Kullanıcının sipariş / onay metni.",
    )

    @field_validator("user_message", mode="before")
    @classmethod
    def strip_user_message_order(cls, v: str) -> str:
        if isinstance(v, str):
            s = v.strip()
            if not s:
                raise ValueError("user_message boş olamaz")
            return s
        return v


class OrderFlowStepResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str = Field(..., alias="threadId")
    message: str
    draft_lines: list[OrderDraftLineOut] = Field(
        default_factory=list,
        alias="draftLines",
    )
    status: OrderFlowStatus
