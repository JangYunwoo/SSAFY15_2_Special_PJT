"""상품 이미지 분석 응답 스키마와 입력 이미지 검증."""

from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field


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
    category_id: int | None = Field(
        default=None,
        description="ERD products.category_id에 반영할 카테고리 ID",
    )
    category_name: str | None = Field(default=None, description="추정한 카테고리 이름")


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
