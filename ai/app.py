"""백엔드가 제공하는 S3 HTTPS 이미지 URL을 받는 서비스용 API."""

import logging
from pathlib import PurePosixPath
from urllib.parse import unquote, urlsplit

from fastapi import FastAPI, HTTPException
from google.genai.errors import ClientError
from pydantic import BaseModel, Field, ValidationError, field_validator

from analysis_schema import ProductAnalysis
from gemini_analyze import MIME_TYPES, analyze_images_urls

logger = logging.getLogger(__name__)
app = FastAPI(title="공구한 AI 상품 이미지 URL 분석 API")


class ImageUrlRequest(BaseModel):
    image_urls: list[str] = Field(min_length=1, max_length=5)

    @field_validator("image_urls")
    @classmethod
    def validate_urls(cls, urls: list[str]) -> list[str]:
        for url in urls:
            parsed = urlsplit(url)
            if (parsed.scheme != "https" or not parsed.hostname
                    or parsed.username or parsed.password or parsed.fragment):
                raise ValueError("외부에서 접근 가능한 HTTPS 이미지 URL이 필요합니다.")
            host = parsed.hostname.lower()
            if host == "localhost" or ":" in host or all(c.isdigit() or c == "." for c in host):
                raise ValueError("localhost 또는 IP 주소 대신 S3 HTTPS 호스트를 사용하세요.")
            if PurePosixPath(unquote(parsed.path)).suffix.lower() not in MIME_TYPES:
                raise ValueError("이미지 URL 경로는 jpg, jpeg, png, webp 확장자가 필요합니다.")
        return urls


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/products/extract", response_model=ProductAnalysis)
def extract_product(request: ImageUrlRequest) -> ProductAnalysis:
    images = [
        (url, MIME_TYPES[PurePosixPath(unquote(urlsplit(url).path)).suffix.lower()])
        for url in request.image_urls
    ]
    try:
        return analyze_images_urls(images)
    except ClientError as error:
        # presigned URL이 로그에 노출되지 않도록 예외 본문은 기록하지 않습니다.
        logger.warning("Gemini URL 분석 요청 실패: HTTP %s", error.code)
        if error.code == 429:
            raise HTTPException(429, "Gemini 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.") from error
        raise HTTPException(502, "Gemini 이미지 URL 분석에 실패했습니다. URL 만료 및 접근 권한을 확인해 주세요.") from error
    except ValidationError as error:
        logger.warning("Gemini URL 분석 응답 형식 오류")
        raise HTTPException(502, "Gemini 응답 형식이 올바르지 않습니다.") from error
    except ValueError as error:
        logger.warning("Gemini URL 분석 설정 또는 빈 응답 오류")
        raise HTTPException(500, "Gemini 설정 또는 응답을 확인해 주세요.") from error
    except Exception as error:
        logger.warning("Gemini URL 분석 호출 실패")
        raise HTTPException(502, "Gemini 이미지 분석 호출에 실패했습니다.") from error
