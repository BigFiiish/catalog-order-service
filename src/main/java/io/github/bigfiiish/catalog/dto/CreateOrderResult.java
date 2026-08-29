package io.github.bigfiiish.catalog.dto;

import io.github.bigfiiish.catalog.model.Order;

public record CreateOrderResult(
        Order order,
        boolean created
) {
}