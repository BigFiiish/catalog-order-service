package io.github.bigfiiish.catalog.repository;

import io.github.bigfiiish.catalog.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:stockconcurrencydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
)
class StockConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void onlyOneConcurrentBuyerCanClaimTheLastUnit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> deductAfterSignal(ready, start)),
                    executor.submit(() -> deductAfterSignal(ready, start))
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long successfulAttempts = attempts.stream()
                    .map(this::awaitResult)
                    .filter(Boolean::booleanValue)
                    .count();

            Product product = productRepository.findById(4L).orElseThrow();

            assertEquals(1L, successfulAttempts);
            assertEquals(0, product.stockQuantity());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean deductAfterSignal(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return productRepository.deductStock(4L, 1);
    }

    private boolean awaitResult(Future<Boolean> result) {
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent stock deduction did not finish", exception);
        }
    }
}
