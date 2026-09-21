from fastapi.testclient import TestClient

try:
    from . import app
except ImportError:
    import app


def test_recommendation_rank_returns_every_candidate_once():
    candidates = [
        {
            "group_buy_id": 1,
            "product_id": 11,
            "brand": "브랜드A",
            "category_id": 10016,
            "recruitment_rate": 0.2,
            "discount_rate": 0.1,
            "deadline_urgency": 0.3,
        },
        {
            "group_buy_id": 2,
            "product_id": 12,
            "brand": "브랜드B",
            "category_id": 10021,
            "recruitment_rate": 0.8,
            "discount_rate": 0.2,
            "deadline_urgency": 0.7,
        },
    ]
    response = TestClient(app.app).post(
        "/api/v1/recommendations/rank",
        json={"target_user_id": 101, "candidates": candidates, "events": [], "limit": 2},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["model_type"] == "HEURISTIC_FALLBACK"
    assert {item["group_buy_id"] for item in payload["ranked_items"]} == {1, 2}
    assert payload["ranked_items"][0]["group_buy_id"] == 2
