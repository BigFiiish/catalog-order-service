-- ============================================================
-- Analytics query: Top three products per category over the last 90 days
-- ============================================================

WITH product_sales AS (
    SELECT
        p.id AS product_id,
        p.category,
        p.name AS product_name,
        SUM(oi.quantity) AS total_quantity_sold
    FROM orders o
             JOIN order_items oi
                  ON oi.order_id = o.id
             JOIN products p
                  ON p.id = oi.product_id
    WHERE o.created_at
              >= CURRENT_TIMESTAMP - INTERVAL '90' DAY
    GROUP BY
        p.id,
        p.category,
        p.name
),
     ranked_products AS (
         SELECT
             category,
             product_name,
             total_quantity_sold,
             RANK() OVER (
            PARTITION BY category
            ORDER BY total_quantity_sold DESC
        ) AS rank_within_category
         FROM product_sales
     )
SELECT
    category,
    product_name,
    total_quantity_sold,
    rank_within_category
FROM ranked_products
WHERE rank_within_category <= 3
ORDER BY
    category,
    rank_within_category,
    product_name;


-- Recommended indexes:

CREATE INDEX idx_orders_created_at_id
    ON orders(created_at, id);

CREATE INDEX idx_order_items_order_product
    ON order_items(order_id, product_id);

-- idx_orders_created_at_id:
-- The query first restricts orders to the trailing 90 days.
-- Starting the index with created_at supports that range filter.
-- Including id supports the subsequent join to order_items.

-- idx_order_items_order_product:
-- After finding the relevant orders, the query joins order_items
-- through order_id and groups the matching rows by product_id.
-- This index supports both operations efficiently.

-- No additional index is needed on products for this query.
-- products.id is already the primary key, and products contains
-- only a few thousand rows.

-- RANK intentionally gives equal sales totals the same rank.
-- Therefore, a tie at rank 3 can return more than three rows for
-- a category, preserving the correct ranking semantics.


-- ============================================================
-- Concurrency note: Race condition explanation and atomic stock deduction
-- ============================================================

-- The original SELECT-then-UPDATE implementation is unsafe because
-- two transactions can read the same stock value before either one
-- performs its UPDATE. Both requests can therefore decide that stock
-- is sufficient and both subtract from the same original inventory,
-- causing overselling or a negative stock quantity.

UPDATE products
SET stock_quantity = stock_quantity - ?
WHERE id = ?
  AND stock_quantity >= ?;

-- Parameter order:
-- 1. Requested quantity
-- 2. Product ID
-- 3. Requested quantity again
--
-- The stock check and deduction now happen in one atomic database
-- statement. Concurrent requests cannot both deduct the final units
-- when there is not enough stock for both.
--
-- Application code must inspect the affected-row count:
--
--   1 affected row  = stock was sufficient and deduction succeeded.
--   0 affected rows = the product does not exist or stock was
--                     insufficient.
--
-- In the application flow, product existence is checked separately first, so an
-- affected-row count of zero during deduction is returned as
-- HTTP 409 Conflict for insufficient stock.
