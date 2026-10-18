-- Run once against a fresh database (docker compose down -v) with the app already started.
INSERT INTO product (id, sku, name, unit_price, currency, tax_rate, requires_preparation, stock) VALUES
    (gen_random_uuid(), 'LOAD-HOT',    'Limited Meal Box', 25.00, 'AED', 5.00, FALSE, 1000),
    (gen_random_uuid(), 'LOAD-BURGER', 'Burger',           32.50, 'AED', 5.00, TRUE,  1000000),
    (gen_random_uuid(), 'LOAD-WATER',  'Water',             8.00, 'AED', 5.00, FALSE, 1000000),
    (gen_random_uuid(), 'LOAD-FRIES',  'Fries',            12.25, 'AED', 5.00, TRUE,  1000000);

CREATE TABLE load_initial_stock AS SELECT sku, stock AS initial FROM product;
