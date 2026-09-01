package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RestWebhookClient implements WebhookClient {

    private final RestClient restClient;
    private final boolean deliveryEnabled;

    public RestWebhookClient(
            @Value("${app.webhook.delivery-enabled:true}")
            boolean deliveryEnabled
    ) {
        this.deliveryEnabled = deliveryEnabled;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Duration.ofSeconds(2)
        );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(2)
        );

        this.restClient = RestClient
                .builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void send(
            String webhookUrl,
            WebhookPayload payload
    ) {
        if (!deliveryEnabled) {
            return;
        }

        restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
