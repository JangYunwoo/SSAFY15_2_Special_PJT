package com.gongguhan.domain.account.dto;

import com.gongguhan.domain.account.entity.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** USR-01 사용자 프로필. FE UserProfile 계약 + 환불 수취 계좌(ssafyAccountNo, S15P21B205-63). */
public record UserProfileResponse(
        String id,
        String email,
        String nickname,
        String phone,
        boolean isSeller,
        OffsetDateTime createdAt,
        String ssafyAccountNo,
        boolean ssafyLinked
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                String.valueOf(user.getUserId()),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.isSeller(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().atOffset(ZoneOffset.UTC),
                user.getSsafyAccountNo(),
                user.getSsafyUserKey() != null && !user.getSsafyUserKey().isBlank());
    }
}
