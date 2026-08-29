package io.github.bigfiiish.catalog.model;

import java.time.Instant;
import java.util.List;

public record Order(
        long id,
        String customerEmail,
        Instant createdAt,
        OrderStatus status,
        List<OrderItem> items
) {
}