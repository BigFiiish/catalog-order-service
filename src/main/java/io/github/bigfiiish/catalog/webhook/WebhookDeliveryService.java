package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class WebhookDeliveryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    WebhookDeliveryService.class
            );

    private static final int MAX_ATTEMPTS = 3;

    private final WebhookClient webhookClient;
    private final long retryBackoffMillis;

    public WebhookDeliveryService(
            WebhookClient webhookClient,
            @Value("${app.webhook.retry-backoff-ms:200}")
            long retryBackoffMillis
    ) {
        this.webhookClient = webhookClient;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    @Async
    public void deliver(
            String webhookUrl,
            WebhookPayload payload
    ) {
        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {
            try {
                webhookClient.send(
                        webhookUrl,
                        payload
                );

                LOGGER.info(
                        "Webhook delivered for order {} on attempt {}",
                        payload.orderId(),
                        attempt
                );

                return;
            } catch (RestClientException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    LOGGER.error(
                            "Webhook delivery failed after {} attempts for order {}",
                            MAX_ATTEMPTS,
                            payload.orderId(),
                            exception
                    );

                    return;
                }

                LOGGER.warn(
                        "Webhook attempt {} failed for order {}; retrying",
                        attempt,
                        payload.orderId()
                );

                if (!waitBeforeRetry(attempt, payload)) {
                    return;
                }
            }
        }
    }

    private boolean waitBeforeRetry(
            int attempt,
            WebhookPayload payload
    ) {
        try {
            Thread.sleep(
                    retryBackoffMillis * attempt
            );

            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            LOGGER.error(
                    "Webhook retry interrupted for order {}",
                    payload.orderId(),
                    exception
            );

            return false;
        }
    }
}