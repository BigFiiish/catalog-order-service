package io.github.bigfiiish.catalog.service;

import io.github.bigfiiish.catalog.dto.ProductPageResponse;
import io.github.bigfiiish.catalog.model.Product;
import io.github.bigfiiish.catalog.repository.ProductRepository;
import io.github.bigfiiish.catalog.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class ProductService {

    // Prevents excessively large API responses.
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductPageResponse getProducts(
            int page,
            int size,
            String category
    ) {
        validatePagination(page, size);   // The page number >= 0. 1 <= size <= 100.

        String normalizedCategory = normalizeCategory(category);

        List<Product> products = productRepository.findPage(page, size, normalizedCategory);

        long totalElements = productRepository.count(normalizedCategory);

        // Integer ceiling division: five products at size two require three pages.
        int totalPages = (int) ((totalElements + size - 1) / size);

        return new ProductPageResponse(
                products,
                page,
                size,
                totalElements,
                totalPages
        );
    }

    public Product getProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Product " + id + " was not found"
                ));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        return category.strip();
    }
}