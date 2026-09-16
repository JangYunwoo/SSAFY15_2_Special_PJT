package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.CardResponse;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 마이페이지 카드 온디맨드 발급·조회.
 *
 * <p>일반 가입자는 카드가 없어 카드 결제를 할 수 없다. 마이페이지에서 "카드 발급"을 누르면
 * SSAFY 교육망으로 카드를 발급하고, 이후 카드 정보를 조회해 결제창에 쓸 수 있게 한다.
 * 카드번호·CVC는 DB에 저장하지 않고 {@link SsafyCardIssueClient}로 매번 조회한다.
 *
 * <p>카드 발급에는 출금계좌가 필요하다 - 판매자 전환 등으로 이미 계좌가 있으면 그것을 쓰고,
 * 없으면 {@link SsafyAccountClient#createAccount}로 개설해 {@code users.ssafy_account_no}에 저장한다.
 * 외부 호출은 트랜잭션 밖에서 하고 계좌번호 저장만 짧은 트랜잭션으로 처리한다(원자성 규칙).
 */
@Service
public class CardIssuanceService {

    private final UserRepository userRepository;
    private final SsafyCardIssueClient cardIssueClient;
    private final SsafyAccountClient accountClient;

    public CardIssuanceService(UserRepository userRepository,
                               SsafyCardIssueClient cardIssueClient,
                               SsafyAccountClient accountClient) {
        this.userRepository = userRepository;
        this.cardIssueClient = cardIssueClient;
        this.accountClient = accountClient;
    }

    /** 내 카드 조회. 발급된 카드가 없으면 {@code hasCard=false}. */
    public CardResponse getMyCard(Long userId) {
        String userKey = requireUserKey(userId);
        List<SsafyCardIssueClient.Card> cards = cardIssueClient.inquireSignedUp(userKey);
        return cards.isEmpty() ? CardResponse.none() : CardResponse.of(cards.get(0));
    }

    /**
     * 카드 발급. 이미 카드가 있으면 그 카드를 그대로 반환한다(멱등 - 버튼 중복 클릭 방어).
     * 출금계좌가 없으면 개설해 저장한 뒤 발급한다.
     */
    public CardResponse issueCard(Long userId) {
        String userKey = requireUserKey(userId);

        List<SsafyCardIssueClient.Card> existing = cardIssueClient.inquireSignedUp(userKey);
        if (!existing.isEmpty()) {
            return CardResponse.of(existing.get(0));
        }

        String accountNo = ensureWithdrawalAccount(userId, userKey);
        String cardUniqueNo = cardIssueClient.firstCardProduct(userKey);
        SsafyCardIssueClient.Card card = cardIssueClient.create(userKey, cardUniqueNo, accountNo);
        return CardResponse.of(card);
    }

    /** 출금계좌를 확보한다. 이미 있으면 재사용, 없으면 개설 후 저장. */
    private String ensureWithdrawalAccount(Long userId, String userKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSsafyAccountNo() != null && !user.getSsafyAccountNo().isBlank()) {
            return user.getSsafyAccountNo();
        }
        String accountNo = accountClient.createAccount(userKey); // 외부 호출(트랜잭션 밖)
        // JpaRepository.save가 자체 트랜잭션이라 계좌번호 저장만 짧게 커밋된다.
        user.updateProfile(null, null, accountNo);
        userRepository.save(user);
        return accountNo;
    }

    private String requireUserKey(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSsafyUserKey() == null || user.getSsafyUserKey().isBlank()) {
            throw new BusinessException(ErrorCode.SSAFY_MEMBER_NOT_LINKED);
        }
        return user.getSsafyUserKey();
    }
}
