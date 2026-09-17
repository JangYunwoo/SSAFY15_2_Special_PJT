from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from google import genai
from google.genai import types
from pydantic import ValidationError

from analysis_schema import ProductAnalysis, validate_image_path
from prompt import PRODUCT_ANALYSIS_PROMPT


load_dotenv(Path(__file__).with_name(".env"))
GEMINI_PROJECT = os.getenv("GEMINI_PROJECT")
GEMINI_LOCATION = os.getenv("GEMINI_LOCATION", "global")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")

MIME_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}
SUPPORTED_MIME_TYPES = frozenset(MIME_TYPES.values())


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="상품 이미지를 Gemini로 분석해 JSON으로 출력합니다."
    )
    parser.add_argument("images", type=Path, nargs="+", help="메인 이미지 경로, 이후 부연 이미지 경로 (최대 5장)")
    return parser.parse_args()


def analyze_image_data(image_data: bytes, mime_type: str) -> ProductAnalysis:
    """기존 단일 이미지 호출을 유지합니다."""
    return analyze_images_data([(image_data, mime_type)])


def analyze_images_data(images: list[tuple[bytes, str]]) -> ProductAnalysis:
    """첫 이미지는 메인, 나머지는 부연 이미지로 한 번에 분석합니다."""
    if not 1 <= len(images) <= 5:
        raise ValueError("이미지는 메인 1장과 부연 최대 4장, 총 1~5장이 필요합니다.")
    for image_data, mime_type in images:
        if not image_data:
            raise ValueError("이미지 데이터가 비어 있습니다.")
        if mime_type not in SUPPORTED_MIME_TYPES:
            supported = ", ".join(sorted(SUPPORTED_MIME_TYPES))
            raise ValueError(f"지원하지 않는 이미지 형식입니다. 지원 형식: {supported}")
    image_parts = [
        types.Part.from_bytes(data=image_data, mime_type=mime_type)
        for image_data, mime_type in images
    ]
    return analyze_image_parts(image_parts)


def analyze_images_urls(images: list[tuple[str, str]]) -> ProductAnalysis:
    """이미지 URL을 Gemini에 직접 전달합니다."""
    if not 1 <= len(images) <= 5:
        raise ValueError("이미지는 총 1~5장이 필요합니다.")
    return analyze_image_parts([
        types.Part.from_uri(file_uri=url, mime_type=mime_type)
        for url, mime_type in images
    ])


def analyze_image_parts(image_parts: list[types.Part]) -> ProductAnalysis:
    """파일과 URL 입력이 공유하는 단일 Gemini 호출."""
    if not GEMINI_PROJECT:
        raise ValueError(".env에 GEMINI_PROJECT를 설정하세요.")
    client = genai.Client(
        enterprise=True,
        project=GEMINI_PROJECT,
        location=GEMINI_LOCATION,
    )
    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=[PRODUCT_ANALYSIS_PROMPT, *image_parts],
        config=types.GenerateContentConfig(
            temperature=0,
            response_mime_type="application/json",
            response_json_schema=ProductAnalysis.model_json_schema(),
        ),
    )
    if not response.text:
        raise ValueError("Gemini가 빈 응답을 반환했습니다.")
    result = ProductAnalysis.model_validate_json(response.text)
    if result.result_json is not None:
        result.result_json.model_name = None
    return result


def analyze_image(image_path: Path) -> ProductAnalysis:
    """로컬 CLI 테스트를 위해 파일 경로를 이미지 데이터로 변환합니다."""
    return analyze_image_data(
        image_path.read_bytes(),
        MIME_TYPES[image_path.suffix.lower()],
    )


def main() -> int:
    args = parse_args()
    try:
        if not 1 <= len(args.images) <= 5:
            raise ValueError("이미지는 메인 1장과 부연 최대 4장, 총 1~5장이 필요합니다.")
        image_paths = [validate_image_path(path) for path in args.images]
        result = analyze_images_data([
            (path.read_bytes(), MIME_TYPES[path.suffix.lower()]) for path in image_paths
        ])
    except ValidationError as error:
        print(f"Gemini 응답 검증 오류: {error}", file=sys.stderr)
        return 3
    except ValueError as error:
        print(f"입력 오류: {error}", file=sys.stderr)
        return 2
    except Exception as error:
        print(f"Gemini API 호출 오류: {error}", file=sys.stderr)
        return 4

    print(json.dumps(result.model_dump(mode="json"), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
