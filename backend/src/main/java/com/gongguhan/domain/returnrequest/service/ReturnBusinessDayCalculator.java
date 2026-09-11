package com.gongguhan.domain.returnrequest.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 주말을 제외한 반품 영업일 계산기. 공휴일 캘린더 도입 시 이 경계만 교체한다. */
@Component
public class ReturnBusinessDayCalculator {

    public LocalDateTime add(LocalDateTime start, int businessDays) {
        if (businessDays < 0) {
            throw new IllegalArgumentException("영업일은 음수일 수 없습니다.");
        }
        LocalDateTime result = start;
        int added = 0;
        while (added < businessDays) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SATURDAY
                    && result.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }
}
