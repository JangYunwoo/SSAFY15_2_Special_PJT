package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

/**
 * 블록체인 노드(JSON-RPC) 접근을 감싼다.
 *
 * <p>web3j 사용을 이 클래스 하나에 가둔다. 검증 로직은
 * {@link CryptoTxVerificationService}가 담당하고, 여기서는 체인에서 읽어온 사실만
 * 돌려준다. 그래서 검증 서비스는 web3j 없이 단위 테스트할 수 있다.
 *
 * <p><b>왜 영수증을 직접 조회하는가.</b> 프론트가 보낸 txHash 는 신뢰할 수 없다.
 * 남의 트랜잭션 해시를 복사해 제출하는 것만으로 결제 완료를 받아낼 수 있기 때문에,
 * 반드시 체인에서 영수증을 가져와 수취 주소·토큰·금액을 대조해야 한다.
 */
@Component
public class ChainClient {

    private static final Logger log = LoggerFactory.getLogger(ChainClient.class);

    /**
     * ERC-20 표준 Transfer 이벤트.
     *
     * <pre>
     *   event Transfer(address indexed from, address indexed to, uint256 value)
     * </pre>
     *
     * <p>indexed 파라미터는 {@code topics} 에, 그 외는 {@code data} 에 담긴다.
     * 따라서 from/to 는 topics[1], topics[2] 에서, 금액은 data 에서 꺼낸다.
     */
    private static final Event TRANSFER_EVENT = new Event("Transfer", List.of(
            new TypeReference<Address>(true) {
            },
            new TypeReference<Address>(true) {
            },
            new TypeReference<Uint256>(false) {
            }));

    /** keccak256("Transfer(address,address,uint256)") — 로그의 topics[0] 과 대조한다. */
    private static final String TRANSFER_TOPIC = EventEncoder.encode(TRANSFER_EVENT);

    private final Web3j web3j;

    public ChainClient(CryptoProperties properties) {
        // 타임아웃을 명시한다. 공용 테스트넷 노드는 간헐적으로 응답이 느려지고,
        // 기본값에 맡기면 폴링 스레드가 그만큼 붙잡힌다.
        long timeoutSeconds = Math.max(1, properties.rpcTimeoutMs() / 1000L);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
        this.web3j = Web3j.build(new HttpService(properties.rpcUrl(), httpClient));
        log.info("체인 클라이언트 초기화 chainId={} rpc={}",
                properties.chainId(), properties.rpcUrl());
    }

    @PreDestroy
    void shutdown() {
        web3j.shutdown();
    }

    /**
     * 트랜잭션 영수증을 조회한다.
     *
     * @return 노드가 아직 트랜잭션을 모르면 {@link Optional#empty()}.
     *         구매자가 송금 직후에 해시를 제출하면 흔히 일어난다. 실패가 아니라
     *         "아직 모른다"이므로 폴링이 재시도해야 한다.
     * @throws BusinessException RPC 자체가 실패한 경우
     */
    public Optional<ReceiptView> findReceipt(String txHash) {
        TransactionReceipt receipt;
        try {
            receipt = web3j.ethGetTransactionReceipt(txHash).send()
                    .getTransactionReceipt()
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            log.warn("영수증 조회 실패 txHash={}", txHash, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "블록체인 노드 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (receipt == null) {
            return Optional.empty();
        }
        // 아직 블록에 포함되지 않은 영수증(pending)은 블록 번호가 없다.
        if (receipt.getBlockNumber() == null) {
            return Optional.empty();
        }
        return Optional.of(new ReceiptView(
                receipt.isStatusOK(),
                receipt.getBlockNumber().longValueExact(),
                extractTransfers(receipt.getLogs())));
    }

    /** 최신 블록 번호. 컨펌 수 계산에 쓴다. */
    public long getLatestBlockNumber() {
        try {
            EthBlockNumber response = web3j.ethBlockNumber().send();
            return response.getBlockNumber().longValueExact();
        } catch (IOException | RuntimeException e) {
            log.warn("최신 블록 조회 실패", e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "블록체인 노드 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 컨펌 수를 계산한다.
     *
     * <p>트랜잭션이 담긴 블록 자체를 1개로 센다. 최신 블록 == 포함 블록이면 컨펌 1.
     * 리오그로 최신 블록이 뒤로 갈 수도 있어 음수 방지가 필요하다.
     */
    public static int calculateConfirmations(long latestBlock, long txBlock) {
        return (int) Math.max(0, latestBlock - txBlock + 1);
    }

    /** 영수증 로그에서 ERC-20 Transfer 만 골라 디코딩한다. */
    private List<TransferLog> extractTransfers(List<Log> logs) {
        List<TransferLog> transfers = new ArrayList<>();
        if (logs == null) {
            return transfers;
        }

        for (Log entry : logs) {
            List<String> topics = entry.getTopics();
            // Transfer 는 indexed 2개 + 시그니처 = topics 3개다. 다른 이벤트는 건너뛴다.
            if (topics == null || topics.size() < 3
                    || !TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0))) {
                continue;
            }
            try {
                String from = decodeAddress(topics.get(1));
                String to = decodeAddress(topics.get(2));
                BigInteger amount = decodeAmount(entry.getData());
                transfers.add(new TransferLog(
                        entry.getAddress().toLowerCase(),
                        from,
                        to,
                        amount,
                        entry.getLogIndex().intValueExact()));
            } catch (RuntimeException e) {
                // 하나가 깨져도 나머지는 살린다. Transfer 시그니처를 흉내낸
                // 비표준 토큰이 섞여 있을 수 있다.
                log.warn("Transfer 로그 디코딩 실패 - 건너뜀 tx={} logIndex={}",
                        entry.getTransactionHash(), entry.getLogIndex(), e);
            }
        }
        return transfers;
    }

    private static String decodeAddress(String topic) {
        Type<?> decoded = FunctionReturnDecoder.decodeIndexedValue(
                topic, new TypeReference<Address>() {
                });
        return String.valueOf(decoded.getValue()).toLowerCase();
    }

    private static BigInteger decodeAmount(String data) {
        List<Type> values = FunctionReturnDecoder.decode(
                data, TRANSFER_EVENT.getNonIndexedParameters());
        if (values.isEmpty()) {
            throw new IllegalStateException("Transfer data 에서 금액을 찾지 못했습니다.");
        }
        return (BigInteger) values.get(0).getValue();
    }

    /**
     * 영수증에서 검증에 필요한 것만 뽑은 결과.
     *
     * @param success     {@code status == 0x1}. false 면 트랜잭션이 revert 됐다.
     * @param blockNumber 포함된 블록. 컨펌 수 계산의 기준.
     * @param transfers   이 트랜잭션이 발생시킨 ERC-20 Transfer 목록
     */
    public record ReceiptView(boolean success, long blockNumber, List<TransferLog> transfers) {

        public ReceiptView {
            transfers = transfers == null ? List.of() : List.copyOf(transfers);
        }
    }

    /**
     * 디코딩한 ERC-20 Transfer 한 건. 주소는 모두 소문자로 정규화되어 있다.
     *
     * @param rawAmount uint256 원본값. 금액 대조는 반드시 이 값으로 한다.
     */
    public record TransferLog(
            String tokenContract,
            String from,
            String to,
            BigInteger rawAmount,
            int logIndex
    ) {
    }

    /** 테스트에서 이벤트 시그니처를 확인할 수 있게 노출한다. */
    static String transferTopic() {
        return TRANSFER_TOPIC;
    }
}
