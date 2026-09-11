package com.gongguhan.domain.returnrequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusinessDayCalculatorTest {

    private final BusinessDayCalculator calculator = new BusinessDayCalculator();

    @Test
    void skipsWeekendWhenAddingThreeBusinessDays() {
        LocalDateTime friday = LocalDateTime.of(2026, 9, 11, 14, 0);

        assertThat(calculator.add(friday, 3))
                .isEqualTo(LocalDateTime.of(2026, 9, 16, 14, 0));
    }
}
