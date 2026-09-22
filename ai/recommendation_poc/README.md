# 추천 알고리즘 PoC

목업 데이터용 Streamlit 화면과 실제 서비스가 함께 사용하는 추천 코어입니다.

Git Bash에서 `ai` 폴더로 이동한 뒤 실행합니다.

```bash
source venv/Scripts/activate
pip install -r requirements.txt
python recommendation_poc/generate_mock_data.py
streamlit run recommendation_poc/demo.py
```

브라우저에서 `http://localhost:8501`을 열고 사용자를 변경하면 추천 순위가 달라집니다.
행동 로그 시뮬레이터에서 상품과 행동을 선택하면 추천 순위가 다시 계산됩니다.
`로그 초기화`를 누르면 원본 목업 로그로 돌아갑니다.

- `recommender.py`: LightGBM Learning-to-Rank 기반 추천 코어
- `demo.py`: 나중에 React로 대체할 임시 화면
- `mock_data/`: 나중에 Java 백엔드와 DB 응답으로 대체할 데이터
- `generate_mock_data.py`: 사용자 3명 × 행동 로그 150건을 재생성

사용자 선호도는 브랜드 수만큼 칼럼을 만들지 않습니다. 사용자가 실제 반응한
브랜드와 카테고리만 `(user_id, brand/category_id, affinity)` 형태의 행으로
계산합니다. 현재 ERD에는 브랜드 마스터가 없어 `products.brand` 문자열을 키로
사용하며, 브랜드 테이블이 생기면 `brand_id`로 교체할 수 있습니다.

상품과 공동구매 컬럼은 `docs/database/공구한_schema_v2.sql`을 따릅니다.
실서비스는 `recommendation_sessions`, `recommendation_events`에 행동을 저장하고,
구매 정답은 `participations`에서 읽습니다. 행동 직전의 선호도만 학습 특징으로 만들어
현재 행동의 정답이 입력에 섞이는 데이터 누수를 막습니다.

FastAPI의 `POST /api/v1/recommendations/rank`가 백엔드에서 받은 후보와 행동 로그를
정렬합니다. 한 세션에 함께 노출된 상품을 하나의 순위 학습 그룹으로 구성하고,
`IMPRESSION=0`, `CLICK·DETAIL_VIEW=1`, `PURCHASE=3`을 관련도 정답으로 사용합니다.
같은 세션에서 같은 상품에 행동이 여러 번 발생하면 가장 높은 관련도만 사용합니다.

유효 학습 행이 40개 미만이거나 비교 가능한 세션이 5개 미만이면 초기 가중치를
사용합니다. LightGBM이 학습된 경우 응답의 `feature_weights`는 회귀계수가 아니라
각 특징의 정규화된 gain 중요도입니다. AI 서버가 꺼져 있으면 Spring 백엔드가 모집률
순으로 대체하므로 홈 화면은 계속 동작합니다.
