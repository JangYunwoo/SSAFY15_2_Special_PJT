"""로컬 상품 이미지를 Ollama Qwen3-VL로 분석하는 PoC."""

import argparse
import json
import os
from pathlib import Path
import sys

from ollama import Client
from pydantic import ValidationError

from analysis_schema import ProductAnalysis, validate_image_path
from prompt import QWEN_PRODUCT_ANALYSIS_PROMPT


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="상품 이미지를 Qwen3-VL로 분석해 JSON으로 출력합니다."
    )
    parser.add_argument("image", type=Path, help="분석할 이미지 파일 경로")
    return parser.parse_args()


def analyze_image(image_path: Path) -> ProductAnalysis:
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    model = os.getenv("OLLAMA_MODEL", "qwen3-vl:8b-instruct")
    client = Client(host=host)
    response = client.chat(
        model=model,
        messages=[
            {
                "role": "user",
                "content": QWEN_PRODUCT_ANALYSIS_PROMPT,
                "images": [image_path.read_bytes()],
            }
        ],
        options={"temperature": 0},
        think=False,
        stream=False,
    )
    content = response.message.content.strip()
    if not content:
        content = (response.message.thinking or "").strip()

    if not content:
        raise ValueError("모델이 content와 thinking 모두 빈 응답을 반환했습니다.")

    if content.startswith("```json"):
        content = content.removeprefix("```json").removesuffix("```").strip()
    elif content.startswith("```"):
        content = content.removeprefix("```").removesuffix("```").strip()

    # thinking에 예시 JSON이 섞일 수 있으므로 전체 응답 구조와 맞는 객체를 찾습니다.
    decoder = json.JSONDecoder()
    for index, character in enumerate(content):
        if character != "{":
            continue
        try:
            parsed_json, _ = decoder.raw_decode(content[index:])
            return ProductAnalysis.model_validate(parsed_json)
        except (json.JSONDecodeError, ValidationError):
            continue

    raise ValueError("모델 응답에서 올바른 ProductAnalysis JSON을 찾지 못했습니다.")


def main() -> int:
    args = parse_args()

    try:
        image_path = validate_image_path(args.image)
        result = analyze_image(image_path)
    except ValidationError as error:
        print(f"모델 응답 검증 오류: {error}", file=sys.stderr)
        return 3
    except ValueError as error:
        print(f"입력 오류: {error}", file=sys.stderr)
        return 2
    except Exception as error:
        print(f"Ollama 호출 오류: {error}", file=sys.stderr)
        return 1

    print(json.dumps(result.model_dump(mode="json"), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
