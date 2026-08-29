package io.github.bigfiiish.catalog.dto;

import io.github.bigfiiish.catalog.model.OrderStatus;

public record WebhookPayload(
        long orderId,
        OrderStatus status
) {
}