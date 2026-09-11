from __future__ import annotations

import csv
import random
from datetime import datetime, timedelta
from pathlib import Path


SEED = 42
NOW = datetime(2026, 9, 7, 12, 0, 0)
DATA_DIR = Path(__file__).parent / "mock_data"
CATEGORIES = {
    1: ("간편식", ["맛있는청년들", "춘천마켓", "집밥연구소"], ["닭갈비", "만두", "떡볶이", "볶음밥"]),
    2: ("생활용품", ["프로코", "클린데이", "살림백서"], ["섬유유연제", "세탁세제", "주방세제", "휴지"]),
    3: ("과일", ["참달아", "제주농장", "산지마켓"], ["참외", "감귤", "사과", "포도"]),
    4: ("디지털", ["사운드픽", "모바일핏", "테크온"], ["이어폰", "충전기", "키보드", "스마트워치"]),
    5: ("건강식품", ["건강한오늘", "바이탈랩", "정담원"], ["유산균", "비타민", "홍삼", "단백질쉐이크"]),
    6: ("뷰티", ["퓨어랩", "글로우업", "스킨온"], ["선크림", "마스크팩", "토너", "클렌저"]),
}
PERSONAS = {
    101: {"categories": {1, 3}, "brands": {"맛있는청년들", "참달아"}},
    102: {"categories": {2, 6}, "brands": {"프로코", "퓨어랩"}},
    103: {"categories": {4, 5}, "brands": {"테크온", "바이탈랩"}},
}


def create_products(rng: random.Random) -> list[dict]:
    products = []
    product_id = 1
    for category_id, (category, brands, names) in CATEGORIES.items():
        for brand in brands:
            for item_name in names:
                price = rng.randrange(15_000, 150_001, 1_000)
                discount = rng.uniform(0.05, 0.35)
                target = rng.randrange(20, 101, 10)
                products.append({
                    "product_id": product_id,
                    "seller_id": 1000 + product_id,
                    "category_id": category_id,
                    "category_name": category,
                    "name": f"{brand} {item_name}",
                    "brand": brand,
                    "price": price,
                    "group_buy_id": 5000 + product_id,
                    "product_price": int(price * (1 - discount) // 100 * 100),
                    "target_participants": target,
                    "current_participants": rng.randint(0, target),
                    "recruit_end_at": (NOW + timedelta(hours=rng.randint(12, 168))).isoformat(),
                    "status": "RECRUITING",
                })
                product_id += 1
    return products


def create_events(rng: random.Random, products: list[dict], count_per_user: int = 150) -> list[dict]:
    events = []
    event_id = 1
    for user_id, persona in PERSONAS.items():
        for index in range(count_per_user):
            product = rng.choice(products)
            preference = 0.08
            if product["category_id"] in persona["categories"]:
                preference += 0.32
            if product["brand"] in persona["brands"]:
                preference += 0.30
            recruitment_rate = product["current_participants"] / product["target_participants"]
            preference += 0.12 * recruitment_rate
            roll = rng.random()
            if roll < preference * 0.10:
                event_type = "PURCHASE"
            elif roll < preference * 0.35:
                event_type = "DETAIL_VIEW"
            elif roll < preference:
                event_type = "CLICK"
            else:
                event_type = "IMPRESSION"
            events.append({
                "event_id": event_id,
                "user_id": user_id,
                "group_buy_id": product["group_buy_id"],
                "event_type": event_type,
                "occurred_at": (NOW - timedelta(hours=count_per_user - index)).isoformat(),
            })
            event_id += 1
    return events


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    rng = random.Random(SEED)
    DATA_DIR.mkdir(exist_ok=True)
    products = create_products(rng)
    events = create_events(rng, products)
    write_csv(DATA_DIR / "products.csv", products)
    write_csv(DATA_DIR / "user_product_events.csv", events)
    print(f"상품 {len(products)}개, 행동 로그 {len(events)}개를 생성했습니다.")


if __name__ == "__main__":
    main()
