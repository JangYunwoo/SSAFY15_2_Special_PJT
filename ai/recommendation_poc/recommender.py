from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


EVENT_WEIGHTS = {
    "IMPRESSION": 0.0,
    "CLICK": 1.0,
    "DETAIL_VIEW": 2.0,
    "PURCHASE": 4.0,
}
FEATURE_COLUMNS = [
    "brand_affinity",
    "category_affinity",
    "recruitment_rate",
    "discount_rate",
    "deadline_urgency",
]
FALLBACK_WEIGHTS = {
    "brand_affinity": 0.38,
    "category_affinity": 0.30,
    "recruitment_rate": 0.14,
    "discount_rate": 0.08,
    "deadline_urgency": 0.10,
}


@dataclass
class RecommendationResult:
    items: pd.DataFrame
    feature_weights: pd.DataFrame
    brand_preferences: pd.DataFrame
    category_preferences: pd.DataFrame
    model_type: str


@dataclass
class UserPreferenceProfile:
    brand_preferences: pd.DataFrame
    category_preferences: pd.DataFrame


def _normalized(scores: dict[object, float]) -> dict[object, float]:
    maximum = max(scores.values(), default=0.0)
    if maximum <= 0:
        return {}
    return {key: value / maximum for key, value in scores.items()}


def build_user_profile(events: pd.DataFrame, user_id: int, now: pd.Timestamp) -> UserPreferenceProfile:
    """현재 시점 이전의 행동만으로 사용자 브랜드·카테고리 선호를 계산한다."""
    history = events.loc[
        (events["user_id"] == user_id) & events["event_type"].isin(EVENT_WEIGHTS)
    ].copy()
    if history.empty:
        return UserPreferenceProfile(
            pd.DataFrame(columns=["user_id", "brand", "affinity"]),
            pd.DataFrame(columns=["user_id", "category_id", "affinity"]),
        )

    occurred_at = pd.to_datetime(history["occurred_at"], utc=True)
    reference = pd.Timestamp(now)
    if reference.tzinfo is None:
        reference = reference.tz_localize("UTC")
    age_hours = ((reference - occurred_at).dt.total_seconds() / 3600).clip(lower=0)
    history["event_score"] = history["event_type"].map(EVENT_WEIGHTS).fillna(0.0)
    history["event_score"] *= 0.5 ** (age_hours / (24 * 14))

    brand = (
        history.dropna(subset=["brand"])
        .groupby("brand", as_index=False)["event_score"]
        .sum()
    )
    category = (
        history.dropna(subset=["category_id"])
        .groupby("category_id", as_index=False)["event_score"]
        .sum()
    )
    brand = brand.loc[brand["event_score"] > 0].copy()
    category = category.loc[category["event_score"] > 0].copy()
    brand["affinity"] = brand["event_score"] / max(brand["event_score"].max(), 1.0)
    category["affinity"] = category["event_score"] / max(category["event_score"].max(), 1.0)
    brand.insert(0, "user_id", user_id)
    category.insert(0, "user_id", user_id)
    return UserPreferenceProfile(
        brand[["user_id", "brand", "affinity"]].sort_values("affinity", ascending=False),
        category[["user_id", "category_id", "affinity"]].sort_values(
            "affinity", ascending=False
        ),
    )


def _training_rows(events: pd.DataFrame) -> pd.DataFrame:
    """각 행동 직전의 선호도만 사용해 정답 유출을 막은 학습 행을 만든다."""
    rows: list[dict[str, float | int]] = []
    ordered = events.loc[events["event_type"].isin(EVENT_WEIGHTS)].copy()
    if ordered.empty:
        return pd.DataFrame(columns=[*FEATURE_COLUMNS, "engaged"])
    ordered["occurred_at"] = pd.to_datetime(ordered["occurred_at"], utc=True)
    ordered = ordered.sort_values(["user_id", "occurred_at"])

    for _, user_events in ordered.groupby("user_id", sort=False):
        brand_scores: dict[object, float] = {}
        category_scores: dict[object, float] = {}
        for event in user_events.itertuples(index=False):
            brand_affinity = _normalized(brand_scores).get(event.brand, 0.0)
            category_affinity = _normalized(category_scores).get(event.category_id, 0.0)
            rows.append(
                {
                    "brand_affinity": brand_affinity,
                    "category_affinity": category_affinity,
                    "recruitment_rate": float(event.recruitment_rate),
                    "discount_rate": float(event.discount_rate),
                    "deadline_urgency": float(event.deadline_urgency),
                    "engaged": int(event.event_type != "IMPRESSION"),
                }
            )
            score = EVENT_WEIGHTS[event.event_type]
            if score > 0:
                if event.brand is not None:
                    brand_scores[event.brand] = brand_scores.get(event.brand, 0.0) + score
                if event.category_id is not None:
                    category_scores[event.category_id] = (
                        category_scores.get(event.category_id, 0.0) + score
                    )
    return pd.DataFrame(rows)


def train_model(events: pd.DataFrame) -> Pipeline | None:
    dataset = _training_rows(events)
    if len(dataset) < 20 or dataset["engaged"].nunique() < 2:
        return None
    model = Pipeline(
        [
            ("scale", StandardScaler()),
            (
                "model",
                LogisticRegression(
                    max_iter=1000,
                    random_state=42,
                    class_weight="balanced",
                ),
            ),
        ]
    )
    model.fit(dataset[FEATURE_COLUMNS], dataset["engaged"])
    return model


def recommend(
    candidates: pd.DataFrame,
    events: pd.DataFrame,
    user_id: int,
    limit: int = 10,
    now: pd.Timestamp | None = None,
) -> RecommendationResult:
    now = pd.Timestamp.now(tz="UTC") if now is None else pd.Timestamp(now)
    if now.tzinfo is None:
        now = now.tz_localize("UTC")
    candidates = candidates.copy()
    if "recruitment_rate" not in candidates:
        candidates["recruitment_rate"] = (
            candidates["current_participants"] / candidates["target_participants"]
        ).clip(0, 1)
    if "discount_rate" not in candidates:
        candidates["discount_rate"] = (
            (candidates["price"] - candidates["product_price"]) / candidates["price"]
        ).clip(0, 1)
    if "deadline_urgency" not in candidates:
        end_at = pd.to_datetime(candidates["recruit_end_at"], utc=True)
        remaining_hours = (end_at - now).dt.total_seconds() / 3600
        candidates["deadline_urgency"] = (1 - remaining_hours.clip(0, 168) / 168).clip(0, 1)
    if "status" in candidates:
        candidates = candidates.loc[candidates["status"] == "RECRUITING"].copy()

    events = events.copy()
    event_features = [
        "product_id",
        "brand",
        "category_id",
        "recruitment_rate",
        "discount_rate",
        "deadline_urgency",
    ]
    missing = [column for column in event_features if column not in events]
    if missing and not events.empty:
        events = events.merge(
            candidates[["group_buy_id", *missing]], on="group_buy_id", how="left"
        )
    profile = build_user_profile(events, user_id, now)
    brand_map = profile.brand_preferences.set_index("brand")["affinity"].to_dict()
    category_map = profile.category_preferences.set_index("category_id")["affinity"].to_dict()

    ranked = candidates.copy()
    ranked["brand_affinity"] = ranked["brand"].map(brand_map).fillna(0.0)
    ranked["category_affinity"] = ranked["category_id"].map(category_map).fillna(0.0)
    model = train_model(events)
    if model is None:
        ranked["score"] = sum(
            ranked[feature] * weight for feature, weight in FALLBACK_WEIGHTS.items()
        )
        weights = FALLBACK_WEIGHTS
        model_type = "HEURISTIC_FALLBACK"
    else:
        ranked["score"] = model.predict_proba(ranked[FEATURE_COLUMNS])[:, 1]
        weights = dict(zip(FEATURE_COLUMNS, model.named_steps["model"].coef_[0], strict=True))
        model_type = "LOGISTIC_REGRESSION"

    purchases = events.loc[
        (events["user_id"] == user_id) & (events["event_type"] == "PURCHASE")
    ].copy()
    if not purchases.empty:
        purchases["occurred_at"] = pd.to_datetime(purchases["occurred_at"], utc=True)
        recent_product_ids = set(
            purchases.loc[
                purchases["occurred_at"] >= now - pd.Timedelta(days=30), "product_id"
            ]
        )
        ranked.loc[ranked["product_id"].isin(recent_product_ids), "score"] *= 0.05

    def reason(row: pd.Series) -> str:
        if row["brand_affinity"] >= 0.5:
            return "선호 브랜드와 비슷해요"
        if row["category_affinity"] >= 0.5:
            return "자주 본 카테고리예요"
        if row["deadline_urgency"] >= 0.7:
            return "마감이 가까워요"
        return "현재 참여가 활발해요"

    ranked["reason"] = ranked.apply(reason, axis=1)
    weight_frame = pd.DataFrame(
        {"feature": list(weights.keys()), "weight": list(weights.values())}
    )
    return RecommendationResult(
        ranked.sort_values(["score", "group_buy_id"], ascending=[False, False]).head(limit),
        weight_frame,
        profile.brand_preferences,
        profile.category_preferences,
        model_type,
    )
