package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;

public interface WebhookClient {

    void send(
            String webhookUrl,
            WebhookPayload payload
    );
}