package io.github.bigfiiish.catalog.repository;

import io.github.bigfiiish.catalog.model.Order;
import io.github.bigfiiish.catalog.model.OrderItem;
import io.github.bigfiiish.catalog.model.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final RowMapper<OrderHeader> ORDER_HEADER_ROW_MAPPER =
            (resultSet, rowNumber) -> new OrderHeader(
                    resultSet.getLong("id"),
                    resultSet.getString("customer_email"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    OrderStatus.valueOf(resultSet.getString("status"))
            );

    private static final RowMapper<OrderItem> ORDER_ITEM_ROW_MAPPER =
            (resultSet, rowNumber) -> new OrderItem(
                    resultSet.getLong("id"),
                    resultSet.getLong("product_id"),
                    resultSet.getInt("quantity"),
                    resultSet.getBigDecimal("unit_price")
            );

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Order> findById(long id) {
        return findOrder(
                """
                SELECT id, customer_email, created_at, status
                FROM orders
                WHERE id = ?
                """,
                id
        );
    }

    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return findOrder(
                """
                SELECT id, customer_email, created_at, status
                FROM orders
                WHERE idempotency_key = ?
                """,
                idempotencyKey
        );
    }

    public long insertOrder(
            String customerEmail,
            Instant createdAt,
            OrderStatus status,
            String idempotencyKey
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        int updatedRows = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO orders (
                        customer_email,
                        created_at,
                        status,
                        idempotency_key
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    new String[]{"id"}
            );

            statement.setString(1, customerEmail);
            statement.setTimestamp(2, Timestamp.from(createdAt));
            statement.setString(3, status.name());
            statement.setString(4, idempotencyKey);

            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (updatedRows != 1 || generatedId == null) {
            throw new IllegalStateException(
                    "Failed to create order"
            );
        }

        return generatedId.longValue();
    }

    public void insertOrderItem(
            long orderId,
            long productId,
            int quantity,
            BigDecimal unitPrice
    ) {
        int updatedRows = jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    quantity,
                    unit_price
                )
                VALUES (?, ?, ?, ?)
                """,
                orderId,
                productId,
                quantity,
                unitPrice
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Failed to create order item"
            );
        }
    }

    public boolean markShipped(long orderId) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE orders
                SET status = ?
                WHERE id = ?
                  AND status = ?
                """,
                OrderStatus.SHIPPED.name(),
                orderId,
                OrderStatus.CREATED.name()
        );

        return updatedRows == 1;
    }

    private Optional<Order> findOrder(
            String sql,
            Object argument
    ) {
        return jdbcTemplate.query(
                        sql,
                        ORDER_HEADER_ROW_MAPPER,
                        argument
                )
                .stream()
                .findFirst()
                .map(this::buildOrder);
    }

    private Order buildOrder(OrderHeader header) {
        List<OrderItem> items = jdbcTemplate.query(
                """
                SELECT id, product_id, quantity, unit_price
                FROM order_items
                WHERE order_id = ?
                ORDER BY id
                """,
                ORDER_ITEM_ROW_MAPPER,
                header.id()
        );

        return new Order(
                header.id(),
                header.customerEmail(),
                header.createdAt(),
                header.status(),
                items
        );
    }

    private record OrderHeader(
            long id,
            String customerEmail,
            Instant createdAt,
            OrderStatus status
    ) {
    }
}