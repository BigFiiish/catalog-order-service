package io.github.bigfiiish.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record ShipOrderRequest(
        @NotBlank(message = "webhookUrl must not be blank")
        String webhookUrl
) {
}