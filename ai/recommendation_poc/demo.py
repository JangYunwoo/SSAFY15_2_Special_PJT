from pathlib import Path
from datetime import datetime

import pandas as pd
import streamlit as st

from recommender import recommend


DATA_DIR = Path(__file__).parent / "mock_data"

st.set_page_config(page_title="공구한 추천 PoC", layout="wide")
st.title("공구한 개인화 추천 PoC")
st.caption("브랜드 × 카테고리 행동 선호도와 현재 모집률을 이용한 임시 데모")

products = pd.read_csv(DATA_DIR / "products.csv")
base_events = pd.read_csv(DATA_DIR / "user_product_events.csv")
if "live_events" not in st.session_state:
    st.session_state.live_events = base_events.copy()
events = st.session_state.live_events
user_ids = sorted(events["user_id"].unique().tolist())
user_id = st.selectbox("사용자", user_ids)

st.subheader("행동 로그 시뮬레이터")
product_options = products.set_index("group_buy_id").apply(
    lambda row: f"{row['name']} · {row['category_name']} (공구 {row.name})", axis=1
)
selected_group_buy_id = st.selectbox(
    "행동을 남길 상품",
    product_options.index,
    format_func=lambda group_buy_id: product_options.loc[group_buy_id],
)

event_columns = st.columns(5)
event_buttons = [
    ("노출", "IMPRESSION"),
    ("클릭", "CLICK"),
    ("상세조회", "DETAIL_VIEW"),
    ("구매", "PURCHASE"),
]
added_event = None
for column, (label, event_type) in zip(event_columns[:4], event_buttons):
    with column:
        if st.button(label, use_container_width=True):
            added_event = event_type

with event_columns[4]:
    if st.button("로그 초기화", use_container_width=True):
        st.session_state.live_events = base_events.copy()
        st.rerun()

if added_event is not None:
    next_event_id = int(events["event_id"].max()) + 1
    new_event = pd.DataFrame(
        [
            {
                "event_id": next_event_id,
                "user_id": int(user_id),
                "group_buy_id": int(selected_group_buy_id),
                "event_type": added_event,
                "occurred_at": datetime.now().isoformat(timespec="seconds"),
            }
        ]
    )
    st.session_state.live_events = pd.concat(
        [events, new_event], ignore_index=True
    )
    events = st.session_state.live_events
    st.success(f"{added_event} 로그를 추가해 추천 순위를 다시 계산했습니다.")

result = recommend(products, events, int(user_id))
recommendations = result.items
st.dataframe(
    recommendations[
        ["product_id", "name", "brand", "category_name", "recruitment_rate", "discount_rate", "score"]
    ].rename(columns={"product_id": "상품 등록번호"}).style.format(
        {"recruitment_rate": "{:.0%}", "score": "{:.3f}"}
    ),
    use_container_width=True,
    hide_index=True,
)

with st.expander("모델이 학습한 공통 가중치"):
    st.dataframe(result.feature_weights, use_container_width=True, hide_index=True)

st.subheader(f"{user_id}번 사용자의 선호 프로필")
brand_column, category_column = st.columns(2)
with brand_column:
    st.caption("반응한 브랜드만 저장·계산")
    st.dataframe(
        result.brand_preferences.style.format({"affinity": "{:.3f}"}),
        use_container_width=True,
        hide_index=True,
    )
with category_column:
    st.caption("반응한 카테고리만 저장·계산")
    st.dataframe(
        result.category_preferences.style.format({"affinity": "{:.3f}"}),
        use_container_width=True,
        hide_index=True,
    )

with st.expander("선택한 사용자의 행동 로그"):
    st.dataframe(
        events.loc[events["user_id"] == user_id].sort_values(
            "event_id", ascending=False
        ),
        use_container_width=True,
        hide_index=True,
    )
