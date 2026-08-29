package io.github.bigfiiish.catalog.repository;

import io.github.bigfiiish.catalog.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void findsSeededProductById() {
        Product product = productRepository.findById(1).orElseThrow();

        assertEquals(1L, product.id());
        assertEquals("Wireless Mouse", product.name());
        assertEquals(new BigDecimal("24.99"), product.price());
        assertEquals(50, product.stockQuantity());
        assertEquals("Electronics", product.category());
    }

    @Test
    void findsOutOfStockProduct() {
        Product product = productRepository.findById(3).orElseThrow();

        assertEquals("Desk Lamp", product.name());
        assertEquals(0, product.stockQuantity());
    }

    @Test
    void returnsEmptyWhenProductDoesNotExist() {
        assertTrue(productRepository.findById(999).isEmpty());
    }

    @Test
    void returnsRequestedPageInStableOrder() {
        List<Product> firstPage = productRepository.findPage(0, 2, null);
        List<Product> secondPage = productRepository.findPage(1, 2, null);

        assertEquals(
                List.of(1L, 2L),
                firstPage.stream().map(Product::id).toList()
        );

        assertEquals(
                List.of(3L, 4L),
                secondPage.stream().map(Product::id).toList()
        );
    }

    @Test
    void filtersProductsByCategory() {
        List<Product> products =
                productRepository.findPage(0, 10, "Home");

        assertEquals(
                List.of("Desk Lamp", "Standing Desk"),
                products.stream().map(Product::name).toList()
        );
    }

    @Test
    void countsProductsWithAndWithoutCategory() {
        assertEquals(5L, productRepository.count(null));
        assertEquals(2L, productRepository.count("Home"));
        assertEquals(1L, productRepository.count("Office"));
    }

    @Test
    void deductsStockOnlyWhenEnoughStockExists() {
        assertTrue(productRepository.deductStock(4, 1));
        assertFalse(productRepository.deductStock(4, 1));

        Product product = productRepository.findById(4).orElseThrow();

        assertEquals(0, product.stockQuantity());
    }

}