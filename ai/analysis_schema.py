"""상품 이미지 분석 응답 스키마와 입력 이미지 검증."""

from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field

from category_catalog import CategoryId


SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


class ExtractionStatus(StrEnum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class ExtractionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(default=None, description="ERD products.name에 반영할 상품명")
    name_candidates: list[str] = Field(
        default_factory=list,
        max_length=3,
        description="상품명을 하나로 확정하기 어려울 때 제시할 후보 목록",
    )
    category_id: CategoryId = Field(
        description="ERD products.category_id에 반영할 카테고리 ID",
    )
    brand: str | None = Field(default=None, description="이미지에서 확인한 products.brand, 불명확하면 null")
    model_name: None = Field(default=None, description="모델명·규격 추출은 비활성화되어 반드시 null")
    color: str | None = Field(default=None, description="이미지에서 확인한 products.color, 불명확하면 null")
    # 필드 이름이 곧 JSON 키입니다. 백엔드가 result_json.description 으로 읽으므로
    # 다른 이름을 쓰면 값이 그대로 버려집니다(AiModuleExtraction.ResultJson).
    description: str | None = Field(
        default=None,
        max_length=300,
        description=(
            "ERD products.description에 반영할 상품 설명. 이미지에서 확인한 사실만 적고"
            " 보증·인증·배송 조건은 쓰지 않는다. 확인할 내용이 없으면 null"
        ),
    )


class ProductAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: ExtractionStatus
    result_json: ExtractionResult | None
    error_message: str | None = None


def validate_image_path(image_path: Path) -> Path:
    resolved = image_path.expanduser().resolve()
    if not resolved.is_file():
        raise ValueError(f"이미지 파일을 찾을 수 없습니다: {image_path}")
    if resolved.suffix.lower() not in SUPPORTED_IMAGE_EXTENSIONS:
        supported = ", ".join(sorted(SUPPORTED_IMAGE_EXTENSIONS))
        raise ValueError(f"지원하지 않는 이미지 형식입니다. 지원 형식: {supported}")
    return resolved
