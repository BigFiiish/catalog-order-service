package io.github.bigfiiish.catalog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import io.github.bigfiiish.catalog.model.OrderStatus;
import io.github.bigfiiish.catalog.repository.OrderRepository;
import io.github.bigfiiish.catalog.webhook.WebhookDeliveryService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:ordercontrollerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final String API_KEY_HEADER =
            "X-API-Key";

    private static final String API_KEY =
            "test-key";

    private static final String IDEMPOTENCY_HEADER =
            "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private WebhookDeliveryService webhookDeliveryService;

    @Test
    void createsNewOrderWithHttp201() throws Exception {
        postOrder(
                "controller-create-key",
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1
                    }
                  ]
                }
                """
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerEmail")
                        .value("jane@example.com"))
                .andExpect(jsonPath("$.status")
                        .value("CREATED"))
                .andExpect(jsonPath("$.items.length()")
                        .value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .isNumber())
                .andExpect(jsonPath("$.items[0].productId")
                        .value(1))
                .andExpect(jsonPath("$.items[0].quantity")
                        .value(1));
    }

    @Test
    void returnsHttp200ForIdempotencyReplay()
            throws Exception {
        String body = """
                {
                  "customerEmail": "retry@example.com",
                  "items": [
                    {
                      "productId": 2,
                      "quantity": 1
                    }
                  ]
                }
                """;

        postOrder("controller-replay-key", body)
                .andExpect(status().isCreated());

        postOrder("controller-replay-key", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("CREATED"));
    }

    @Test
    void rejectsMissingIdempotencyKey()
            throws Exception {
        postOrder(
                null,
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": [
                    {
                      "productId": 5,
                      "quantity": 1
                    }
                  ]
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Idempotency-Key header is required"
                        ));
    }

    @Test
    void rejectsBlankCustomerEmail()
            throws Exception {
        postOrder(
                "blank-email-key",
                """
                {
                  "customerEmail": "   ",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 1
                    }
                  ]
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "customerEmail must not be blank"
                        ));
    }

    @Test
    void rejectsEmptyItems() throws Exception {
        postOrder(
                "empty-items-key",
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": []
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("items must not be empty"));
    }

    @Test
    void rejectsNonPositiveQuantity()
            throws Exception {
        postOrder(
                "invalid-quantity-key",
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": [
                    {
                      "productId": 1,
                      "quantity": 0
                    }
                  ]
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("quantity must be positive"));
    }

    @Test
    void returns404ForMissingProduct()
            throws Exception {
        postOrder(
                "missing-product-controller-key",
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": [
                    {
                      "productId": 999,
                      "quantity": 1
                    }
                  ]
                }
                """
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Product 999 was not found"));
    }

    @Test
    void returns409ForInsufficientStock()
            throws Exception {
        postOrder(
                "insufficient-stock-controller-key",
                """
                {
                  "customerEmail": "jane@example.com",
                  "items": [
                    {
                      "productId": 3,
                      "quantity": 1
                    }
                  ]
                }
                """
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Insufficient stock for product 3"
                        ));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        postOrder(
                "malformed-json-key",
                "{"
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Malformed or missing request body"
                        ));
    }

    @Test
    void shipsCreatedOrderAndRejectsSecondAttempt()
            throws Exception {
        String idempotencyKey =
                "ship-controller-key";

        postOrder(
                idempotencyKey,
                """
                {
                  "customerEmail": "ship@example.com",
                  "items": [
                    {
                      "productId": 5,
                      "quantity": 1
                    }
                  ]
                }
                """
        ).andExpect(status().isCreated());

        long orderId = orderRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow()
                .id();

        String webhookUrl =
                "https://example.com/order-shipped";

        postShip(
                orderId,
                """
                {
                  "webhookUrl":
                    "https://example.com/order-shipped"
                }
                """
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(orderId))
                .andExpect(jsonPath("$.status")
                        .value("SHIPPED"));

        verify(
                webhookDeliveryService,
                times(1)
        ).deliver(
                eq(webhookUrl),
                eq(new WebhookPayload(
                        orderId,
                        OrderStatus.SHIPPED
                ))
        );

        postShip(
                orderId,
                """
                {
                  "webhookUrl":
                    "https://example.com/order-shipped"
                }
                """
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Order " + orderId
                                        + " is not in a shippable state"
                        ));

        verify(
                webhookDeliveryService,
                times(1)
        ).deliver(
                eq(webhookUrl),
                eq(new WebhookPayload(
                        orderId,
                        OrderStatus.SHIPPED
                ))
        );
    }

    @Test
    void returns404WhenShippingMissingOrder()
            throws Exception {
        postShip(
                999L,
                """
                {
                  "webhookUrl":
                    "https://example.com/order-shipped"
                }
                """
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Order 999 was not found"));
    }

    @Test
    void rejectsBlankWebhookUrl()
            throws Exception {
        postShip(
                1L,
                """
                {
                  "webhookUrl": "   "
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "webhookUrl must not be blank"
                        ));
    }

    @Test
    void rejectsUnsupportedWebhookUrl()
            throws Exception {
        postShip(
                1L,
                """
                {
                  "webhookUrl":
                    "ftp://example.com/webhook"
                }
                """
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "webhookUrl must be an absolute HTTP or HTTPS URL"
                        ));
    }

    private ResultActions postOrder(
            String idempotencyKey,
            String body
    ) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/orders")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);

        if (idempotencyKey != null) {
            request.header(
                    IDEMPOTENCY_HEADER,
                    idempotencyKey
            );
        }

        return mockMvc.perform(request);
    }

    private ResultActions postShip(
            long orderId,
            String body
    ) throws Exception {
        return mockMvc.perform(
                post("/api/orders/{id}/ship", orderId)
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }
}