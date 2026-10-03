-- The test profile runs on H2 with Hibernate's create-drop and Flyway switched off, so the rows that
-- V4__add_inventory_categories.sql seeds in a real database are not there. DataSeeder needs the two
-- pickle families to exist before it can build the catalogue.
INSERT INTO inventory_categories (name, slug, icon, veg, display_order, active, archived, created_at, updated_at) VALUES
    ('Veg Pickles',     'veg-pickles',     'V', TRUE,  1, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Non-Veg Pickles', 'non-veg-pickles', 'N', FALSE, 2, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
