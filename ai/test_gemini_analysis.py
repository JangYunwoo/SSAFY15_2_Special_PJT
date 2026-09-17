"""외부 API 호출 없이 이미지 분석 흐름을 검증합니다."""

from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

import gemini_analyze


@pytest.mark.parametrize("count", [1, 5])
def test_images_use_one_call_without_search(monkeypatch, count):
    client = MagicMock()
    client.models.generate_content.return_value = SimpleNamespace(text='{"status":"COMPLETED","result_json":{"name":null,"name_candidates":[],"category_id":10001,"brand":null,"color":null,"model_name":null},"error_message":null}')
    monkeypatch.setattr(gemini_analyze, "GEMINI_PROJECT", "test-project")
    monkeypatch.setattr(gemini_analyze.genai, "Client", lambda **kwargs: client)
    result = gemini_analyze.analyze_images_data([(b"image", "image/jpeg")] * count)
    assert result.result_json.name is None
    assert result.result_json.model_name is None
    client.models.generate_content.assert_called_once()
    request = client.models.generate_content.call_args.kwargs
    assert len(request["contents"]) == count + 1
    assert not request["config"].tools
