package io.github.bigfiiish.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerEmail must not be blank")
        String customerEmail,

        @NotEmpty(message = "items must not be empty")
        List<@Valid OrderItemRequest> items
) {
}