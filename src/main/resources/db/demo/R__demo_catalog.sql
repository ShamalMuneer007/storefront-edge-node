INSERT INTO product (id, sku, name, unit_price, currency, tax_rate, requires_preparation, stock)
VALUES ('0192a000-0000-7000-8000-000000000001', 'MEAL-BOX-CHICKEN', 'Fresh Chicken Meal Box', 25.00, 'AED', 5.00, FALSE, 10),
       ('0192a000-0000-7000-8000-000000000002', 'BURGER-CLASSIC',   'Classic Burger',         32.50, 'AED', 5.00, TRUE,  200),
       ('0192a000-0000-7000-8000-000000000003', 'WATER-SPARKLING',  'Sparkling Water 330ml',   8.00, 'AED', 5.00, FALSE, 50)
ON CONFLICT (sku) DO NOTHING;
