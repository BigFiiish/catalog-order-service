package io.github.bigfiiish.catalog.repository;

import io.github.bigfiiish.catalog.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private static final RowMapper<Product> PRODUCT_ROW_MAPPER =
            (resultSet, rowNumber) -> new Product(
                    resultSet.getLong("id"),
                    resultSet.getString("name"),
                    resultSet.getBigDecimal("price"),
                    resultSet.getInt("stock_quantity"),
                    resultSet.getString("category")
            );

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Product> findById(long id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, name, price, stock_quantity, category
                        FROM products
                        WHERE id = ?
                        """,
                        PRODUCT_ROW_MAPPER,
                        id
                )
                .stream()
                .findFirst();
    }

    public List<Product> findPage(int page, int size, String category) {
        long offset = (long) page * size;

        if (category == null || category.isBlank()) {
            return jdbcTemplate.query(
                    """
                    SELECT id, name, price, stock_quantity, category
                    FROM products
                    ORDER BY id
                    LIMIT ? OFFSET ?
                    """,
                    PRODUCT_ROW_MAPPER,
                    size,
                    offset
            );
        }

        return jdbcTemplate.query(
                """
                SELECT id, name, price, stock_quantity, category
                FROM products
                WHERE category = ?
                ORDER BY id
                LIMIT ? OFFSET ?
                """,
                PRODUCT_ROW_MAPPER,
                category,
                size,
                offset
        );
    }

    public long count(String category) {
        Long total;

        if (category == null || category.isBlank()) {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products",
                    Long.class
            );
        } else {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE category = ?",
                    Long.class,
                    category
            );
        }

        return total == null ? 0 : total;
    }

    public boolean deductStock(long productId, int quantity) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE products
                SET stock_quantity = stock_quantity - ?
                WHERE id = ?
                  AND stock_quantity >= ?
                """,
                quantity,
                productId,
                quantity
        );

        return updatedRows == 1;
    }

}