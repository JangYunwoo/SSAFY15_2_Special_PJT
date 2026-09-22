from __future__ import annotations

from datetime import timedelta

import pandas as pd

try:
    from .recommendation_poc.recommender import (
        FEATURE_COLUMNS,
        _training_rows,
        recommend,
        train_model,
    )
except ImportError:
    from recommendation_poc.recommender import (
        FEATURE_COLUMNS,
        _training_rows,
        recommend,
        train_model,
    )


def _event(
    session_id: str,
    user_id: int,
    group_buy_id: int,
    event_type: str,
    minute: int,
) -> dict[str, object]:
    return {
        "session_id": session_id,
        "user_id": user_id,
        "group_buy_id": group_buy_id,
        "product_id": 10_000 + group_buy_id,
        "event_type": event_type,
        "occurred_at": pd.Timestamp("2026-09-01T00:00:00Z") + timedelta(minutes=minute),
        "brand": f"브랜드{group_buy_id % 4}",
        "category_id": 10_001 + group_buy_id % 3,
        "recruitment_rate": group_buy_id / 10,
        "discount_rate": (group_buy_id % 4) / 10,
        "deadline_urgency": (10 - group_buy_id) / 10,
    }


def test_training_rows_merge_repeated_actions_into_highest_relevance():
    events = pd.DataFrame(
        [
            _event("session-1", 1, 1, "IMPRESSION", 0),
            _event("session-1", 1, 1, "CLICK", 1),
            _event("session-1", 1, 2, "IMPRESSION", 2),
        ]
    )

    rows = _training_rows(events)

    assert len(rows) == 2
    relevance = rows.set_index("group_buy_id")["relevance"].to_dict()
    assert relevance == {1: 1, 2: 0}
    assert rows["brand_affinity"].eq(0).all()
    assert rows["category_affinity"].eq(0).all()


def test_lightgbm_ranker_is_used_when_session_groups_are_sufficient():
    events: list[dict[str, object]] = []
    minute = 0
    for user_id in range(1, 4):
        for session_index in range(6):
            session_id = f"user-{user_id}-session-{session_index}"
            preferred_item = (user_id + session_index) % 4 + 1
            for group_buy_id in range(1, 9):
                event_type = "CLICK" if group_buy_id == preferred_item else "IMPRESSION"
                events.append(_event(session_id, user_id, group_buy_id, event_type, minute))
                minute += 1
    event_frame = pd.DataFrame(events)
    candidates = pd.DataFrame(
        [
            {
                "group_buy_id": group_buy_id,
                "product_id": 10_000 + group_buy_id,
                "brand": f"브랜드{group_buy_id % 4}",
                "category_id": 10_001 + group_buy_id % 3,
                "recruitment_rate": group_buy_id / 10,
                "discount_rate": (group_buy_id % 4) / 10,
                "deadline_urgency": (10 - group_buy_id) / 10,
            }
            for group_buy_id in range(1, 9)
        ]
    )

    model = train_model(event_frame)
    result = recommend(
        candidates,
        event_frame,
        user_id=1,
        limit=8,
        now=pd.Timestamp("2026-09-02T00:00:00Z"),
    )

    assert model is not None
    assert result.model_type == "LIGHTGBM_RANKER"
    assert set(result.feature_weights["feature"]) == set(FEATURE_COLUMNS)
    assert len(result.items) == 8
    assert result.items["score"].between(0, 1).all()
