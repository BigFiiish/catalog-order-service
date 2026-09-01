package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import io.github.bigfiiish.catalog.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RestWebhookClientTest {

    @Test
    void skipsNetworkDeliveryWhenDisabledForPublicDemo() {
        RestWebhookClient client =
                new RestWebhookClient(false);

        assertDoesNotThrow(() -> client.send(
                "http://127.0.0.1:1/webhook",
                new WebhookPayload(
                        1L,
                        OrderStatus.SHIPPED
                )
        ));
    }
}
