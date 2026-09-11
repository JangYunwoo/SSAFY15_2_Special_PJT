package com.gongguhan.domain.returnrequest.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 주말을 제외한 영업일 계산기. 공휴일 캘린더 도입 시 이 경계만 교체한다. */
// 빈 이름 명시: groupbuy.service.BusinessDayCalculator와 클래스 단순명이 같아
// 기본 빈 이름('businessDayCalculator')이 충돌하면 컨텍스트가 뜨지 않는다.
// 두 도메인 모두 타입으로 주입하므로 이름만 갈라주면 된다.
@Component("returnBusinessDayCalculator")
public class BusinessDayCalculator {

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
