# syntax=docker/dockerfile:1
#
# AI 상품 이미지 분석 서비스(FastAPI) 프로덕션 이미지.
#
# 빌드 컨텍스트는 ai/ 디렉터리 기준이다.
# 런타임에 Google Vertex AI(Gemini) 를 호출하므로, 인증은 서비스계정 키(JSON)를
# GOOGLE_APPLICATION_CREDENTIALS 로 주입한다(키 파일은 compose 에서 볼륨 마운트).

FROM python:3.12-slim AS run
WORKDIR /app

# 파이썬 런타임 권장 설정: .pyc 미생성, 출력 버퍼링 해제(로그 즉시 노출).
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 의존성 먼저 설치해 레이어 캐시를 살린다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션 소스 복사. experiments/ 등 무거운 실험 코드는 requirements 에 없으므로
# import 되지 않는다(app.py 는 gemini_analyze 만 사용).
COPY . .

# root 아닌 사용자로 실행.
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && chown -R app:app /app
USER app

EXPOSE 8000

# uvicorn 워커 1개(단일 EC2, GPU 없음, Gemini 호출은 I/O 대기가 대부분).
# 부하가 늘면 --workers 를 올리거나 gunicorn+uvicorn worker 로 교체한다.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
