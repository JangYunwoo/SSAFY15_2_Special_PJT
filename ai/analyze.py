"""로컬 상품 이미지를 Ollama Qwen3-VL로 분석하는 PoC."""

import argparse
import json
import os
from enum import StrEnum
from pathlib import Path
import sys

from ollama import Client
from pydantic import BaseModel, ConfigDict, Field, ValidationError


SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


class ExtractionStatus(StrEnum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class ExtractionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(
        default=None,
        description="ERD products.name에 반영할 상품명",
    )
    category_id: int | None = Field(
        default=None,
        description="ERD products.category_id에 반영할 카테고리 ID",
    )
    category_name: str | None = Field(
        default=None,
        description="추정한 카테고리 이름",
    )


class ExtractionConfidence(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: float = Field(ge=0, le=1, description="상품명 신뢰도")
    category: float = Field(ge=0, le=1, description="카테고리 신뢰도")


class ProductAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: ExtractionStatus
    result_json: ExtractionResult | None
    confidence_json: ExtractionConfidence | None
    error_message: str | None = None


PROMPT = """
당신은 공동구매 판매자가 업로드한 상품 사진을 분석합니다.

ERD에 정의된 AI 추출 대상만 분석하세요.
- 상품명: products.name
- 카테고리: products.category_id

규칙:
1. 이미지에서 직접 확인할 수 있는 정보를 우선 사용하세요.
2. 포장지, 라벨, 인쇄 문구에서 고유한 상품명을 확인할 수 있을 때만 result_json.name에 작성하세요.
3. result_json.category_name은 반드시 다음 카테고리 중 하나만 선택하세요: 디지털기기, 생활가전, 가구/인테리어, 생활/주방, 유아동, 유아도서, 여성의류, 여성잡화, 남성패션/잡화, 뷰티/미용, 스포츠/레저, 취미/게임/음반, 도서, 티켓/교환권, e쿠폰, 가공식품, 건강기능식품, 반려동물용품, 식물, 기타. 어느 카테고리인지 판단할 수 없으면 기타로 분류하세요.
4. 카테고리가 명확하지 않으면 제품의 외형, 구성, 조리 형태를 기반으로 목록에서 가장 가능성 높은 카테고리를 추론하세요.
5. 추론한 카테고리는 직접 확인한 정보보다 낮은 confidence를 부여하세요.
6. 합리적인 카테고리 후보를 판단할 수 없으면 category_name을 null로 반환하지 말고 기타로 반환하세요.
7. name이 null이면 confidence_json.name은 0.0으로 반환하세요.
8. name이 null이 아니면 confidence_json.name은 반드시 0보다 크게 작성하세요.
9. category_name이 null이 아니면 confidence_json.category는 반드시 0보다 크게 작성하세요.
10. confidence_json의 값은 0부터 1 사이로 작성하세요.
11. 현재 내부 카테고리 목록이 제공되지 않았으므로 category_id는 null로 반환하세요.
12. 분석에 성공하면 status는 COMPLETED로 반환하세요.
13. 이미지를 분석할 수 없으면 status는 FAILED, result_json과 confidence_json은 null로 반환하고 error_message에 실패 이유를 작성하세요.
14. 브랜드, 모델명, 색상, 가격, 배송비, 재고, 공동구매 조건은 별도 정보로 추출하지 마세요.
15. category_name에는 위 목록에 없는 품목명, 형태 또는 특징을 작성하지 마세요. 예를 들어 스마트폰은 디지털기기, 섬유유연제는 생활/주방, 닭고기 요리는 가공식품으로 작성하세요.
16. JSON 객체 외의 설명이나 마크다운은 반환하지 마세요.
17. 상품명과 카테고리 이름은 반드시 한국어로 작성하세요.
""".strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="상품 이미지를 Qwen3-VL로 분석해 JSON으로 출력합니다."
    )
    parser.add_argument("image", type=Path, help="분석할 이미지 파일 경로")
    return parser.parse_args()


def validate_image_path(image_path: Path) -> Path:
    resolved = image_path.expanduser().resolve()
    if not resolved.is_file():
        raise ValueError(f"이미지 파일을 찾을 수 없습니다: {image_path}")
    if resolved.suffix.lower() not in SUPPORTED_IMAGE_EXTENSIONS:
        supported = ", ".join(sorted(SUPPORTED_IMAGE_EXTENSIONS))
        raise ValueError(f"지원하지 않는 이미지 형식입니다. 지원 형식: {supported}")
    return resolved


def analyze_image(image_path: Path) -> ProductAnalysis:
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    model = os.getenv("OLLAMA_MODEL", "qwen3-vl:8b-instruct")
    client = Client(host=host)
    prompt = f"""{PROMPT}

    다음 모양의 JSON 객체만 반환하세요:
    {{
    "status": "COMPLETED",
    "result_json": {{
        "name": "상품명 또는 null",
        "category_id": null,
        "category_name": "카테고리 이름 또는 null"
    }},
    "confidence_json": {{
        "name": 0.0,
        "category": 0.0
    }},
    "error_message": null
    }}
    """

    response = client.chat(
        model=model,
        messages=[
            {
                "role": "user",
                "content": prompt,
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
