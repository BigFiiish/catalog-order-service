package io.github.bigfiiish.catalog.service;

import io.github.bigfiiish.catalog.dto.CreateOrderRequest;
import io.github.bigfiiish.catalog.dto.CreateOrderResult;
import io.github.bigfiiish.catalog.dto.OrderItemRequest;
import io.github.bigfiiish.catalog.exception.ApiException;
import io.github.bigfiiish.catalog.model.OrderStatus;
import io.github.bigfiiish.catalog.model.Product;
import io.github.bigfiiish.catalog.repository.OrderRepository;
import io.github.bigfiiish.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:orderservicedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createsOrderAndDeductsStock() {
        CreateOrderResult result = orderService.createOrder(
                "create-order-key",
                request(
                        "jane@example.com",
                        new OrderItemRequest(1L, 2)
                )
        );

        Product product =
                productRepository.findById(1L).orElseThrow();

        assertTrue(result.created());
        assertEquals(OrderStatus.CREATED, result.order().status());
        assertEquals("jane@example.com",
                result.order().customerEmail());
        assertEquals(1, result.order().items().size());
        assertEquals(2,
                result.order().items().getFirst().quantity());
        assertEquals(48, product.stockQuantity());
    }

    @Test
    void returnsOriginalOrderForRepeatedIdempotencyKey() {
        CreateOrderRequest request = request(
                "retry@example.com",
                new OrderItemRequest(2L, 1)
        );

        CreateOrderResult first = orderService.createOrder(
                "replay-key",
                request
        );

        int stockAfterFirstRequest = productRepository
                .findById(2L)
                .orElseThrow()
                .stockQuantity();

        CreateOrderResult second = orderService.createOrder(
                "replay-key",
                request
        );

        int stockAfterSecondRequest = productRepository
                .findById(2L)
                .orElseThrow()
                .stockQuantity();

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(
                first.order().id(),
                second.order().id()
        );
        assertEquals(
                stockAfterFirstRequest,
                stockAfterSecondRequest
        );
    }

    @Test
    void rejectsMissingProduct() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> orderService.createOrder(
                        "missing-product-key",
                        request(
                                "missing@example.com",
                                new OrderItemRequest(999L, 1)
                        )
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatus()
        );
    }

    @Test
    void rejectsInsufficientStock() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> orderService.createOrder(
                        "out-of-stock-key",
                        request(
                                "stock@example.com",
                                new OrderItemRequest(3L, 1)
                        )
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatus()
        );

        assertTrue(
                orderRepository
                        .findByIdempotencyKey("out-of-stock-key")
                        .isEmpty()
        );
    }

    @Test
    void rollsBackEarlierStockDeductionWhenLaterItemFails() {
        int stockBefore = productRepository
                .findById(1L)
                .orElseThrow()
                .stockQuantity();

        assertThrows(
                ApiException.class,
                () -> orderService.createOrder(
                        "rollback-key",
                        new CreateOrderRequest(
                                "rollback@example.com",
                                List.of(
                                        new OrderItemRequest(1L, 1),
                                        new OrderItemRequest(3L, 1)
                                )
                        )
                )
        );

        int stockAfter = productRepository
                .findById(1L)
                .orElseThrow()
                .stockQuantity();

        assertEquals(stockBefore, stockAfter);

        assertTrue(
                orderRepository
                        .findByIdempotencyKey("rollback-key")
                        .isEmpty()
        );
    }

    @Test
    void combinesDuplicateProductEntries() {
        CreateOrderResult result = orderService.createOrder(
                "duplicate-product-key",
                new CreateOrderRequest(
                        "duplicate@example.com",
                        List.of(
                                new OrderItemRequest(5L, 2),
                                new OrderItemRequest(5L, 3)
                        )
                )
        );

        Product product =
                productRepository.findById(5L).orElseThrow();

        assertEquals(1, result.order().items().size());
        assertEquals(
                5,
                result.order().items().getFirst().quantity()
        );
        assertEquals(195, product.stockQuantity());
    }

    private CreateOrderRequest request(
            String email,
            OrderItemRequest item
    ) {
        return new CreateOrderRequest(
                email,
                List.of(item)
        );
    }
}