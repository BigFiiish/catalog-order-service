package io.github.bigfiiish.catalog.model;

import java.math.BigDecimal;

public record Product(
        long id,
        String name,
        BigDecimal price,
        int stockQuantity,
        String category
) {
}