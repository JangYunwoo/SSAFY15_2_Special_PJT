package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusinessDayCalculatorTest {

    private final BusinessDayCalculator calculator = new BusinessDayCalculator();

    @Test
    void threeBusinessDaysFromFridayEndsOnWednesday() {
        LocalDateTime friday = LocalDateTime.of(2026, 9, 11, 15, 30);

        LocalDateTime result = calculator.addBusinessDays(friday, 3);

        assertEquals(LocalDateTime.of(2026, 9, 16, 15, 30), result);
    }

    @Test
    void zeroBusinessDaysKeepsOriginalTime() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 12, 15, 30);

        assertEquals(start, calculator.addBusinessDays(start, 0));
    }

    @Test
    void negativeBusinessDaysAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.addBusinessDays(LocalDateTime.now(), -1));
    }
}
