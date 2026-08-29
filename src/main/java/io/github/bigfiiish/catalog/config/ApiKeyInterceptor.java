package io.github.bigfiiish.catalog.config;

import io.github.bigfiiish.catalog.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyInterceptor(
            @Value("${app.api-key}") String expectedApiKey
    ) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String providedApiKey = request.getHeader(API_KEY_HEADER);

        if (!expectedApiKey.equals(providedApiKey)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid API key"
            );
        }

        return true;
    }
}