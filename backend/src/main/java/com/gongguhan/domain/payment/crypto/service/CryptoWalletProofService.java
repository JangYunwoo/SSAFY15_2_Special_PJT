package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.crypto.dto.CryptoTxSubmitRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoWalletChallengeRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoWalletChallengeResponse;
import com.gongguhan.domain.payment.crypto.entity.*;
import com.gongguhan.domain.payment.crypto.repository.*;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/** 지갑 증명은 송금 승인이 아니다. 공개 거래를 특정 사용자 결제에 귀속시키는 별도 증명이다. */
@Service
public class CryptoWalletProofService {
    private final CryptoWalletChallengeRepository challenges;
    private final PaymentRepository payments;
    private final CryptoQuoteRepository quotes;
    private final CryptoAssetRepository assets;
    private final CryptoPaymentRepository cryptoPayments;
    private final CryptoTxRecordService records;
    private final Clock clock;
    private final String domain;

    public CryptoWalletProofService(CryptoWalletChallengeRepository challenges, PaymentRepository payments,
            CryptoQuoteRepository quotes, CryptoAssetRepository assets, CryptoPaymentRepository cryptoPayments,
            CryptoTxRecordService records, Clock clock,
            @Value("${app.crypto.wallet-proof-domain:j15b205.p.ssafy.io}") String domain) {
        this.challenges = challenges; this.payments = payments; this.quotes = quotes; this.assets = assets;
        this.cryptoPayments = cryptoPayments; this.records = records; this.clock = clock; this.domain = domain;
    }

    @Transactional
    public CryptoWalletChallengeResponse issue(Long userId, Long paymentId, CryptoWalletChallengeRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = ownedPayment(userId, paymentId);
        if (!"CRYPTO".equals(payment.getMethod())) throw invalid("코인 결제에만 지갑 증명을 발급합니다.");
        if (!payment.isProcessing()) throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        if (payment.isExpired(now)) throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        CryptoQuote quote = quotes.findById(request.quoteId()).orElseThrow(() -> invalid("견적을 찾을 수 없습니다."));
        if (quote.isUsed() || quote.isExpired(now)) throw new BusinessException(ErrorCode.QUOTE_EXPIRED);
        if (quote.getKrwAmount() != payment.getKrwAmount()) throw invalid("견적 금액이 일치하지 않습니다.");
        CryptoAsset asset = assets.findByAssetIdAndEnabledTrue(quote.getAssetId())
                .orElseThrow(() -> invalid("지원하지 않는 자산입니다."));
        LocalDateTime expires = now.plusMinutes(5);
        if (payment.getSlotExpiresAt().isBefore(expires)) expires = payment.getSlotExpiresAt();
        if (quote.getExpiresAt().isBefore(expires)) expires = quote.getExpiresAt();
        String nonce = UUID.randomUUID().toString();
        String address = request.fromAddress().toLowerCase(Locale.ROOT);
        String hash = request.txHash().toLowerCase(Locale.ROOT);
        String message = """
                Gongguhan wallet ownership proof v1
                Domain: %s
                Purpose: Bind an existing token transfer to my Gongguhan payment.
                This signature does not send funds or grant token approval.
                User ID: %d
                Payment ID: %d
                Quote ID: %d
                Chain ID: %d
                Wallet: %s
                Transaction: %s
                Nonce: %s
                Expires at: %s
                """.formatted(domain, userId, paymentId, quote.getQuoteId(), asset.getChainId(), address, hash,
                nonce, expires.atOffset(ZoneOffset.UTC));
        challenges.save(new CryptoWalletChallenge(nonce, userId, paymentId, quote.getQuoteId(), asset.getChainId(),
                address, hash, message, expires));
        return new CryptoWalletChallengeResponse(nonce, message, expires.atOffset(ZoneOffset.UTC).toString(), asset.getChainId());
    }

    /** RPC 전에 증명부터 검사한다. 소비된 동일 요청은 외부 호출 없이 기존 결과만 복구한다. */
    @Transactional(readOnly = true)
    public Optional<CryptoPayment> verifyAndRecover(Long userId, Long paymentId, CryptoTxSubmitRequest request) {
        ownedPayment(userId, paymentId);
        CryptoWalletChallenge proof = challenges.findById(request.challengeId()).orElseThrow(CryptoWalletProofService::denied);
        validate(proof, userId, paymentId, request);
        return proof.isConsumed() ? Optional.of(recover(proof)) : Optional.empty();
    }

    /** nonce 소비와 거래 기록이 같은 트랜잭션이다. 기록 실패 시 nonce도 롤백해 같은 요청을 재시도한다. */
    @Transactional
    public CryptoPayment recordVerified(Long userId, Long paymentId, CryptoTxSubmitRequest request,
            CryptoQuote quote, CryptoAsset asset, CryptoWallet wallet, ChainClient.ReceiptView receipt, long latestBlock) {
        CryptoWalletChallenge proof = challenges.findForUpdate(request.challengeId()).orElseThrow(CryptoWalletProofService::denied);
        validate(proof, userId, paymentId, request);
        if (proof.isConsumed()) return recover(proof);
        if (proof.getChainId() != asset.getChainId() || !proof.getQuoteId().equals(quote.getQuoteId())) throw denied();
        // 결제 종료와 동시 등록도 같은 행 잠금으로 직렬화한다.
        Payment payment = payments.findForUpdateByPaymentIdAndUserId(paymentId, userId).orElseThrow(CryptoWalletProofService::denied);
        if (!payment.isProcessing()) throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        if (payment.isExpired(LocalDateTime.now(clock))) throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        if (cryptoPayments.existsById(paymentId)) throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        proof.consume(LocalDateTime.now(clock));
        return records.record(paymentId, quote, asset, wallet, proof.getFromAddress(), proof.getTxHash(), receipt, latestBlock);
    }

    private Payment ownedPayment(Long userId, Long paymentId) {
        return payments.findByPaymentIdAndUserId(paymentId, userId).orElseThrow(CryptoWalletProofService::denied);
    }

    private void validate(CryptoWalletChallenge proof, Long userId, Long paymentId, CryptoTxSubmitRequest request) {
        if (!proof.getUserId().equals(userId) || !proof.getPaymentId().equals(paymentId)
                || !proof.getQuoteId().equals(request.quoteId())
                || !proof.getFromAddress().equalsIgnoreCase(request.fromAddress())
                || !proof.getTxHash().equalsIgnoreCase(request.txHash())) throw denied();
        if (!proof.isConsumed() && !LocalDateTime.now(clock).isBefore(proof.getExpiresAt())) {
            throw invalid("지갑 소유권 확인이 만료되었습니다. 다시 확인해 주세요.");
        }
        try {
            if (request.signature() == null || !request.signature().matches("^0x[0-9a-fA-F]{130}$")) throw denied();
            byte[] bytes = Numeric.hexStringToByteArray(request.signature());
            int v = Byte.toUnsignedInt(bytes[64]);
            if (v != 27 && v != 28) throw denied();
            var signature = new Sign.SignatureData(bytes[64], Arrays.copyOfRange(bytes, 0, 32), Arrays.copyOfRange(bytes, 32, 64));
            String recovered = "0x" + Keys.getAddress(Sign.signedPrefixedMessageToKey(
                    proof.getMessage().getBytes(StandardCharsets.UTF_8), signature));
            if (!proof.getFromAddress().equalsIgnoreCase(recovered)) throw denied();
        } catch (SignatureException | IllegalArgumentException e) {
            throw denied();
        }
    }

    private CryptoPayment recover(CryptoWalletChallenge proof) {
        CryptoPayment existing = cryptoPayments.findById(proof.getPaymentId()).orElseThrow(CryptoWalletProofService::denied);
        if (!existing.getQuoteId().equals(proof.getQuoteId()) || existing.getChainId() != proof.getChainId()
                || !existing.getFromAddress().equalsIgnoreCase(proof.getFromAddress())
                || !existing.getTxHash().equalsIgnoreCase(proof.getTxHash())) throw denied();
        return existing;
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED, "지갑 소유권 증명이 유효하지 않습니다."); }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
}
