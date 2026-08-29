package io.github.bigfiiish.catalog.controller;

import io.github.bigfiiish.catalog.dto.CreateOrderRequest;
import io.github.bigfiiish.catalog.dto.CreateOrderResult;
import io.github.bigfiiish.catalog.model.Order;
import io.github.bigfiiish.catalog.service.OrderService;
import io.github.bigfiiish.catalog.dto.ShipOrderRequest;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestHeader(
                    name = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            CreateOrderRequest request
    ) {
        CreateOrderResult result =
                orderService.createOrder(
                        idempotencyKey,
                        request
                );

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(result.order());
    }

    @PostMapping("/{id}/ship")
    public Order shipOrder(
            @PathVariable long id,

            @Valid
            @RequestBody
            ShipOrderRequest request
    ) {
        return orderService.shipOrder(
                id,
                request.webhookUrl()
        );
    }

}