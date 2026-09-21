package com.gongguhan.domain.recommendation.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 환경변수를 명시한 로컬 실행에서만 추천 통합 테스트 데이터를 생성한다. */
@Component
@Profile("local")
public class RecommendationLocalSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationLocalSeedService.class);
    private static final String TEST_PASSWORD = "Recommend1234!";
    private static final long SELLER_ID = 970000L;
    private static final long[] BUYER_IDS = {970101L, 970102L, 970103L};
    private static final int EVENTS_PER_USER = 120;

    private static final List<Catalog> CATALOGS = List.of(
            new Catalog(10016L, "가공식품", List.of("맛있는청년들", "춘천마켓", "집밥연구소"),
                    List.of("닭갈비", "만두", "떡볶이", "볶음밥")),
            new Catalog(10004L, "생활/주방", List.of("프로코", "클린데이", "살림백서"),
                    List.of("섬유유연제", "세탁세제", "주방세제", "화장지")),
            new Catalog(10021L, "농산물", List.of("참달아", "제주농장", "산지마켓"),
                    List.of("참외", "감귤", "사과", "포도")),
            new Catalog(10001L, "디지털기기", List.of("삼성전자", "애플", "테크온"),
                    List.of("스마트폰", "이어폰", "충전기", "키보드")),
            new Catalog(10017L, "건강기능식품", List.of("종근당", "바이탈랩", "정담원"),
                    List.of("비타민", "유산균", "홍삼", "단백질쉐이크")),
            new Catalog(10010L, "뷰티/미용", List.of("퓨어랩", "글로우업", "스킨온"),
                    List.of("선크림", "마스크팩", "토너", "클렌저")));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RecommendationProperties properties;

    public RecommendationLocalSeedService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            RecommendationProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seedEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        seedUsers(now);
        List<SeedProduct> products = seedProductsAndGroupBuys(now);
        seedBehavior(products, now);
        seedPurchases(products, now);
        log.info("로컬 추천 시드 완료: 상품={}개, 사용자별 행동={}건, 로그인={} / {}",
                products.size(), EVENTS_PER_USER, "recommend101@gongguhan.test", TEST_PASSWORD);
    }

    private void seedUsers(LocalDateTime now) {
        String passwordHash = passwordEncoder.encode(TEST_PASSWORD);
        String userSql = """
                INSERT INTO users
                       (user_id, email, password_hash, nickname, phone_verified,
                        is_seller, status, created_at, updated_at)
                VALUES (:userId, :email, :passwordHash, :nickname, TRUE,
                        :isSeller, 'ACTIVE', :now, :now)
                ON DUPLICATE KEY UPDATE
                       password_hash = VALUES(password_hash),
                       nickname = VALUES(nickname),
                       is_seller = VALUES(is_seller),
                       status = 'ACTIVE',
                       updated_at = VALUES(updated_at)
                """;
        jdbcTemplate.update(userSql, new MapSqlParameterSource()
                .addValue("userId", SELLER_ID)
                .addValue("email", "recommend.seller@gongguhan.test")
                .addValue("passwordHash", passwordHash)
                .addValue("nickname", "추천데모 판매자")
                .addValue("isSeller", true)
                .addValue("now", timestamp(now)));
        for (int index = 0; index < BUYER_IDS.length; index++) {
            jdbcTemplate.update(userSql, new MapSqlParameterSource()
                    .addValue("userId", BUYER_IDS[index])
                    .addValue("email", "recommend10" + (index + 1) + "@gongguhan.test")
                    .addValue("passwordHash", passwordHash)
                    .addValue("nickname", "추천데모 구매자 " + (index + 1))
                    .addValue("isSeller", false)
                    .addValue("now", timestamp(now)));
        }
        jdbcTemplate.update("""
                INSERT INTO seller_profiles
                       (seller_id, business_name, account_verified, penalty_count,
                        status, verified_at, created_at, updated_at)
                VALUES (:sellerId, '추천 알고리즘 데모마켓', TRUE, 0,
                        'ACTIVE', :now, :now, :now)
                ON DUPLICATE KEY UPDATE
                       business_name = VALUES(business_name),
                       account_verified = TRUE,
                       penalty_count = 0,
                       status = 'ACTIVE',
                       updated_at = VALUES(updated_at)
                """, Map.of("sellerId", SELLER_ID, "now", timestamp(now)));
    }

    private List<SeedProduct> seedProductsAndGroupBuys(LocalDateTime now) {
        List<SeedProduct> products = new ArrayList<>();
        Random random = new Random(42);
        int sequence = 1;
        for (Catalog catalog : CATALOGS) {
            for (int itemIndex = 0; itemIndex < catalog.names().size(); itemIndex++) {
                String brand = catalog.brands().get(itemIndex % catalog.brands().size());
                String name = brand + " " + catalog.names().get(itemIndex);
                long productId = 971000L + sequence;
                long groupBuyId = 972000L + sequence;
                long listPrice = 60_000L + random.nextInt(10) * 10_000L;
                long productPrice = (long) (listPrice * (0.70 + random.nextDouble() * 0.20));
                int target = 10 + random.nextInt(5) * 5;
                int current = 1 + random.nextInt(Math.max(1, target - 1));
                long unitAmount = (productPrice + target - 1) / target;
                LocalDateTime endAt = now.plusHours(12L + random.nextInt(156));
                products.add(new SeedProduct(
                        productId, groupBuyId, catalog.categoryId(), brand, name,
                        listPrice, productPrice, target, current, endAt));
                seedProduct(products.getLast(), sequence, now, unitAmount);
                sequence++;
            }
        }
        return products;
    }

    private void seedProduct(
            SeedProduct product, int sequence, LocalDateTime now, long unitAmount) {
        jdbcTemplate.update("""
                INSERT INTO products
                       (product_id, seller_id, category_id, name, brand, description,
                        price, ai_extracted, withdrawal_restricted,
                        restriction_review_status, status, created_at, updated_at)
                VALUES (:productId, :sellerId, :categoryId, :name, :brand,
                        '개인화 추천 로컬 통합 테스트용 가상 상품입니다.',
                        :listPrice, FALSE, FALSE, 'NONE', 'ACTIVE', :now, :now)
                ON DUPLICATE KEY UPDATE
                       category_id = VALUES(category_id), name = VALUES(name),
                       brand = VALUES(brand), description = VALUES(description),
                       price = VALUES(price), status = 'ACTIVE', updated_at = VALUES(updated_at)
                """, new MapSqlParameterSource()
                .addValue("productId", product.productId())
                .addValue("sellerId", SELLER_ID)
                .addValue("categoryId", product.categoryId())
                .addValue("name", product.name())
                .addValue("brand", product.brand())
                .addValue("listPrice", product.listPrice())
                .addValue("now", timestamp(now)));
        jdbcTemplate.update("""
                INSERT INTO product_images
                       (image_id, product_id, image_url, sort_order, is_thumbnail, created_at)
                VALUES (:imageId, :productId, :imageUrl, 0, TRUE, :now)
                ON DUPLICATE KEY UPDATE
                       product_id = VALUES(product_id), image_url = VALUES(image_url),
                       sort_order = 0, is_thumbnail = TRUE
                """, new MapSqlParameterSource()
                .addValue("imageId", 973000L + sequence)
                .addValue("productId", product.productId())
                .addValue("imageUrl", "https://placehold.co/640x480?text=RECO-" + sequence)
                .addValue("now", timestamp(now)));
        long platformFee = product.productPrice() * 5 / 100;
        long shippingFee = 3_000L;
        jdbcTemplate.update("""
                INSERT INTO group_buys
                       (group_buy_id, product_id, seller_id, product_price, target_participants,
                        unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                        expected_platform_fee, expected_settlement_amount,
                        recruit_start_at, recruit_end_at, current_participants, reserved_slots,
                        status, version, created_at, updated_at)
                VALUES (:groupBuyId, :productId, :sellerId, :productPrice, :target,
                        :unitAmount, :shippingFee, :roundingDiff, 0.0500,
                        :platformFee, :settlementAmount,
                        :startAt, :endAt, :current, 0,
                        'RECRUITING', 0, :now, :now)
                ON DUPLICATE KEY UPDATE
                       product_price = VALUES(product_price),
                       target_participants = VALUES(target_participants),
                       unit_share_amount = VALUES(unit_share_amount),
                       shipping_fee = VALUES(shipping_fee),
                       current_participants = VALUES(current_participants),
                       recruit_start_at = VALUES(recruit_start_at),
                       recruit_end_at = VALUES(recruit_end_at),
                       status = 'RECRUITING', version = 0, updated_at = VALUES(updated_at)
                """, new MapSqlParameterSource()
                .addValue("groupBuyId", product.groupBuyId())
                .addValue("productId", product.productId())
                .addValue("sellerId", SELLER_ID)
                .addValue("productPrice", product.productPrice())
                .addValue("target", product.target())
                .addValue("unitAmount", unitAmount)
                .addValue("shippingFee", shippingFee)
                .addValue("roundingDiff", unitAmount * product.target() - product.productPrice())
                .addValue("platformFee", platformFee)
                .addValue("settlementAmount",
                        product.productPrice() - platformFee + shippingFee * product.target())
                .addValue("startAt", timestamp(now.minusDays(1)))
                .addValue("endAt", timestamp(product.endAt()))
                .addValue("current", product.current())
                .addValue("now", timestamp(now)));
    }

    private void seedBehavior(List<SeedProduct> products, LocalDateTime now) {
        Random random = new Random(205);
        long eventId = 974000L;
        for (int userIndex = 0; userIndex < BUYER_IDS.length; userIndex++) {
            long userId = BUYER_IDS[userIndex];
            long preferredCategory = CATALOGS.get(userIndex * 2).categoryId();
            String preferredBrand = CATALOGS.get(userIndex * 2).brands().getFirst();
            for (int sessionIndex = 0; sessionIndex < 6; sessionIndex++) {
                String sessionId = "00000000-0000-0000-%04d-%012d"
                        .formatted(userIndex + 1, 970_000L + userIndex * 10L + sessionIndex);
                LocalDateTime sessionTime = now.minusDays(7L - sessionIndex).minusHours(2);
                jdbcTemplate.update("""
                        INSERT INTO recommendation_sessions
                               (session_id, user_id, started_at, last_event_at, ended_at, end_reason)
                        VALUES (:sessionId, :userId, :startedAt, :endedAt, :endedAt, 'PAGEHIDE')
                        ON DUPLICATE KEY UPDATE
                               user_id = VALUES(user_id), started_at = VALUES(started_at),
                               last_event_at = VALUES(last_event_at), ended_at = VALUES(ended_at),
                               end_reason = 'PAGEHIDE'
                        """, Map.of(
                                "sessionId", sessionId,
                                "userId", userId,
                                "startedAt", timestamp(sessionTime),
                                "endedAt", timestamp(sessionTime.plusMinutes(20))));
                for (int sessionEvent = 0; sessionEvent < 20; sessionEvent++) {
                    SeedProduct product = products.get(random.nextInt(products.size()));
                    double preference = 0.10;
                    if (product.categoryId() == preferredCategory) {
                        preference += 0.42;
                    }
                    if (product.brand().equals(preferredBrand)) {
                        preference += 0.30;
                    }
                    double roll = random.nextDouble();
                    String eventType = roll < preference * 0.28
                            ? "DETAIL_VIEW"
                            : roll < preference ? "CLICK" : "IMPRESSION";
                    double recruitmentRate = ratio(product.current(), product.target());
                    double discountRate = ratio(
                            product.listPrice() - product.productPrice(), product.listPrice());
                    double deadlineUrgency = deadlineUrgency(sessionTime, product.endAt());
                    jdbcTemplate.update("""
                            INSERT INTO recommendation_events
                                   (event_id, session_id, user_id, group_buy_id, event_type,
                                    source, position, product_id_snapshot, brand_snapshot,
                                    category_id_snapshot, recruitment_rate, discount_rate,
                                    deadline_urgency, occurred_at)
                            VALUES (:eventId, :sessionId, :userId, :groupBuyId, :eventType,
                                    'LOCAL_SEED', :position, :productId, :brand,
                                    :categoryId, :recruitmentRate, :discountRate,
                                    :deadlineUrgency, :occurredAt)
                            ON DUPLICATE KEY UPDATE
                                   session_id = VALUES(session_id), user_id = VALUES(user_id),
                                   group_buy_id = VALUES(group_buy_id), event_type = VALUES(event_type),
                                   source = 'LOCAL_SEED', position = VALUES(position),
                                   product_id_snapshot = VALUES(product_id_snapshot),
                                   brand_snapshot = VALUES(brand_snapshot),
                                   category_id_snapshot = VALUES(category_id_snapshot),
                                   recruitment_rate = VALUES(recruitment_rate),
                                   discount_rate = VALUES(discount_rate),
                                   deadline_urgency = VALUES(deadline_urgency),
                                   occurred_at = VALUES(occurred_at)
                            """, new MapSqlParameterSource()
                            .addValue("eventId", ++eventId)
                            .addValue("sessionId", sessionId)
                            .addValue("userId", userId)
                            .addValue("groupBuyId", product.groupBuyId())
                            .addValue("eventType", eventType)
                            .addValue("position", sessionEvent)
                            .addValue("productId", product.productId())
                            .addValue("brand", product.brand())
                            .addValue("categoryId", product.categoryId())
                            .addValue("recruitmentRate", recruitmentRate)
                            .addValue("discountRate", discountRate)
                            .addValue("deadlineUrgency", deadlineUrgency)
                            .addValue("occurredAt", timestamp(sessionTime.plusMinutes(sessionEvent))));
                }
            }
        }
    }

    private void seedPurchases(List<SeedProduct> products, LocalDateTime now) {
        long participationId = 975000L;
        for (int userIndex = 0; userIndex < BUYER_IDS.length; userIndex++) {
            long categoryId = CATALOGS.get(userIndex * 2).categoryId();
            List<SeedProduct> preferred = products.stream()
                    .filter(product -> product.categoryId() == categoryId)
                    .limit(2)
                    .toList();
            for (SeedProduct product : preferred) {
                long unitAmount = (product.productPrice() + product.target() - 1) / product.target();
                jdbcTemplate.update("""
                        INSERT INTO participations
                               (participation_id, group_buy_id, user_id, share_amount,
                                shipping_fee, total_amount, recipient_name, recipient_phone,
                                zipcode, address1, address2, status, joined_at,
                                confirm_notice_agreed, confirm_hold, settled,
                                active_marker, updated_at)
                        VALUES (:participationId, :groupBuyId, :userId, :shareAmount,
                                3000, :totalAmount, '추천데모 구매자', '01000000000',
                                '34134', '대전광역시 유성구', '로컬 추천 테스트', 'PAID', :joinedAt,
                                FALSE, FALSE, FALSE, 'Y', :now)
                        ON DUPLICATE KEY UPDATE
                               group_buy_id = VALUES(group_buy_id), user_id = VALUES(user_id),
                               share_amount = VALUES(share_amount), total_amount = VALUES(total_amount),
                               status = 'PAID', joined_at = VALUES(joined_at),
                               active_marker = 'Y', updated_at = VALUES(updated_at)
                        """, new MapSqlParameterSource()
                        .addValue("participationId", ++participationId)
                        .addValue("groupBuyId", product.groupBuyId())
                        .addValue("userId", BUYER_IDS[userIndex])
                        .addValue("shareAmount", unitAmount)
                        .addValue("totalAmount", unitAmount + 3_000L)
                        .addValue("joinedAt", timestamp(now.minusDays(3L + preferred.indexOf(product))))
                        .addValue("now", timestamp(now)));
            }
        }
    }

    private Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : Math.max(0, Math.min(1, numerator * 1.0 / denominator));
    }

    private double deadlineUrgency(LocalDateTime from, LocalDateTime deadline) {
        long remainingHours = java.time.Duration.between(from, deadline).toHours();
        return Math.max(0, Math.min(1, 1 - Math.max(0, Math.min(168, remainingHours)) / 168.0));
    }

    private record Catalog(
            long categoryId,
            String categoryName,
            List<String> brands,
            List<String> names
    ) {
    }

    private record SeedProduct(
            long productId,
            long groupBuyId,
            long categoryId,
            String brand,
            String name,
            long listPrice,
            long productPrice,
            int target,
            int current,
            LocalDateTime endAt
    ) {
    }
}
