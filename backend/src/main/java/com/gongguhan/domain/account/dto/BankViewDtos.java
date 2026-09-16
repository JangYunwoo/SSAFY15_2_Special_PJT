package com.gongguhan.domain.account.dto;

import com.gongguhan.domain.account.service.SsafyAccountClient;
import com.gongguhan.domain.account.service.SsafyCardIssueClient;
import java.util.List;

/**
 * 가상 은행 조회 콘솔 응답(시연용). SSAFY 교육망에서 조회한 카드 이용내역·계좌 잔액/거래내역을
 * 그대로 노출한다 - 실제 은행 앱이 없으니 "카드에 결제가 잡혔는지, 환불금이 계좌에 들어왔는지"를
 * 확인하는 용도다.
 */
public final class BankViewDtos {

    private BankViewDtos() {
    }

    /** 카드 이용내역. {@code estimatedBalance}는 청구예정액(신용카드라 계좌 즉시 출금 아님). */
    public record CardView(boolean hasCard, String cardName, String cardIssuerName,
                           String estimatedBalance, List<CardTx> transactions) {

        public static CardView none() {
            return new CardView(false, null, null, null, List.of());
        }

        public static CardView of(SsafyCardIssueClient.CardUsage usage) {
            List<CardTx> txs = usage.transactionList() == null ? List.of()
                    : usage.transactionList().stream().map(CardTx::of).toList();
            return new CardView(true, usage.cardName(), usage.cardIssuerName(),
                    usage.estimatedBalance(), txs);
        }
    }

    public record CardTx(String merchantName, String categoryName, String amount,
                         String date, String time) {

        static CardTx of(SsafyCardIssueClient.CardUsageItem i) {
            return new CardTx(i.merchantName(), i.categoryName(), i.transactionBalance(),
                    i.transactionDate(), i.transactionTime());
        }
    }

    /** 계좌 잔액 + 거래내역. 환불 입금이 거래내역에 잡힌다. */
    public record AccountView(boolean hasAccount, String accountNo, String balance,
                              String currency, List<AccountTx> transactions) {

        public static AccountView none() {
            return new AccountView(false, null, null, null, List.of());
        }

        public static AccountView of(String accountNo, SsafyAccountClient.Balance balance,
                                     List<SsafyAccountClient.History> history) {
            List<AccountTx> txs = history.stream().map(AccountTx::of).toList();
            String bal = balance == null ? null : balance.accountBalance();
            String cur = balance == null ? null : balance.currency();
            return new AccountView(true, accountNo, bal, cur, txs);
        }
    }

    public record AccountTx(String type, String amount, String afterBalance,
                            String summary, String date, String time) {

        static AccountTx of(SsafyAccountClient.History h) {
            return new AccountTx(h.transactionTypeName(), h.transactionBalance(),
                    h.transactionAfterBalance(), h.transactionSummary(),
                    h.transactionDate(), h.transactionTime());
        }
    }
}
