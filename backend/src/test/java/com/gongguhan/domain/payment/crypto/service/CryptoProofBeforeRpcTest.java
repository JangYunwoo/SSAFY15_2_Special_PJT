package com.gongguhan.domain.payment.crypto.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.crypto.dto.CryptoTxSubmitRequest;
import com.gongguhan.domain.payment.crypto.repository.*;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class CryptoProofBeforeRpcTest {
    @Test void invalidProofCannotReachChainOrPersistence() {
        var chain = mock(ChainClient.class);
        var payments = mock(PaymentRepository.class);
        var crypto = mock(CryptoPaymentRepository.class);
        var quotes = mock(CryptoQuoteRepository.class);
        var assets = mock(CryptoAssetRepository.class);
        var wallets = mock(CryptoWalletRepository.class);
        var proofs = mock(CryptoWalletProofService.class);
        var request = new CryptoTxSubmitRequest(1L, "0x" + "a".repeat(64), "0x" + "b".repeat(40), "invalid", "0x00");
        when(proofs.verifyAndRecover(1L, 2L, request)).thenThrow(new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        var service = new CryptoTxVerificationService(payments, crypto, quotes, assets, wallets, chain, proofs, Clock.systemUTC());
        assertThatThrownBy(() -> service.submitTransaction(1L, 2L, request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(chain, payments, crypto, quotes, assets, wallets);
    }
}
