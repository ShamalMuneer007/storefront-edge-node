\pset footer off
\echo '--- fiscal numbering per branch-day: must have count = max = distinct, min = 1'
SELECT branch_code, business_date, count(*) AS orders, min(fiscal_sequence) AS min_seq,
       max(fiscal_sequence) AS max_seq, count(DISTINCT fiscal_sequence) AS distinct_seq,
       (SELECT last_value FROM fiscal_sequence f WHERE f.branch_code = o.branch_code AND f.business_date = o.business_date) AS counter
FROM customer_order o GROUP BY branch_code, business_date;

\echo '--- chronology: orders whose placed_at is earlier than the previous fiscal number (must be 0)'
SELECT count(*) AS out_of_order FROM (
  SELECT placed_at < lag(placed_at) OVER (PARTITION BY branch_code, business_date ORDER BY fiscal_sequence) AS bad
  FROM customer_order) s WHERE bad;

\echo '--- stock reconciliation: initial = remaining + sold (diff must be 0)'
SELECT p.sku, l.initial, p.stock AS remaining, coalesce(sum(ol.quantity), 0) AS sold,
       l.initial - p.stock - coalesce(sum(ol.quantity), 0) AS diff
FROM product p JOIN load_initial_stock l ON l.sku = p.sku
LEFT JOIN order_line ol ON ol.sku = p.sku
GROUP BY p.sku, l.initial, p.stock ORDER BY p.sku;

\echo '--- idempotency: orders without exactly one key, keys without an order (must be 0, 0)'
SELECT (SELECT count(*) FROM customer_order o WHERE (SELECT count(*) FROM idempotency_key k WHERE k.order_id = o.id) <> 1) AS orders_bad_key,
       (SELECT count(*) FROM idempotency_key k LEFT JOIN customer_order o ON o.id = k.order_id WHERE o.id IS NULL) AS orphan_keys;

\echo '--- totals: stored total = subtotal + tax (must be 0)'
SELECT count(*) AS bad_totals FROM customer_order WHERE total <> subtotal + tax;

\echo '--- kitchen: orders needing prep vs events vs tickets, pending events'
SELECT (SELECT count(DISTINCT order_id) FROM order_line WHERE requires_preparation) AS prep_orders,
       (SELECT count(*) FROM outbox_event) AS events,
       (SELECT count(*) FROM kitchen_ticket) AS tickets,
       (SELECT count(*) FROM outbox_event WHERE published_at IS NULL) AS pending,
       (SELECT count(*) FROM (SELECT order_id FROM kitchen_ticket GROUP BY order_id HAVING count(*) > 1) d) AS duplicate_tickets;
