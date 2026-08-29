package io.github.bigfiiish.catalog.webhook;

import io.github.bigfiiish.catalog.dto.WebhookPayload;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RestWebhookClient implements WebhookClient {

    private final RestClient restClient;

    public RestWebhookClient() {
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
        restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}