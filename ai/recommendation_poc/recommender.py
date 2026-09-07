from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


EVENT_WEIGHTS = {"IMPRESSION": 0.0, "CLICK": 1.0, "DETAIL_VIEW": 2.0, "PURCHASE": 4.0}
FEATURE_COLUMNS = [
    "brand_affinity",
    "category_affinity",
    "recruitment_rate",
    "discount_rate",
    "deadline_urgency",
]


@dataclass
class RecommendationResult:
    items: pd.DataFrame
    feature_weights: pd.DataFrame
    brand_preferences: pd.DataFrame
    category_preferences: pd.DataFrame


@dataclass
class UserPreferenceProfile:
    brand_preferences: pd.DataFrame
    category_preferences: pd.DataFrame


def add_derived_features(products: pd.DataFrame, now: pd.Timestamp) -> pd.DataFrame:
    result = products.copy()
    result["recruit_end_at"] = pd.to_datetime(result["recruit_end_at"])
    result["recruitment_rate"] = (
        result["current_participants"] / result["target_participants"]
    ).clip(0, 1)
    result["discount_rate"] = (
        (result["price"] - result["product_price"]) / result["price"]
    ).clip(0, 1)
    remaining_hours = (result["recruit_end_at"] - now).dt.total_seconds() / 3600
    result["deadline_urgency"] = (1 - remaining_hours.clip(0, 168) / 168).clip(0, 1)
    return result


def build_user_profile(
    products: pd.DataFrame, events: pd.DataFrame, user_id: int
) -> UserPreferenceProfile:
    """반응 이력이 있는 브랜드와 카테고리만 행 형태로 만든다."""
    history = events.loc[events["user_id"] == user_id].merge(
        products[["group_buy_id", "brand", "category_id", "category_name"]],
        on="group_buy_id",
        how="left",
    )
    history["event_score"] = history["event_type"].map(EVENT_WEIGHTS).fillna(0.0)
    occurred_at = pd.to_datetime(history["occurred_at"])
    reference_time = pd.Timestamp("2026-09-07T12:00:00")
    age_hours = (
        (reference_time - occurred_at).dt.total_seconds() / 3600
    ).clip(lower=0)
    history["event_score"] *= 0.5 ** (age_hours / 6)
    brand = history.groupby("brand", as_index=False)["event_score"].sum()
    category = history.groupby(
        ["category_id", "category_name"], as_index=False
    )["event_score"].sum()

    brand = brand.loc[brand["event_score"] > 0].copy()
    category = category.loc[category["event_score"] > 0].copy()
    brand["affinity"] = brand["event_score"] / max(brand["event_score"].max(), 1.0)
    category["affinity"] = category["event_score"] / max(
        category["event_score"].max(), 1.0
    )
    brand.insert(0, "user_id", user_id)
    category.insert(0, "user_id", user_id)
    return UserPreferenceProfile(
        brand_preferences=brand[["user_id", "brand", "affinity"]].sort_values(
            "affinity", ascending=False
        ),
        category_preferences=category[
            ["user_id", "category_id", "category_name", "affinity"]
        ].sort_values("affinity", ascending=False),
    )


def _user_item_features(products: pd.DataFrame, events: pd.DataFrame, user_id: int) -> pd.DataFrame:
    profile = build_user_profile(products, events, user_id)
    brand = profile.brand_preferences.set_index("brand")["affinity"]
    category = profile.category_preferences.set_index("category_id")["affinity"]
    result = products.copy()
    result["brand_affinity"] = result["brand"].map(brand).fillna(0.0)
    result["category_affinity"] = result["category_id"].map(category).fillna(0.0)
    return result


def train_model(products: pd.DataFrame, events: pd.DataFrame) -> Pipeline:
    """클릭·상세조회·구매 여부로 추천 특성의 가중치를 학습한다."""
    rows = []
    for user_id in events["user_id"].unique():
        user_events = events.loc[events["user_id"] == user_id]
        features = _user_item_features(products, events, int(user_id))
        training = user_events.merge(
            features[["group_buy_id", *FEATURE_COLUMNS]], on="group_buy_id", how="left"
        )
        training["engaged"] = (training["event_type"] != "IMPRESSION").astype(int)
        rows.append(training)

    dataset = pd.concat(rows, ignore_index=True)
    model = Pipeline([
        ("scale", StandardScaler()),
        ("model", LogisticRegression(max_iter=1000, random_state=42)),
    ])
    model.fit(dataset[FEATURE_COLUMNS], dataset["engaged"])
    return model


def recommend(products: pd.DataFrame, events: pd.DataFrame, user_id: int, limit: int = 10) -> RecommendationResult:
    prepared = add_derived_features(products, pd.Timestamp("2026-09-07T12:00:00"))
    profile = build_user_profile(prepared, events, user_id)
    candidates = _user_item_features(prepared, events, user_id)
    candidates = candidates.loc[candidates["status"] == "RECRUITING"].copy()
    model = train_model(prepared, events)
    candidates["score"] = model.predict_proba(candidates[FEATURE_COLUMNS])[:, 1]

    purchased_ids = set(events.loc[
        (events["user_id"] == user_id) & (events["event_type"] == "PURCHASE"),
        "group_buy_id",
    ])
    candidates.loc[candidates["group_buy_id"].isin(purchased_ids), "score"] *= 0.05

    weights = pd.DataFrame({
        "feature": FEATURE_COLUMNS,
        "weight": model.named_steps["model"].coef_[0],
    })
    return RecommendationResult(
        items=candidates.sort_values("score", ascending=False).head(limit),
        feature_weights=weights,
        brand_preferences=profile.brand_preferences,
        category_preferences=profile.category_preferences,
    )
