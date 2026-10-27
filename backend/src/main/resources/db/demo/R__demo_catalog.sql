INSERT INTO products (sku, name, description, category, price, stock_quantity, company_id) VALUES
('MG-001', 'The Silk Pillow', 'A quiet statement. Champagne tones and a beautifully soft finish.', 'Living', 129.00, 12,1),
('MG-002', 'Midnight Robe', 'An unhurried morning essential in deep obsidian.', 'Wear', 249.00, 8,2),
('MG-003', 'Golden Hour Scrunchie', 'A little luxury for your everyday ritual.', 'Accessories', 39.00, 1,3),
('MG-004', 'The Sleep Mask', 'Switch off the world. Make room for rest.', 'Wellness', 79.00, 0,3)
ON CONFLICT (sku) DO NOTHING;
-- Seed data is repeatable and executes after versioned schema migrations.
