# 공구한 AI 상품 이미지 분석 PoC

메인 이미지 1장과 부연 이미지 최대 4장을 Gemini API로 함께 분석하고 결과 JSON 하나를 출력합니다. Qwen과 YOLO 코드는 초기 모델 비교 기록으로 `experiments/`에 보관합니다.

## PoC 범위

- ERD `products.name`에 대응하는 상품명 추출
- DB 카테고리 카탈로그에서 ERD `products.category_id`에 대응하는 ID 선택
- 상품명이 불명확하면 사용자가 선택할 수 있는 후보를 최대 3개 출력

브랜드와 색상은 이미지에서 확인 가능한 경우 추출하고 불명확하면 null로 반환합니다. 모델명·규격과 가격은 추출하지 않습니다. 명백히 무관한 상품 사진이 섞이면 FAILED와 문제 사진 번호를 반환합니다. 이는 모델의 판단이며 정확성을 보장하는 검사는 아닙니다. FastAPI를 제공하며 웹 검색과 DB 저장은 포함하지 않습니다.

## 사전 준비

Gemini 이미지 분석은 요청당 한 번만 호출합니다. 추가 웹 검색은 하지 않습니다.
확정할 수 없는 상품명은 후보 최대 3개 또는 null로 반환하며 판매자가 직접 수정합니다.
모델명·모델 코드·용량·규격은 추출하지 않으며 model_name은 항상 null로 반환합니다.

1. Python 가상환경을 활성화합니다.

```powershell
cd ai
.\venv\Scripts\Activate.ps1
```

2. 기본 의존성을 설치합니다.

```powershell
python -m pip install -r requirements.txt
```

테스트까지 실행할 개발자는 다음 파일을 사용합니다.

```powershell
python -m pip install -r requirements-dev.txt
```

3. Qwen·YOLO 비교 실험이 필요한 경우에만 실험 의존성과 Ollama 이미지 입력 모델을 준비합니다.

```powershell
python -m pip install -r requirements-experiments.txt
```

```powershell
ollama pull qwen3-vl:4b-instruct
```

일반 `qwen3:4b`는 이미지 입력 모델이 아니므로 사용할 수 없습니다.

4. 필요하면 `.env.example`을 참고해 환경변수를 설정합니다. `.env`는 Git에 포함되지 않습니다.

```powershell
$env:OLLAMA_HOST = "http://localhost:11434"
$env:OLLAMA_MODEL = "qwen3-vl:4b-instruct"
```

Gemini 테스트는 `ai/.env`에 다음 값을 설정합니다.

```env
GEMINI_PROJECT=실제_GOOGLE_CLOUD_PROJECT_ID
GEMINI_LOCATION=global
GEMINI_MODEL=gemini-3.8-flash
```

Enterprise API는 API 키 대신 Google Application Default Credentials를 사용합니다.

```powershell
gcloud auth application-default login
```

## 실행

`ai/` 디렉터리에서 Gemini에 분석할 이미지 경로를 전달합니다.

```powershell
python .\gemini_analyze.py C:\path\to\product.jpg
python .\gemini_analyze.py C:\path\to\main.jpg C:\path\to\sub1.jpg C:\path\to\sub2.jpg
```

초기 비교 실험 코드는 다음과 같이 실행합니다.

```powershell
python -m experiments.qwen_analyze C:\path\to\product.jpg
python .\experiments\yolo_detect.py C:\path\to\product.jpg
```

지원 이미지 형식은 JPG, JPEG, PNG, WEBP입니다. 결과는 터미널에 JSON으로 출력됩니다.

백엔드 연동용 FastAPI 서버는 `ai/` 디렉터리에서 실행합니다.

```powershell
uvicorn app:app --reload --port 8000
```

서비스 엔드포인트는 JSON의 `image_urls`로 S3 HTTPS 이미지 URL을 총 1~5개 받습니다. 첫 번째는 대표 이미지, 이후는 추가 이미지입니다. URL 경로에 jpg, jpeg, png, webp 확장자가 있어야 하며 URL 쿼리(서명)는 유지됩니다. Gemini가 직접 URL을 읽으므로 AWS 키나 AI 서버의 다운로드는 필요하지 않습니다. 비공개 S3 presigned GET URL의 실제 모델 지원은 연동 테스트로 확인해야 합니다. 백엔드도 기존 multipart 요청 대신 이 JSON 계약으로 변경해야 합니다.

```json
{"image_urls": ["https://bucket.s3.ap-northeast-2.amazonaws.com/main.jpg?서명"]}
```

개인 로컬 파일 첨부 테스트는 Git에서 제외된 `local_app.py`로 실행합니다.
이 파일은 저장소를 새로 받은 팀원에게 제공되지 않습니다.

```bash
python -m uvicorn local_app:app --reload --port 8000
```

Postman에서 같은 경로로 Body → form-data의 `images`(File) 필드를 반복하여 첨부합니다.
서비스 서버와 로컬 서버는 동시에 같은 포트를 사용할 수 없으므로 하나를 종료한 뒤 실행하세요.

## 현재 구조

```text
ai/
├── venv/
├── experiments/
│   ├── qwen_analyze.py
│   └── yolo_detect.py
├── analysis_schema.py
├── category_catalog.py
├── prompt.py
├── gemini_analyze.py
├── app.py
├── .env.example
├── .gitignore
├── requirements.txt
├── requirements-experiments.txt
├── requirements-dev.txt
└── README.md
```
