import pandas as pd
import pytest

from recommendation_poc.recommender import build_user_profile, recommend


NOW = pd.Timestamp("2026-09-21T06:00:00Z")


def events(at, kind="PURCHASE", count=1):
    return pd.DataFrame([
        dict(user_id=1, group_buy_id=1, product_id=11, brand="A", category_id=1,
             event_type=kind if i % 2 == 0 else "IMPRESSION", occurred_at=at,
             recruitment_rate=0.5, discount_rate=0.1, deadline_urgency=0.1)
        for i in range(count)
    ])


def candidates():
    return pd.DataFrame([
        dict(group_buy_id=i, product_id=10+i, brand=brand, category_id=i,
             recruitment_rate=0.5, discount_rate=0.1, deadline_urgency=0.1)
        for i, brand in [(1, "A"), (2, "B")]
    ])


def test_future_events_do_not_create_preferences():
    profile = build_user_profile(events(NOW + pd.Timedelta(seconds=1)), 1, NOW)
    assert profile.brand_preferences.empty
    assert profile.category_preferences.empty


@pytest.mark.parametrize("at", [NOW, NOW.tz_convert("Asia/Seoul"), NOW.tz_localize(None)])
def test_events_at_reference_are_included_with_equivalent_timezones(at):
    profile = build_user_profile(events(at), 1, NOW)
    assert profile.brand_preferences.iloc[0]["brand"] == "A"


def test_future_purchase_changes_neither_score_nor_preferences():
    empty = events(NOW).iloc[:0]
    baseline = recommend(candidates(), empty, 1, now=NOW)
    actual = recommend(candidates(), events(NOW + pd.Timedelta(days=1)), 1, now=NOW)
    pd.testing.assert_frame_equal(actual.items, baseline.items)


def test_future_events_cannot_train_model():
    result = recommend(candidates(), events(NOW + pd.Timedelta(days=1), count=24), 1, now=NOW)
    assert result.model_type == "HEURISTIC_FALLBACK"


def test_past_purchase_still_penalizes_recent_product():
    result = recommend(candidates(), events(NOW - pd.Timedelta(hours=1)), 1, now=NOW)
    assert list(result.items["group_buy_id"]) == [2, 1]
