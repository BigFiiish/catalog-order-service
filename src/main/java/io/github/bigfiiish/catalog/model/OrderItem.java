package io.github.bigfiiish.catalog.model;

import java.math.BigDecimal;

public record OrderItem(
        long id,
        long productId,
        int quantity,
        BigDecimal unitPrice
) {
}