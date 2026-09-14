"""DB categories 초기 데이터와 동일한 활성 카테고리 카탈로그."""

from enum import IntEnum


class CategoryId(IntEnum):
    DIGITAL_DEVICE = 10001
    HOME_APPLIANCE = 10002
    FURNITURE_INTERIOR = 10003
    LIVING_KITCHEN = 10004
    KIDS = 10005
    KIDS_BOOKS = 10006
    WOMENS_CLOTHING = 10007
    WOMENS_ACCESSORIES = 10008
    MENS_FASHION = 10009
    BEAUTY = 10010
    SPORTS_LEISURE = 10011
    HOBBY_GAME_MUSIC = 10012
    BOOKS = 10013
    TICKETS = 10014
    E_COUPON = 10015
    PROCESSED_FOOD = 10016
    HEALTH_SUPPLEMENTS = 10017
    PET_SUPPLIES = 10018
    PLANTS = 10019
    OTHER = 10020
    AGRICULTURAL_PRODUCTS = 10021


CATEGORY_NAMES = {
    CategoryId.DIGITAL_DEVICE: "디지털기기",
    CategoryId.HOME_APPLIANCE: "생활가전",
    CategoryId.FURNITURE_INTERIOR: "가구/인테리어",
    CategoryId.LIVING_KITCHEN: "생활/주방",
    CategoryId.KIDS: "유아동",
    CategoryId.KIDS_BOOKS: "유아도서",
    CategoryId.WOMENS_CLOTHING: "여성의류",
    CategoryId.WOMENS_ACCESSORIES: "여성잡화",
    CategoryId.MENS_FASHION: "남성패션/잡화",
    CategoryId.BEAUTY: "뷰티/미용",
    CategoryId.SPORTS_LEISURE: "스포츠/레저",
    CategoryId.HOBBY_GAME_MUSIC: "취미/게임/음반",
    CategoryId.BOOKS: "도서",
    CategoryId.TICKETS: "티켓/교환권",
    CategoryId.E_COUPON: "e쿠폰",
    CategoryId.PROCESSED_FOOD: "가공식품",
    CategoryId.HEALTH_SUPPLEMENTS: "건강기능식품",
    CategoryId.PET_SUPPLIES: "반려동물용품",
    CategoryId.PLANTS: "식물",
    CategoryId.OTHER: "기타",
    CategoryId.AGRICULTURAL_PRODUCTS: "농산물",
}
