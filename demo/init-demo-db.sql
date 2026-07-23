-- ============================================================
-- DBInsightX demo target database: "shopdemo"
-- A realistic e-commerce schema with DELIBERATE performance
-- problems for the live demo:
--   * no index on orders.customer_id      -> seq scans on joins
--   * no index on order_items.order_id    -> slow order lookups
--   * no index on orders.status           -> slow status filters
--   * text search on products.name        -> LIKE '%...%' seq scan
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Read-only monitoring user for DBInsightX (pg_monitor grants access to
-- pg_stat_statements, pg_stat_activity etc. without table write rights)
CREATE USER dbperf_monitor WITH PASSWORD 'monitor123';
GRANT pg_monitor TO dbperf_monitor;
GRANT CONNECT ON DATABASE shopdemo TO dbperf_monitor;
GRANT USAGE ON SCHEMA public TO dbperf_monitor;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO dbperf_monitor;

-- ---------- Schema ----------
CREATE TABLE customers (
    id         SERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    full_name  VARCHAR(120) NOT NULL,
    country    VARCHAR(2)   NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(200)  NOT NULL,
    category VARCHAR(50)   NOT NULL,
    price    NUMERIC(10,2) NOT NULL
);

CREATE TABLE orders (
    id          SERIAL PRIMARY KEY,
    customer_id INT           NOT NULL,   -- deliberately unindexed
    status      VARCHAR(20)   NOT NULL,   -- deliberately unindexed
    total       NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id         SERIAL PRIMARY KEY,
    order_id   INT           NOT NULL,    -- deliberately unindexed
    product_id INT           NOT NULL,
    quantity   INT           NOT NULL,
    price      NUMERIC(10,2) NOT NULL
);

-- ---------- Data ----------
INSERT INTO customers (email, full_name, country, created_at)
SELECT 'user' || g || '@example.com',
       'Customer ' || g,
       (ARRAY['US','DE','IN','GB','FR','JP','BR','CA'])[1 + (g % 8)],
       now() - (g % 730) * INTERVAL '1 day'
FROM generate_series(1, 20000) g;

INSERT INTO products (name, category, price)
SELECT (ARRAY['Ultra','Pro','Max','Eco','Smart','Prime'])[1 + (g % 6)] || ' ' ||
       (ARRAY['Widget','Gadget','Charger','Speaker','Lamp','Mouse','Keyboard','Monitor'])[1 + (g % 8)] || ' ' || g,
       (ARRAY['electronics','home','office','outdoor','toys'])[1 + (g % 5)],
       round((10 + random() * 990)::numeric, 2)
FROM generate_series(1, 3000) g;

INSERT INTO orders (customer_id, status, total, created_at)
SELECT 1 + (g * 7919) % 20000,
       (ARRAY['pending','paid','shipped','delivered','cancelled'])[1 + (g % 5)],
       round((20 + random() * 2000)::numeric, 2),
       now() - (g % 365) * INTERVAL '1 day'
FROM generate_series(1, 100000) g;

INSERT INTO order_items (order_id, product_id, quantity, price)
SELECT 1 + (g * 6151) % 100000,
       1 + (g * 4093) % 3000,
       1 + (g % 5),
       round((5 + random() * 500)::numeric, 2)
FROM generate_series(1, 300000) g;

ANALYZE;

-- Warm pg_stat_statements with the query shapes the demo investigates
SELECT c.full_name, count(*) AS order_count
FROM orders o JOIN customers c ON c.id = o.customer_id
WHERE o.status = 'pending'
GROUP BY c.full_name ORDER BY order_count DESC LIMIT 10;

SELECT o.id, o.total, sum(oi.quantity) AS items
FROM orders o JOIN order_items oi ON oi.order_id = o.id
WHERE o.customer_id = 4242
GROUP BY o.id, o.total;

SELECT * FROM products WHERE name LIKE '%Speaker%' ORDER BY price DESC LIMIT 20;

SELECT pg_stat_statements_reset();
