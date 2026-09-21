from __future__ import annotations

from datetime import datetime

import pandas as pd
from fastapi import APIRouter
from pydantic import BaseModel, Field

try:
    from .recommendation_poc.recommender import recommend
except ImportError:
    from recommendation_poc.recommender import recommend


router = APIRouter(prefix="/api/v1/recommendations", tags=["recommendations"])


class Candidate(BaseModel):
    group_buy_id: int
    product_id: int
    brand: str | None = None
    category_id: int | None = None
    recruitment_rate: float = Field(ge=0, le=1)
    discount_rate: float = Field(ge=0, le=1)
    deadline_urgency: float = Field(ge=0, le=1)


class Event(BaseModel):
    user_id: int
    group_buy_id: int
    product_id: int
    event_type: str
    occurred_at: datetime
    brand: str | None = None
    category_id: int | None = None
    recruitment_rate: float = Field(ge=0, le=1)
    discount_rate: float = Field(ge=0, le=1)
    deadline_urgency: float = Field(ge=0, le=1)


class RankRequest(BaseModel):
    target_user_id: int
    candidates: list[Candidate]
    events: list[Event] = Field(default_factory=list)
    limit: int = Field(default=8, ge=1, le=100)


class RankedItem(BaseModel):
    group_buy_id: int
    score: float
    reason: str


class RankResponse(BaseModel):
    model_type: str
    ranked_items: list[RankedItem]
    feature_weights: dict[str, float]


@router.post("/rank", response_model=RankResponse)
def rank(request: RankRequest) -> RankResponse:
    if not request.candidates:
        return RankResponse(model_type="EMPTY", ranked_items=[], feature_weights={})

    candidates = pd.DataFrame(candidate.model_dump() for candidate in request.candidates)
    events = pd.DataFrame(event.model_dump() for event in request.events)
    if events.empty:
        events = pd.DataFrame(
            columns=[
                "user_id",
                "group_buy_id",
                "product_id",
                "event_type",
                "occurred_at",
                "brand",
                "category_id",
                "recruitment_rate",
                "discount_rate",
                "deadline_urgency",
            ]
        )
    result = recommend(candidates, events, request.target_user_id, request.limit)
    weights = {
        str(row.feature): float(row.weight)
        for row in result.feature_weights.itertuples(index=False)
    }
    return RankResponse(
        model_type=result.model_type,
        ranked_items=[
            RankedItem(
                group_buy_id=int(row.group_buy_id),
                score=round(float(row.score), 6),
                reason=str(row.reason),
            )
            for row in result.items.itertuples(index=False)
        ],
        feature_weights=weights,
    )
