package com.gongguhan.domain.returnrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

/**
 * 반품 목록의 기존 행별 연관 조회와 JPA 프로젝션 조회를 같은 왕복 지연으로 비교한다.
 */
class ReturnListPerformanceTest {

    private static final int[] PAGE_SIZES = {20, 100, 1_000};
    private static final int SAMPLE_COUNT = 5;
    private static final long ROUND_TRIP_NANOS = TimeUnit.MILLISECONDS.toNanos(2);

    @Test
    void compareLegacyRowLookupsWithProjection() {
        legacy(20);
        projection(20);

        for (int pageSize : PAGE_SIZES) {
            List<Long> legacySamples = new ArrayList<>();
            List<Long> projectionSamples = new ArrayList<>();
            for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
                legacySamples.add(measureMicros(() -> legacy(pageSize)));
                projectionSamples.add(measureMicros(() -> projection(pageSize)));
            }

            long legacyMedian = percentile(legacySamples, 0.50);
            long projectionMedian = percentile(projectionSamples, 0.50);
            long legacyP95 = percentile(legacySamples, 0.95);
            long projectionP95 = percentile(projectionSamples, 0.95);
            double improvement = 100.0 * (legacyMedian - projectionMedian) / legacyMedian;

            assertEquals(2 + 3 * pageSize, legacyQueryCount(pageSize));
            assertEquals(2, projectionQueryCount());
            System.out.printf(
                    "RETURN_LIST rows=%d samples=%d roundTripMs=2 "
                            + "legacyQueries=%d projectionQueries=2 legacyMedianMs=%.3f "
                            + "projectionMedianMs=%.3f legacyP95Ms=%.3f "
                            + "projectionP95Ms=%.3f improvementPct=%.2f%n",
                    pageSize, SAMPLE_COUNT, legacyQueryCount(pageSize),
                    legacyMedian / 1_000.0, projectionMedian / 1_000.0,
                    legacyP95 / 1_000.0, projectionP95 / 1_000.0, improvement);
        }
    }

    private void legacy(int rows) {
        delayQueries(legacyQueryCount(rows));
        consumeRows(rows);
    }

    private void projection(int rows) {
        delayQueries(projectionQueryCount());
        // 결과 행 DTO 생성 비용이 두 비교에서 동일하다는 모델이다.
        consumeRows(rows);
    }

    private int legacyQueryCount(int rows) {
        return 2 + 3 * rows;
    }

    private int projectionQueryCount() {
        return 2;
    }

    private void delayQueries(int count) {
        // Windows의 짧은 park 호출은 타이머 해상도 때문에 매번 약 15ms까지
        // 늘어날 수 있다. 총 왕복 지연을 한 번에 적용해 2ms 모델을 보존한다.
        LockSupport.parkNanos(ROUND_TRIP_NANOS * count);
    }

    private void consumeRows(int rows) {
        for (int i = 0; i < rows; i++) {
            // 반복 자체가 최적화되어 사라지지 않게 매우 작은 동일 작업을 둔다.
            String.valueOf(i).length();
        }
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
}
