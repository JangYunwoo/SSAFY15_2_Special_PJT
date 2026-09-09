package com.gongguhan.domain.groupbuy.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class BusinessDayCalculator {

    public LocalDateTime addBusinessDays(LocalDateTime start, int businessDays) {
        if (businessDays < 0) {
            throw new IllegalArgumentException("영업일 수는 0 이상이어야 합니다.");
        }

        LocalDateTime result = start;
        int added = 0;
        while (added < businessDays) {
            result = result.plusDays(1);
            if (isBusinessDay(result.getDayOfWeek())) {
                added++;
            }
        }
        return result;
    }

    private boolean isBusinessDay(DayOfWeek dayOfWeek) {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
