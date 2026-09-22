from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from lightgbm import LGBMRanker


EVENT_WEIGHTS = {
    "IMPRESSION": 0.0,
    "CLICK": 1.0,
    "DETAIL_VIEW": 1.0,
    "PURCHASE": 3.0,
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
MIN_TRAINING_ROWS = 40
MIN_TRAINING_GROUPS = 5


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
    reference = pd.Timestamp(now)
    if reference.tzinfo is None:
        reference = reference.tz_localize("UTC")
    history = events.loc[
        (events["user_id"] == user_id) & events["event_type"].isin(EVENT_WEIGHTS)
        & (pd.to_datetime(events["occurred_at"], utc=True) <= reference)
    ].copy()
    if history.empty:
        return UserPreferenceProfile(
            pd.DataFrame(columns=["user_id", "brand", "affinity"]),
            pd.DataFrame(columns=["user_id", "category_id", "affinity"]),
        )

    occurred_at = pd.to_datetime(history["occurred_at"], utc=True)
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


def _with_session_ids(events: pd.DataFrame) -> pd.DataFrame:
    """예전 CSV처럼 session_id가 없는 입력은 8건씩 임시 세션으로 묶는다."""
    events = events.copy()
    fallback_index = events.groupby("user_id").cumcount().floordiv(8).astype(str)
    fallback_ids = "legacy-" + events["user_id"].astype(str) + "-" + fallback_index
    if "session_id" not in events:
        events["session_id"] = fallback_ids
        return events
    missing = events["session_id"].isna() | events["session_id"].astype(str).str.strip().eq("")
    events.loc[missing, "session_id"] = fallback_ids.loc[missing]
    return events


def _training_rows(events: pd.DataFrame) -> pd.DataFrame:
    """세션 직전 선호도와 세션 내 상품 순위 정답을 만든다."""
    rows: list[dict[str, float | int | str]] = []
    ordered = events.loc[events["event_type"].isin(EVENT_WEIGHTS)].copy()
    if ordered.empty:
        return pd.DataFrame(columns=["session_id", "group_buy_id", *FEATURE_COLUMNS, "relevance"])
    ordered["occurred_at"] = pd.to_datetime(ordered["occurred_at"], utc=True)
    ordered = ordered.sort_values(["user_id", "occurred_at"])
    ordered = _with_session_ids(ordered)

    for _, user_events in ordered.groupby("user_id", sort=False):
        brand_scores: dict[object, float] = {}
        category_scores: dict[object, float] = {}
        session_order = (
            user_events.groupby("session_id")["occurred_at"].min().sort_values().index
        )
        for session_id in session_order:
            session_events = user_events.loc[user_events["session_id"] == session_id].copy()
            session_events["relevance"] = (
                session_events["event_type"].map(EVENT_WEIGHTS).fillna(0).astype(int)
            )
            session_items = (
                session_events.sort_values("occurred_at")
                .groupby("group_buy_id", as_index=False)
                .agg(
                    brand=("brand", "first"),
                    category_id=("category_id", "first"),
                    recruitment_rate=("recruitment_rate", "first"),
                    discount_rate=("discount_rate", "first"),
                    deadline_urgency=("deadline_urgency", "first"),
                    relevance=("relevance", "max"),
                )
            )
            normalized_brands = _normalized(brand_scores)
            normalized_categories = _normalized(category_scores)
            for item in session_items.itertuples(index=False):
                rows.append(
                    {
                        "session_id": str(session_id),
                        "group_buy_id": int(item.group_buy_id),
                        "brand_affinity": normalized_brands.get(item.brand, 0.0),
                        "category_affinity": normalized_categories.get(item.category_id, 0.0),
                        "recruitment_rate": float(item.recruitment_rate),
                        "discount_rate": float(item.discount_rate),
                        "deadline_urgency": float(item.deadline_urgency),
                        "relevance": int(item.relevance),
                    }
                )

            # 현재 세션의 정답은 특징에 섞지 않고, 다음 세션부터 선호도에 반영한다.
            for item in session_items.itertuples(index=False):
                score = float(item.relevance)
                if score <= 0:
                    continue
                if pd.notna(item.brand):
                    brand_scores[item.brand] = brand_scores.get(item.brand, 0.0) + score
                if pd.notna(item.category_id):
                    category_scores[item.category_id] = (
                        category_scores.get(item.category_id, 0.0) + score
                    )

    dataset = pd.DataFrame(rows)
    if dataset.empty:
        return pd.DataFrame(columns=["session_id", "group_buy_id", *FEATURE_COLUMNS, "relevance"])
    group_stats = dataset.groupby("session_id")["relevance"].agg(["size", "nunique"])
    eligible = group_stats.loc[(group_stats["size"] >= 2) & (group_stats["nunique"] >= 2)].index
    return dataset.loc[dataset["session_id"].isin(eligible)].sort_values(
        ["session_id", "group_buy_id"]
    )


def train_model(events: pd.DataFrame) -> LGBMRanker | None:
    dataset = _training_rows(events)
    group_sizes = dataset.groupby("session_id", sort=False).size().tolist()
    if len(dataset) < MIN_TRAINING_ROWS or len(group_sizes) < MIN_TRAINING_GROUPS:
        return None
    model = LGBMRanker(
        objective="lambdarank",
        metric="ndcg",
        n_estimators=100,
        learning_rate=0.05,
        num_leaves=15,
        max_depth=5,
        min_child_samples=10,
        reg_lambda=1.0,
        random_state=42,
        importance_type="gain",
        verbosity=-1,
    )
    model.fit(
        dataset[FEATURE_COLUMNS],
        dataset["relevance"],
        group=group_sizes,
        eval_at=[8],
    )
    return model


def _feature_importance(model: LGBMRanker) -> dict[str, float]:
    importance = model.booster_.feature_importance(importance_type="gain").astype(float)
    total = float(importance.sum())
    if total <= 0:
        return {feature: 0.0 for feature in FEATURE_COLUMNS}
    return {
        feature: float(value / total)
        for feature, value in zip(FEATURE_COLUMNS, importance, strict=True)
    }


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
    # 하나의 기준시각으로 선호도, 학습, 최근 구매 감점의 관측 범위를 맞춘다.
    events["occurred_at"] = pd.to_datetime(events["occurred_at"], utc=True)
    events = events.loc[events["occurred_at"] <= now].copy()
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
        raw_score = pd.Series(model.predict(ranked[FEATURE_COLUMNS]), index=ranked.index)
        score_range = float(raw_score.max() - raw_score.min())
        ranked["score"] = (
            (raw_score - raw_score.min()) / score_range if score_range > 0 else 0.5
        )
        weights = _feature_importance(model)
        model_type = "LIGHTGBM_RANKER"

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
