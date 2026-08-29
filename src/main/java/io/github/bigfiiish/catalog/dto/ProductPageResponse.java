package io.github.bigfiiish.catalog.dto;

import io.github.bigfiiish.catalog.model.Product;

import java.util.List;

public record ProductPageResponse(
        List<Product> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}