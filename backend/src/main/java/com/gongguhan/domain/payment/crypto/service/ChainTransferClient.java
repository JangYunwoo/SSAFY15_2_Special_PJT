package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

/**
 * 환불 지갑에서 ERC-20 토큰을 송금한다 (S15P21B205-80, FR-RFD-03).
 *
 * <p>{@link ChainClient} 는 <b>읽기 전용</b>이다 — 영수증·블록·로그 조회만 한다.
 * 이 클래스는 반대로 <b>우리가 트랜잭션을 만들어 서명하고 보낸다.</b> 둘을 나눈 이유는
 * 개인키를 다루는 범위를 최소로 가두기 위해서다. 결제 검증 경로는 개인키를 몰라도 되고,
 * 알 이유도 없다.
 *
 * <h2>개인키</h2>
 *
 * <p>환경변수 {@code CHAIN_REFUND_WALLET_PRIVATE_KEY} 에서만 읽는다. <b>DB 에 절대
 * 넣지 않는다</b> — DB 가 유출돼도 자금이 빠져나가지 않게 하는 분리다.
 *
 * <p>키가 없어도 애플리케이션은 뜬다. 코인 환불만 못 할 뿐 나머지 기능은 정상이어야
 * 하기 때문이다. 송금을 시도하는 시점에 명확한 사유로 실패한다.
 *
 * <h2>논스 충돌</h2>
 *
 * <p>같은 지갑에서 동시에 두 건을 보내면 논스가 겹쳐 하나가 버려진다. 송금 메서드를
 * {@code synchronized} 로 직렬화하고, 논스는 매번 {@code pending} 기준으로 다시
 * 조회한다. 환불은 초당 수십 건이 나갈 일이 없어 이 정도로 충분하다.
 *
 * <p><b>인스턴스를 2대 이상으로 늘리면 이 보호가 깨진다.</b> 프로세스마다 별도로
 * 직렬화되므로 서로의 논스를 모른다. 확장 전에 송금을 한 인스턴스로 몰거나 분산 락이
 * 필요하다.
 */
@Component
public class ChainTransferClient {

    private static final Logger log = LoggerFactory.getLogger(ChainTransferClient.class);

    /** ERC-20 {@code transfer(address,uint256)}. */
    private static final String TRANSFER_FUNCTION = "transfer";

    /**
     * 가스 한도.
     *
     * <p>ERC-20 전송은 보통 5~6만 가스면 끝나지만, 수신 주소가 토큰을 처음 받는 경우
     * 저장 슬롯이 새로 생겨 더 든다. 넉넉히 잡는다 — 남은 가스는 환불되므로 크게 잡아
     * 손해 볼 것이 없고, 모자라면 트랜잭션이 revert 되어 가스만 날린다.
     */
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(120_000L);

    /** 영수증 폴링 간격·횟수. Amoy 블록이 약 2초라 40회면 80초 정도 기다린다. */
    private static final long RECEIPT_POLL_INTERVAL_MS = 2_000L;
    private static final int RECEIPT_POLL_ATTEMPTS = 40;

    private final Web3j web3j;
    private final Credentials credentials;
    private final long chainId;

    public ChainTransferClient(CryptoProperties properties,
                               @Value("${CHAIN_REFUND_WALLET_PRIVATE_KEY:}") String privateKey) {
        int timeoutMs = properties.rpcTimeoutMs();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.web3j = Web3j.build(new HttpService(properties.rpcUrl(), httpClient));
        this.chainId = properties.chainId();
        this.credentials = privateKey == null || privateKey.isBlank()
                ? null
                : Credentials.create(privateKey.trim());

        if (this.credentials == null) {
            log.warn("환불 지갑 개인키가 설정되지 않았습니다. 코인 환불 송금이 비활성 상태입니다. "
                    + "CHAIN_REFUND_WALLET_PRIVATE_KEY 환경변수를 확인하세요.");
        } else {
            log.info("환불 지갑 준비됨 address={}", this.credentials.getAddress());
        }
    }

    /** 송금할 수 있는 상태인지. 개인키가 없으면 false. */
    public boolean isEnabled() {
        return credentials != null;
    }

    /** 환불 지갑 주소. 잔액 확인·운영 점검에 쓴다. */
    public String walletAddress() {
        requireEnabled();
        return credentials.getAddress();
    }

    /**
     * ERC-20 토큰을 송금하고 영수증까지 기다린다.
     *
     * <p><b>영수증을 기다리는 이유.</b> 브로드캐스트만 하고 성공으로 기록하면, 나중에
     * revert 된 트랜잭션을 "환불 완료" 로 남기게 된다. 구매자는 돈을 못 받았는데 장부는
     * 지급으로 끝나 있는 상태가 가장 나쁘다. Amoy 블록이 약 2초라 몇 초만 기다리면 된다.
     *
     * @param tokenContract 토큰 컨트랙트 주소
     * @param to            받는 지갑. 구매자가 결제 때 송금한 주소다
     * @param rawAmount     uint256 원본값. 사람이 읽는 수량이 아니다
     * @return 확정된 트랜잭션 해시
     * @throws BusinessException {@code EXTERNAL_SERVICE_UNAVAILABLE} — 전송 실패 또는
     *         영수증 대기 시간 초과. <b>후자는 송금이 일어났는지 알 수 없는 상태다</b>
     * @throws BusinessException {@code INTERNAL_SERVER_ERROR} — 개인키 미설정, revert
     */
    public synchronized String transferToken(String tokenContract, String to,
                                             BigInteger rawAmount) {
        requireEnabled();

        String encoded = FunctionEncoder.encode(new Function(
                TRANSFER_FUNCTION,
                List.of(new Address(to), new Uint256(rawAmount)),
                List.<TypeReference<?>>of()));

        try {
            BigInteger gasPrice = currentGasPrice();
            // pending 기준으로 매번 다시 읽는다. 직전 송금이 아직 블록에 안 들어갔어도
            // 다음 논스를 받아 연속 송금이 막히지 않는다.
            EthGetTransactionCount nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.PENDING).send();
            if (nonce.hasError()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "논스를 조회하지 못했습니다: " + nonce.getError().getMessage());
            }

            PollingTransactionReceiptProcessor receiptProcessor =
                    new PollingTransactionReceiptProcessor(
                            web3j, RECEIPT_POLL_INTERVAL_MS, RECEIPT_POLL_ATTEMPTS);
            RawTransactionManager manager =
                    new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

            EthSendTransaction sent = manager.sendTransaction(
                    gasPrice, GAS_LIMIT, tokenContract, encoded, BigInteger.ZERO);
            if (sent.hasError()) {
                // 노드가 거부했다. 트랜잭션이 체인에 올라가지 않았으므로 재시도해도 안전하다.
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "송금 요청이 거부되었습니다: " + sent.getError().getMessage());
            }

            String txHash = sent.getTransactionHash();
            log.info("코인 환불 송금 전송 txHash={} to={} rawAmount={}", txHash, to, rawAmount);

            TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(txHash);
            if (!receipt.isStatusOK()) {
                // 체인에 올라갔지만 실패했다. 토큰은 나가지 않았고 가스만 소모됐다.
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "송금 트랜잭션이 실패했습니다(revert). txHash=" + txHash);
            }

            log.info("코인 환불 송금 확정 txHash={} block={}", txHash, receipt.getBlockNumber());
            return txHash;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 여기에는 영수증 대기 시간 초과가 포함된다. 트랜잭션이 이미 나갔을 수 있으므로
            // 호출 측은 자동 재시도하지 말고 사람이 확인하게 해야 한다.
            log.error("코인 환불 송금 실패 to={} rawAmount={}", to, rawAmount, exception);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "송금 결과를 확인하지 못했습니다: " + exception.getMessage());
        }
    }

    /**
     * 현재 가스 가격.
     *
     * <p>고정값을 쓰면 네트워크가 혼잡할 때 트랜잭션이 영영 채굴되지 않는다. 매번
     * 노드에 물어본다.
     */
    private BigInteger currentGasPrice() throws Exception {
        EthGasPrice gasPrice = web3j.ethGasPrice().send();
        if (gasPrice.hasError()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "가스 가격을 조회하지 못했습니다: " + gasPrice.getError().getMessage());
        }
        return gasPrice.getGasPrice();
    }

    private void requireEnabled() {
        if (credentials == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 지갑 개인키가 설정되지 않아 코인 환불을 보낼 수 없습니다. "
                            + "CHAIN_REFUND_WALLET_PRIVATE_KEY 환경변수를 확인하세요.");
        }
    }

    @PreDestroy
    public void shutdown() {
        web3j.shutdown();
    }
}
