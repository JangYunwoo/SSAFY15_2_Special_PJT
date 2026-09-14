package com.gongguhan.domain.refund.entity;

/**
 * 환불 수단 ({@code refunds.refund_method}).
 *
 * <p><b>원 결제수단과 항상 같지는 않습니다.</b> 원칙은 원 결제수단으로 되돌리는
 * 것이지만(API 명세 11장), SSAFY 카드 취소는 거래 단위 전액 취소만 되기 때문에
 * 부분 환불이나 청구 확정 이후에는 취소가 불가능합니다. 그때 계좌이체로 전환합니다.
 * 그래서 이 값은 "실제로 어떻게 돌려줬는지" 이고, 원 결제수단은
 * {@code payments.method} 에 따로 남아 있습니다.
 *
 * <p>수단에 따라 채워지는 컬럼이 다릅니다.
 *
 * <table border="1">
 *   <caption>수단별 결과 컬럼</caption>
 *   <tr><th>수단</th><th>채워지는 컬럼</th></tr>
 *   <tr><td>{@link #CARD_CANCEL}</td><td>{@code ssafy_transaction_no}</td></tr>
 *   <tr><td>{@link #ACCOUNT_TRANSFER}</td>
 *       <td>{@code institution_tx_no}, {@code ssafy_transaction_no}, {@code to_account_no}</td></tr>
 *   <tr><td>{@link #KAKAOPAY}</td><td>{@code institution_tx_no} (취소 aid)</td></tr>
 *   <tr><td>{@link #CRYPTO}</td>
 *       <td>{@code to_address}, {@code asset_amount}, {@code tx_hash}</td></tr>
 * </table>
 */
public enum RefundMethod {

    /** 카드 승인 취소. SSAFY 금융망 전액 취소. */
    CARD_CANCEL,

    /** 계좌 이체. 카드 취소가 불가능할 때의 폴백이자 코인 결제의 원화 환불 경로. */
    ACCOUNT_TRANSFER,

    /** 카카오페이 결제 취소. */
    KAKAOPAY,

    /** 온체인 토큰 송금. 확정되면 되돌릴 수 없습니다. */
    CRYPTO
}
