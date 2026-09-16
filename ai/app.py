"""Spring Boot가 상품 이미지 분석을 요청하는 FastAPI 진입점."""

import logging

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from google.genai.errors import ClientError
from pydantic import ValidationError

from analysis_schema import ProductAnalysis
from gemini_analyze import SUPPORTED_MIME_TYPES, analyze_image_data


logger = logging.getLogger(__name__)
app = FastAPI(title="공구한 AI 상품 이미지 분석 API")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post(
    "/api/v1/products/extract",
    response_model=ProductAnalysis,
    response_model_exclude_none=False,
)
def extract_product(images: list[UploadFile] = File(...)) -> ProductAnalysis:
    """현재 계약에 맞춰 상품 이미지 한 장을 분석합니다."""
    if len(images) != 1:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="현재는 이미지 한 장만 분석할 수 있습니다.",
        )

    image = images[0]
    if image.content_type not in SUPPORTED_MIME_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="지원하지 않는 이미지 형식입니다.",
        )

    image_data = image.file.read()
    if not image_data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 파일이 비어 있습니다.",
        )

    try:
        return analyze_image_data(image_data, image.content_type)
    except ClientError as error:
        if error.code == status.HTTP_429_TOO_MANY_REQUESTS:
            logger.warning("Gemini API 할당량 또는 처리 용량 초과: %s", error.message)
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Gemini 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.",
            ) from error
        logger.exception("Gemini API 클라이언트 오류")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini API 요청이 거절되었습니다.",
        ) from error
    except ValidationError as error:
        logger.warning("Gemini 응답 검증 실패", exc_info=error)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini 응답 형식이 올바르지 않습니다.",
        ) from error
    except ValueError as error:
        logger.warning("Gemini 이미지 분석 설정 오류: %s", error)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(error),
        ) from error
    except Exception as error:
        logger.exception("Gemini 이미지 분석 호출 실패")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini 이미지 분석 호출에 실패했습니다.",
        ) from error
