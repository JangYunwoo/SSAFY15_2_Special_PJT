package com.gongguhan.domain.product.dto;

import java.math.BigDecimal;

public record AiField<T>(T value, BigDecimal confidence) {
}
