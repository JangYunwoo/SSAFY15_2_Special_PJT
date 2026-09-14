package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 결제 이력 N+1 제거의 재현 가능한 비교 시나리오.
 *
 * <p>실제 네트워크 상태는 실행할 때마다 달라지므로 DB 왕복 1회에 2ms가 걸린다는
 * 통제된 모델을 사용한다. 두 경로 모두 같은 결제 객체와 DTO 변환을 수행하고,
 * 차이는 슬롯을 건별 조회하는지 일괄 조회하는지뿐이다.
 */
class PaymentHistoryPerformanceTest {

    private static final int[] PAGE_SIZES = {20, 100, 1_000};
    private static final int SAMPLE_COUNT = 5;
    private static final long ROUND_TRIP_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void compareLegacyNPlusOneWithBatchLookup() {
        // Mockito/JIT 초기 비용을 실제 샘플에서 제외한다.
        Scenario warmup = scenario(20);
        legacy(warmup.rows(), warmup.slots());
        optimized(warmup);

        for (int pageSize : PAGE_SIZES) {
            Scenario scenario = scenario(pageSize);
            List<Long> legacySamples = new ArrayList<>();
            List<Long> optimizedSamples = new ArrayList<>();
            for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
                legacySamples.add(measureMicros(() -> legacy(scenario.rows(), scenario.slots())));
                optimizedSamples.add(measureMicros(() -> optimized(scenario)));
            }

            long legacyMedian = percentile(legacySamples, 0.50);
            long optimizedMedian = percentile(optimizedSamples, 0.50);
            long legacyP95 = percentile(legacySamples, 0.95);
            long optimizedP95 = percentile(optimizedSamples, 0.95);
            double improvement = 100.0 * (legacyMedian - optimizedMedian) / legacyMedian;

            scenario.slots().resetCounters();
            assertEquals(pageSize, legacy(scenario.rows(), scenario.slots()).size());
            assertEquals(pageSize, scenario.slots().singleLookupCount());
            assertEquals(0, scenario.slots().batchLookupCount());

            scenario.slots().resetCounters();
            assertEquals(pageSize, optimized(scenario).size());
            assertEquals(0, scenario.slots().singleLookupCount());
            assertEquals(1, scenario.slots().batchLookupCount());
            System.out.printf(
                    "PAYMENT_HISTORY rows=%d samples=%d roundTripMs=2 "
                            + "legacyQueries=%d batchQueries=2 legacyMedianMs=%.3f "
                            + "batchMedianMs=%.3f legacyP95Ms=%.3f batchP95Ms=%.3f improvementPct=%.2f%n",
                    pageSize, SAMPLE_COUNT, pageSize + 1,
                    legacyMedian / 1_000.0, optimizedMedian / 1_000.0,
                    legacyP95 / 1_000.0, optimizedP95 / 1_000.0, improvement);
        }
    }

    private List<PaymentResponse> legacy(List<Payment> rows, DelayedSlotPort slots) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        return rows.stream()
                .map(payment -> PaymentResponse.from(
                        payment, slots.get(payment.getGroupBuyId()), now))
                .toList();
    }

    private List<PaymentResponse> optimized(Scenario scenario) {
        return scenario.service().listHistory(1L, scenario.pageable()).getContent();
    }

    private Scenario scenario(int pageSize) {
        List<Payment> rows = payments(pageSize);
        PageRequest pageable = PageRequest.of(0, pageSize);
        PaymentRepository repository = mock(PaymentRepository.class);
        when(repository.findByUserId(1L, pageable))
                .thenReturn(new PageImpl<>(rows, pageable, pageSize));
        DelayedSlotPort slots = new DelayedSlotPort(rows);
        PaymentService service = new PaymentService(
                mock(GroupBuyRepository.class), repository, mock(UserAddressRepository.class),
                mock(GroupBuySlotGate.class), slots, mock(ParticipationWriterPort.class),
                mock(EscrowWriterPort.class), mock(PaymentSlotReservationTransaction.class), CLOCK);
        return new Scenario(rows, pageable, slots, service);
    }

    private List<Payment> payments(int count) {
        List<Payment> rows = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            Payment payment = mock(Payment.class);
            when(payment.getPaymentId()).thenReturn(id);
            when(payment.getGroupBuyId()).thenReturn(id);
            when(payment.getStatus()).thenReturn(Payment.STATUS_PROCESSING);
            when(payment.getMethod()).thenReturn("CARD");
            when(payment.getKrwAmount()).thenReturn(53_000L);
            when(payment.getSlotExpiresAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 1, 0));
            when(payment.isProcessing()).thenReturn(true);
            rows.add(payment);
        }
        return rows;
    }

    private long measureMicros(Runnable operation) {
        long startedAt = System.nanoTime();
        operation.run();
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt);
    }

    private long percentile(List<Long> samples, double percentile) {
        List<Long> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private record Scenario(
            List<Payment> rows,
            PageRequest pageable,
            DelayedSlotPort slots,
            PaymentService service
    ) {
    }

    private static final class DelayedSlotPort implements GroupBuySlotPort {

        private final Map<Long, GroupBuySlot> slots;
        private final AtomicInteger singleLookups = new AtomicInteger();
        private final AtomicInteger batchLookups = new AtomicInteger();

        private DelayedSlotPort(List<Payment> payments) {
            slots = new HashMap<>();
            payments.forEach(payment -> slots.put(
                    payment.getGroupBuyId(), slot(payment.getGroupBuyId())));
        }

        @Override
        public GroupBuySlot get(Long groupBuyId) {
            singleLookups.incrementAndGet();
            delayOneRoundTrip();
            return slots.get(groupBuyId);
        }

        @Override
        public Map<Long, GroupBuySlot> getAll(Collection<Long> groupBuyIds) {
            batchLookups.incrementAndGet();
            delayOneRoundTrip();
            Map<Long, GroupBuySlot> result = new HashMap<>();
            groupBuyIds.forEach(id -> result.put(id, slots.get(id)));
            return result;
        }

        @Override
        public GroupBuySlot reserve(Long groupBuyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupBuySlot release(Long groupBuyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupBuySlot confirm(Long groupBuyId) {
            throw new UnsupportedOperationException();
        }

        private static GroupBuySlot slot(Long groupBuyId) {
            return new GroupBuySlot(
                    groupBuyId, 50_000L, 3_000L, 100, 0, 0,
                    LocalDateTime.of(2026, 1, 2, 0, 0), "RECRUITING");
        }

        private void delayOneRoundTrip() {
            LockSupport.parkNanos(ROUND_TRIP_NANOS);
        }

        private void resetCounters() {
            singleLookups.set(0);
            batchLookups.set(0);
        }

        private int singleLookupCount() {
            return singleLookups.get();
        }

        private int batchLookupCount() {
            return batchLookups.get();
        }
    }
}
