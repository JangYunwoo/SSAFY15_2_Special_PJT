# 추천 알고리즘 PoC

백엔드와 프론트엔드가 준비되기 전에 목업 데이터로 개인화 추천을 확인하는 공간입니다.

Git Bash에서 `ai` 폴더로 이동한 뒤 실행합니다.

```bash
source venv/Scripts/activate
pip install -r requirements.txt
python recommendation_poc/generate_mock_data.py
streamlit run recommendation_poc/demo.py
```

브라우저에서 `http://localhost:8501`을 열고 사용자를 변경하면 추천 순위가 달라집니다.
행동 로그 시뮬레이터에서 상품과 행동을 선택하면 세션 로그가 추가되고 추천 순위가
즉시 다시 계산됩니다. `로그 초기화`를 누르면 원본 목업 로그로 돌아갑니다.

- `recommender.py`: scikit-learn 로지스틱 회귀 기반 추천 초안
- `demo.py`: 나중에 React로 대체할 임시 화면
- `mock_data/`: 나중에 Java 백엔드와 DB 응답으로 대체할 데이터
- `generate_mock_data.py`: 사용자 3명 × 행동 로그 150건을 재생성

사용자 선호도는 브랜드 수만큼 칼럼을 만들지 않습니다. 사용자가 실제 반응한
브랜드와 카테고리만 `(user_id, brand/category_id, affinity)` 형태의 행으로
계산합니다. 현재 ERD에는 브랜드 마스터가 없어 `products.brand` 문자열을 키로
사용하며, 브랜드 테이블이 생기면 `brand_id`로 교체할 수 있습니다.

상품과 공동구매 컬럼은 `docs/공구한_schema_v2.sql`을 따릅니다. `event_type`과
`occurred_at`은 현 ERD에 없는 추천 PoC 전용 행동 로그입니다.
