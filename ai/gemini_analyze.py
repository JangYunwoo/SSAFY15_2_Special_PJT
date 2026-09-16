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
    parser.add_argument("image", type=Path, help="분석할 이미지 파일 경로")
    return parser.parse_args()


def analyze_image_data(image_data: bytes, mime_type: str) -> ProductAnalysis:
    if not GEMINI_PROJECT:
        raise ValueError(".env에 GEMINI_PROJECT를 설정하세요.")
    if not image_data:
        raise ValueError("이미지 데이터가 비어 있습니다.")
    if mime_type not in SUPPORTED_MIME_TYPES:
        supported = ", ".join(sorted(SUPPORTED_MIME_TYPES))
        raise ValueError(f"지원하지 않는 이미지 형식입니다. 지원 형식: {supported}")

    client = genai.Client(
        enterprise=True,
        project=GEMINI_PROJECT,
        location=GEMINI_LOCATION,
    )
    image_part = types.Part.from_bytes(
        data=image_data,
        mime_type=mime_type,
    )
    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=[PRODUCT_ANALYSIS_PROMPT, image_part],
        config=types.GenerateContentConfig(
            temperature=0,
            response_mime_type="application/json",
            response_json_schema=ProductAnalysis.model_json_schema(),
        ),
    )
    if not response.text:
        raise ValueError("Gemini가 빈 응답을 반환했습니다.")
    return ProductAnalysis.model_validate_json(response.text)


def analyze_image(image_path: Path) -> ProductAnalysis:
    """로컬 CLI 테스트를 위해 파일 경로를 이미지 데이터로 변환합니다."""
    return analyze_image_data(
        image_path.read_bytes(),
        MIME_TYPES[image_path.suffix.lower()],
    )


def main() -> int:
    args = parse_args()
    try:
        image_path = validate_image_path(args.image)
        result = analyze_image(image_path)
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
