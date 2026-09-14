package com.gongguhan.domain.groupbuy.repository;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/** 공동구매 생성 명령을 JPA 엔티티 생성으로 연결한다. */
@Repository
public class GroupBuyCommandRepository {

    private final GroupBuyRepository groupBuys;

    public GroupBuyCommandRepository(GroupBuyRepository groupBuys) {
        this.groupBuys = groupBuys;
    }

    public StoredGroupBuy create(GroupBuyCreateCommand command) {
        GroupBuy groupBuy = GroupBuy.create(
                command.productId(), command.sellerId(), command.productPrice(),
                command.targetParticipants(), command.unitShareAmount(), command.shippingFee(),
                command.roundingDiff(), command.platformFeeRate(), command.expectedPlatformFee(),
                command.expectedSettlementAmount(), command.recruitStartAt(), command.recruitEndAt());
        // 바로 뒤의 JDBC 운영 상세 조회에서도 같은 트랜잭션의 신규 행을 볼 수 있게 flush한다.
        GroupBuy stored = groupBuys.saveAndFlush(groupBuy);
        return new StoredGroupBuy(
                stored.getGroupBuyId(), stored.getCreatedAt(), stored.getUpdatedAt());
    }

    public record StoredGroupBuy(Long groupBuyId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
