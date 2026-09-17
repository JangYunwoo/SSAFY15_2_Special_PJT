from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

import app
from analysis_schema import ProductAnalysis


def test_url_api_preserves_order_and_signature():
    urls = ["https://bucket.s3.amazonaws.com/main.jpg?X-Amz-Signature=test", "https://bucket.s3.amazonaws.com/sub.png"]
    result = ProductAnalysis(status="FAILED", result_json=None, error_message="test")
    with patch.object(app, "analyze_images_urls", return_value=result) as analyze:
        response = TestClient(app.app).post("/api/v1/products/extract", json={"image_urls": urls})
    assert response.status_code == 200
    analyze.assert_called_once_with([(urls[0], "image/jpeg"), (urls[1], "image/png")])


@pytest.mark.parametrize("urls", [[], ["https://bucket.s3.amazonaws.com/a.jpg"] * 6, ["http://localhost/a.jpg"], ["https://bucket.s3.amazonaws.com/a.txt"]])
def test_invalid_urls_are_rejected(urls):
    with patch.object(app, "analyze_images_urls") as analyze:
        response = TestClient(app.app).post("/api/v1/products/extract", json={"image_urls": urls})
    assert response.status_code == 422
    analyze.assert_not_called()
