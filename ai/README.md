# 공구한 AI 상품 이미지 분석 PoC

상품 이미지 한 장을 Gemini API로 분석하고 결과를 JSON으로 출력합니다. Qwen과 YOLO 코드는 초기 모델 비교 기록으로 `experiments/`에 보관합니다.

## PoC 범위

- ERD `products.name`에 대응하는 상품명 추출
- ERD `products.category_id`에 대응하는 카테고리 추정
- 상품명이 불명확하면 사용자가 선택할 수 있는 후보를 최대 3개 출력

브랜드, 모델명, 색상, 가격은 별도 필드로 추출하지 않습니다. 상품정보 웹 검색, FastAPI, 백엔드 연동, DB 저장도 포함하지 않습니다.

## 사전 준비

1. Python 가상환경을 활성화합니다.

```powershell
cd ai
.\venv\Scripts\Activate.ps1
```

2. 개발 의존성을 설치합니다.

```powershell
python -m pip install -r requirements-dev.txt
```

3. Qwen 비교 실험이 필요한 경우에만 Ollama와 이미지 입력 모델을 준비합니다.

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
```

초기 비교 실험 코드는 다음과 같이 실행합니다.

```powershell
python -m experiments.qwen_analyze C:\path\to\product.jpg
python .\experiments\yolo_detect.py C:\path\to\product.jpg
```

지원 이미지 형식은 JPG, JPEG, PNG, WEBP입니다. 결과는 터미널에 JSON으로 출력됩니다.

## 현재 구조

```text
ai/
├── venv/
├── experiments/
│   ├── qwen_analyze.py
│   └── yolo_detect.py
├── analysis_schema.py
├── prompt.py
├── gemini_analyze.py
├── .env.example
├── .gitignore
├── requirements.txt
├── requirements-dev.txt
└── README.md
```
