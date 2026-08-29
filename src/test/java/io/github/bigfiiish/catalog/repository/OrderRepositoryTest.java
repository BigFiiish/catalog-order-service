package io.github.bigfiiish.catalog.repository;

import io.github.bigfiiish.catalog.model.Order;
import io.github.bigfiiish.catalog.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createsAndReadsOrderWithItems() {
        Instant createdAt =
                Instant.parse("2026-08-25T12:00:00Z");

        long orderId = orderRepository.insertOrder(
                "jane@example.com",
                createdAt,
                OrderStatus.CREATED,
                "repository-test-key"
        );

        orderRepository.insertOrderItem(
                orderId,
                1L,
                2,
                new BigDecimal("24.99")
        );

        Order order = orderRepository
                .findByIdempotencyKey("repository-test-key")
                .orElseThrow();

        assertEquals(orderId, order.id());
        assertEquals("jane@example.com", order.customerEmail());
        assertEquals(createdAt, order.createdAt());
        assertEquals(OrderStatus.CREATED, order.status());
        assertEquals(1, order.items().size());
        assertEquals(1L, order.items().getFirst().productId());
        assertEquals(2, order.items().getFirst().quantity());
        assertEquals(
                new BigDecimal("24.99"),
                order.items().getFirst().unitPrice()
        );
    }

    @Test
    void rejectsDuplicateIdempotencyKey() {
        Instant createdAt = Instant.now();

        orderRepository.insertOrder(
                "first@example.com",
                createdAt,
                OrderStatus.CREATED,
                "duplicate-key"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderRepository.insertOrder(
                        "second@example.com",
                        createdAt,
                        OrderStatus.CREATED,
                        "duplicate-key"
                )
        );
    }

    @Test
    void marksCreatedOrderAsShippedOnlyOnce() {
        long orderId = orderRepository.insertOrder(
                "shipping@example.com",
                Instant.now(),
                OrderStatus.CREATED,
                "shipping-repository-key"
        );

        assertTrue(
                orderRepository.markShipped(orderId)
        );

        assertFalse(
                orderRepository.markShipped(orderId)
        );

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow();

        assertEquals(
                OrderStatus.SHIPPED,
                order.status()
        );
    }

}