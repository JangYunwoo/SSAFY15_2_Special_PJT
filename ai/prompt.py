"""상품 이미지 분석에 공통으로 사용하는 프롬프트."""

from category_catalog import CATEGORY_NAMES

CATEGORY_CATALOG_TEXT = "\n".join(
    f"- {category_id.value}: {name}" for category_id, name in CATEGORY_NAMES.items()
)

PRODUCT_ANALYSIS_PROMPT = f"""
당신은 공동구매 판매자가 업로드한 상품 사진을 분석합니다.

ERD에 정의된 AI 추출 대상만 분석하세요.
- 상품명: products.name
- 카테고리: products.category_id

카테고리 목록(category_id: name):
{CATEGORY_CATALOG_TEXT}

규칙:
1. 이미지에서 직접 확인할 수 있는 정보를 우선 사용하세요.
2. 포장지, 라벨, 인쇄 문구에서 고유한 상품명을 확인할 수 있을 때만 result_json.name에 작성하세요.
3. 이미지에 해당하는 카테고리를 위 목록에서 하나만 선택하고, 그 항목의 category_id를 result_json.category_id에 작성하세요.
4. 카테고리가 명확하지 않으면 제품의 외형, 구성, 조리 형태를 기반으로 목록에서 가장 가능성 높은 category_id를 선택하세요.
5. 합리적인 카테고리를 판단할 수 없으면 기타의 category_id인 10020을 반환하세요.
6. 상품명이 이미지의 글자로 명확히 확인되거나 하나로 확정 가능하면 result_json.name에 해당 상품명을 작성하고 name_candidates는 빈 배열로 반환하세요.
7. 상품명을 하나로 확정하기 어렵지만 구체적인 후보를 판단할 수 있으면 result_json.name은 null로 반환하고, name_candidates에 가능한 상품명을 가능성이 높은 순서대로 최대 3개 작성하세요.
8. 확인 가능한 후보만 반환하며 개수를 맞추기 위해 후보를 만들어내지 마세요. 후보를 판단할 수 없으면 name_candidates를 빈 배열로 반환하세요.
9. name_candidates에는 브랜드, 제품군, 모델명 등 상품을 구별할 수 있는 구체적인 상품명만 작성하세요. 스마트폰, 식품, 의류, 세제처럼 일반 품목명이나 카테고리명은 후보로 반환하지 마세요. 구체적인 후보가 부족하면 3개를 채우지 마세요.
10. 이미지에 상품명이나 모델명이 글자로 명확히 표시된 경우 해당 표기를 최우선으로 사용하고 다른 세대나 유사 모델을 후보로 추가하지 마세요.
11. 브랜드나 고유 상품명이 없는 농산물은 참외, 사과, 감자처럼 일반적으로 통용되는 품목명을 result_json.name에 상품명으로 작성할 수 있습니다.
12. category_id는 반드시 위 목록에 존재하는 ID 중 하나만 반환하고, 카테고리 이름은 별도로 반환하지 마세요.
13. 분석에 성공하면 status는 COMPLETED로 반환하세요.
14. 이미지를 분석할 수 없으면 status는 FAILED, result_json은 null로 반환하고 error_message에 실패 이유를 작성하세요.
15. 브랜드, 모델명, 색상, 가격, 배송비, 재고, 공동구매 조건은 별도 정보로 추출하지 마세요.
16. 예를 들어 스마트폰은 10001, 섬유유연제는 10004, 참외는 10021, 닭고기 요리는 10016으로 작성하세요.
17. JSON 객체 외의 설명이나 마크다운은 반환하지 마세요.
18. 상품명은 반드시 한국어로 작성하세요.
""".strip()


QWEN_PRODUCT_ANALYSIS_PROMPT = f"""{PRODUCT_ANALYSIS_PROMPT}

다음 모양의 JSON 객체만 반환하세요:
{{
  "status": "COMPLETED",
  "result_json": {{
    "name": "상품명 또는 null",
    "name_candidates": [],
    "category_id": 10020
  }},
  "error_message": null
}}
"""
