package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import io.github.bigfiiish.catalog.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WebhookDeliveryServiceTest {

    private static final String WEBHOOK_URL =
            "https://example.com/webhook";

    private static final WebhookPayload PAYLOAD =
            new WebhookPayload(
                    10L,
                    OrderStatus.SHIPPED
            );

    @Test
    void stopsAfterFirstSuccessfulAttempt() {
        WebhookClient webhookClient =
                mock(WebhookClient.class);

        WebhookDeliveryService service =
                new WebhookDeliveryService(
                        webhookClient,
                        0
                );

        service.deliver(WEBHOOK_URL, PAYLOAD);

        verify(
                webhookClient,
                times(1)
        ).send(WEBHOOK_URL, PAYLOAD);
    }

    @Test
    void retriesUntilThirdAttemptSucceeds() {
        WebhookClient webhookClient =
                mock(WebhookClient.class);

        doThrow(new RestClientException(
                "first failure"
        ))
                .doThrow(new RestClientException(
                        "second failure"
                ))
                .doNothing()
                .when(webhookClient)
                .send(WEBHOOK_URL, PAYLOAD);

        WebhookDeliveryService service =
                new WebhookDeliveryService(
                        webhookClient,
                        0
                );

        service.deliver(WEBHOOK_URL, PAYLOAD);

        verify(
                webhookClient,
                times(3)
        ).send(WEBHOOK_URL, PAYLOAD);
    }

    @Test
    void stopsAfterThreeFailedAttempts() {
        WebhookClient webhookClient =
                mock(WebhookClient.class);

        doThrow(new RestClientException(
                "delivery failure"
        ))
                .when(webhookClient)
                .send(WEBHOOK_URL, PAYLOAD);

        WebhookDeliveryService service =
                new WebhookDeliveryService(
                        webhookClient,
                        0
                );

        service.deliver(WEBHOOK_URL, PAYLOAD);

        verify(
                webhookClient,
                times(3)
        ).send(WEBHOOK_URL, PAYLOAD);
    }
}