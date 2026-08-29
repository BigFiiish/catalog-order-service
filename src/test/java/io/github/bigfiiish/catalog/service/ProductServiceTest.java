package io.github.bigfiiish.catalog.service;

import io.github.bigfiiish.catalog.dto.ProductPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void returnsProductsWithPaginationMetadata() {
        ProductPageResponse response = productService.getProducts(0, 2, null);

        assertEquals(2, response.items().size());
        assertEquals(1L, response.items().get(0).id());
        assertEquals(2L, response.items().get(1).id());
        assertEquals(0, response.page());
        assertEquals(2, response.size());
        assertEquals(5L, response.totalElements());
        assertEquals(3, response.totalPages());
    }

    @Test
    void trimsCategoryBeforeFiltering() {
        ProductPageResponse response = productService.getProducts(0, 10, "  Home  ");

        assertEquals(2, response.items().size());
        assertEquals("Desk Lamp", response.items().get(0).name());
        assertEquals("Standing Desk", response.items().get(1).name());
        assertEquals(2L, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void rejectsNegativePage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.getProducts(-1, 20, null)
        );

        assertEquals(
                "page must be zero or greater",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPageSizesOutsideAllowedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> productService.getProducts(0, 0, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> productService.getProducts(0, 101, null)
        );
    }
}