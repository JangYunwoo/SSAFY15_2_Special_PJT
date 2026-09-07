# 공구한 AI 상품 이미지 분석 PoC

로컬 상품 이미지 한 장을 Ollama의 `qwen3-vl:4b-instruct`로 분석하고 결과를 JSON으로 출력합니다.

## PoC 범위

- ERD `products.name`에 대응하는 상품명 추출
- ERD `products.category_id`에 대응하는 카테고리 추정
- ERD `ai_extractions.result_json` 형식의 결과 출력
- ERD `ai_extractions.confidence_json` 형식의 신뢰도 출력

브랜드, 모델명, 색상, 가격은 ERD상 판매자 입력이므로 추출하지 않습니다. 상품정보 웹 검색, 별도 OCR, YOLO, FastAPI, 백엔드 연동, DB 저장도 포함하지 않습니다.

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

3. Ollama를 설치하고 이미지 입력이 가능한 모델을 준비합니다.

```powershell
ollama pull qwen3-vl:4b-instruct
```

일반 `qwen3:4b`는 이미지 입력 모델이 아니므로 사용할 수 없습니다.

4. 필요하면 `.env.example`을 참고해 환경변수를 설정합니다. `.env`는 Git에 포함되지 않습니다.

```powershell
$env:OLLAMA_HOST = "http://localhost:11434"
$env:OLLAMA_MODEL = "qwen3-vl:4b-instruct"
```

## 실행

`ai/` 디렉터리에서 분석할 이미지 경로를 전달합니다.

```powershell
python .\analyze.py C:\path\to\product.jpg
```

지원 이미지 형식은 JPG, JPEG, PNG, WEBP입니다. 결과는 터미널에 JSON으로 출력됩니다.

## 현재 구조

```text
ai/
├── venv/
├── analyze.py
├── .env.example
├── .gitignore
├── requirements.txt
├── requirements-dev.txt
└── README.md
```
