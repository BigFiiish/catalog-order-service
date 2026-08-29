package io.github.bigfiiish.catalog.service;

import io.github.bigfiiish.catalog.dto.CreateOrderRequest;
import io.github.bigfiiish.catalog.dto.CreateOrderResult;
import io.github.bigfiiish.catalog.dto.OrderItemRequest;
import io.github.bigfiiish.catalog.exception.ApiException;
import io.github.bigfiiish.catalog.model.Order;
import io.github.bigfiiish.catalog.model.OrderStatus;
import io.github.bigfiiish.catalog.model.Product;
import io.github.bigfiiish.catalog.repository.OrderRepository;
import io.github.bigfiiish.catalog.repository.ProductRepository;
import io.github.bigfiiish.catalog.dto.WebhookPayload;
import io.github.bigfiiish.catalog.webhook.WebhookDeliveryService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.net.URI;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;
    private final WebhookDeliveryService webhookDeliveryService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            PlatformTransactionManager transactionManager,
            WebhookDeliveryService webhookDeliveryService
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.webhookDeliveryService =
                webhookDeliveryService;
    }

    public CreateOrderResult createOrder(
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        String normalizedKey =
                validateIdempotencyKey(idempotencyKey);

        Order existingOrder = orderRepository
                .findByIdempotencyKey(normalizedKey)
                .orElse(null);

        if (existingOrder != null) {
            return new CreateOrderResult(
                    existingOrder,
                    false
            );
        }

        List<PreparedOrderItem> preparedItems =
                prepareItems(request.items());

        try {
            Order createdOrder = transactionTemplate.execute(
                    status -> createOrderInTransaction(
                            normalizedKey,
                            request.customerEmail().strip(),
                            preparedItems
                    )
            );

            if (createdOrder == null) {
                throw new IllegalStateException(
                        "Order transaction returned no result"
                );
            }

            return new CreateOrderResult(
                    createdOrder,
                    true
            );
        } catch (DataIntegrityViolationException exception) {
            Order concurrentOrder = orderRepository
                    .findByIdempotencyKey(normalizedKey)
                    .orElseThrow(() -> exception);

            return new CreateOrderResult(
                    concurrentOrder,
                    false
            );
        }
    }

    public Order shipOrder(
            long orderId,
            String webhookUrl
    ) {
        String normalizedWebhookUrl =
                validateWebhookUrl(webhookUrl);

        Order shippedOrder = transactionTemplate.execute(
                status -> shipOrderInTransaction(orderId)
        );

        if (shippedOrder == null) {
            throw new IllegalStateException(
                    "Ship transaction returned no result"
            );
        }

        webhookDeliveryService.deliver(
                normalizedWebhookUrl,
                new WebhookPayload(
                        shippedOrder.id(),
                        shippedOrder.status()
                )
        );

        return shippedOrder;
    }

    private Order createOrderInTransaction(
            String idempotencyKey,
            String customerEmail,
            List<PreparedOrderItem> items
    ) {
        long orderId = orderRepository.insertOrder(
                customerEmail,
                Instant.now(),
                OrderStatus.CREATED,
                idempotencyKey
        );

        for (PreparedOrderItem item : items) {
            boolean deducted = productRepository.deductStock(
                    item.productId(),
                    item.quantity()
            );

            if (!deducted) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for product "
                                + item.productId()
                );
            }

            orderRepository.insertOrderItem(
                    orderId,
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()
            );
        }

        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created order could not be loaded"
                ));
    }

    private List<PreparedOrderItem> prepareItems(
            List<OrderItemRequest> requestedItems
    ) {
        Map<Long, Integer> quantitiesByProduct =
                new TreeMap<>();

        for (OrderItemRequest item : requestedItems) {
            quantitiesByProduct.merge(
                    item.productId(),
                    item.quantity(),
                    this::addQuantities
            );
        }

        List<PreparedOrderItem> preparedItems =
                new ArrayList<>();

        for (Map.Entry<Long, Integer> entry
                : quantitiesByProduct.entrySet()) {
            Product product = productRepository
                    .findById(entry.getKey())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "Product " + entry.getKey()
                                    + " was not found"
                    ));

            preparedItems.add(new PreparedOrderItem(
                    product.id(),
                    entry.getValue(),
                    product.price()
            ));
        }

        return preparedItems;
    }

    private int addQuantities(
            int firstQuantity,
            int secondQuantity
    ) {
        try {
            return Math.addExact(
                    firstQuantity,
                    secondQuantity
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Combined product quantity is too large"
            );
        }
    }

    private String validateIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        String normalizedKey = idempotencyKey.strip();

        if (normalizedKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be at most 100 characters"
            );
        }

        return normalizedKey;
    }

    private record PreparedOrderItem(
            long productId,
            int quantity,
            BigDecimal unitPrice
    ) {
    }

    private Order shipOrderInTransaction(long orderId) {
        Order existingOrder = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Order " + orderId + " was not found"
                ));

        if (existingOrder.status() != OrderStatus.CREATED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Order " + orderId
                            + " is not in a shippable state"
            );
        }

        boolean updated =
                orderRepository.markShipped(orderId);

        if (!updated) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Order " + orderId
                            + " is not in a shippable state"
            );
        }

        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipped order could not be loaded"
                ));
    }

    private String validateWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "webhookUrl must not be blank"
            );
        }

        String normalizedUrl = webhookUrl.strip();

        try {
            URI uri = URI.create(normalizedUrl);
            String scheme = uri.getScheme();

            boolean validScheme =
                    "http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme);

            if (!validScheme || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "webhookUrl must be an absolute HTTP or HTTPS URL"
            );
        }

        return normalizedUrl;
    }
}