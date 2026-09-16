package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.BankViewDtos.AccountView;
import com.gongguhan.domain.account.dto.BankViewDtos.CardView;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 은행 조회 콘솔(시연용). 로그인한 본인의 SSAFY 카드 이용내역·계좌 잔액/거래내역을
 * 조회한다. 실제 은행 앱이 없어 결제/환불이 진짜 반영됐는지 확인하는 용도다.
 *
 * <p>카드번호·CVC를 우리가 저장하지 않으므로, userKey로 발급 카드를 먼저 조회해 그 카드의
 * 번호·CVC로 이용내역을 조회한다(외부 조회는 트랜잭션 불필요, readOnly).
 */
@Service
@Transactional(readOnly = true)
public class BankViewService {

    // 콘솔은 전체 이력을 보여준다(교육망 데이터라 넓게 잡아도 무해).
    private static final String START_DATE = "20200101";
    private static final String END_DATE = "20301231";

    private final UserRepository userRepository;
    private final SsafyCardIssueClient cardIssueClient;
    private final SsafyAccountClient accountClient;

    public BankViewService(UserRepository userRepository,
                           SsafyCardIssueClient cardIssueClient,
                           SsafyAccountClient accountClient) {
        this.userRepository = userRepository;
        this.cardIssueClient = cardIssueClient;
        this.accountClient = accountClient;
    }

    /** 카드 이용내역 + 청구예정액. 발급 카드가 없으면 hasCard=false. */
    public CardView getCardView(Long userId) {
        String userKey = requireUserKey(userId);
        List<SsafyCardIssueClient.Card> cards = cardIssueClient.inquireSignedUp(userKey);
        if (cards.isEmpty()) {
            return CardView.none();
        }
        SsafyCardIssueClient.Card card = cards.get(0);
        return CardView.of(cardIssueClient.inquireCardUsage(
                userKey, card.cardNo(), card.cvc(), START_DATE, END_DATE));
    }

    /** 계좌 잔액 + 거래내역. 정산계좌가 없으면 hasAccount=false. */
    public AccountView getAccountView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSsafyUserKey() == null || user.getSsafyUserKey().isBlank()) {
            throw new BusinessException(ErrorCode.SSAFY_MEMBER_NOT_LINKED);
        }
        String accountNo = user.getSsafyAccountNo();
        if (accountNo == null || accountNo.isBlank()) {
            return AccountView.none();
        }
        String userKey = user.getSsafyUserKey();
        return AccountView.of(accountNo,
                accountClient.inquireBalance(userKey, accountNo),
                accountClient.inquireHistory(userKey, accountNo, START_DATE, END_DATE));
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
