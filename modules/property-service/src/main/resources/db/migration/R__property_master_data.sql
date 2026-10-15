-- Property master data: the same ids and values PropertyMasterDataInitializer seeds (ADR-002, wave W3).
-- Repeatable: Flyway re-runs it when this file changes. ON CONFLICT (id) DO NOTHING keeps existing rows,
-- so it is safe on a database the runner has already filled. To change a row, add an UPDATE here.

INSERT INTO rule_categories (id, category_name, description, default_severity, is_negotiable, icon) VALUES
    (1, 'PETS', 'Pet policy', 'HIGH', TRUE, '🐾'),
    (2, 'SMOKING', 'Smoking policy', 'HIGH', FALSE, '🚬'),
    (3, 'NOISE', 'Noise policy', 'MEDIUM', TRUE, '🔊'),
    (4, 'GUESTS', 'Guest policy', 'LOW', TRUE, '👥'),
    (5, 'PARTIES', 'Party/event policy', 'HIGH', FALSE, '🎉'),
    (6, 'COOKING', 'Cooking policy', 'LOW', TRUE, '🍳')
ON CONFLICT (id) DO NOTHING;

-- Ids 1-4 are categories (no parent); 10+ are amenities that reference a category.
INSERT INTO amenity_types (id, amenity_name, category_id, category_name, description, is_standard, level_of_need, icon) VALUES
    (1, 'General Amenities', NULL, NULL, NULL, TRUE, 'STANDARD', NULL),
    (2, 'Kitchen', NULL, NULL, NULL, TRUE, 'STANDARD', NULL),
    (3, 'Bedroom', NULL, NULL, NULL, TRUE, 'STANDARD', NULL),
    (4, 'Bathroom', NULL, NULL, NULL, TRUE, 'STANDARD', NULL),
    (10, 'WiFi', 1, 'General Amenities', 'Wireless internet connection', TRUE, 'STANDARD', '📶'),
    (11, 'Air Conditioner', 3, 'Bedroom', 'Air conditioning', TRUE, 'STANDARD', '❄️'),
    (12, 'Washing Machine', 1, 'General Amenities', 'Private washing machine', FALSE, 'PREMIUM', '🫧'),
    (13, 'Refrigerator', 2, 'Kitchen', 'Full-size refrigerator', TRUE, 'STANDARD', '🧊'),
    (14, 'Gas/Electric Stove', 2, 'Kitchen', 'Cooking stove', TRUE, 'STANDARD', '🔥'),
    (15, 'Water Heater', 4, 'Bathroom', 'Hot and cold water', TRUE, 'STANDARD', '🚿'),
    (16, 'Parking', 1, 'General Amenities', 'Motorbike/car parking space', FALSE, 'OPTIONAL', '🅿️'),
    (17, 'Balcony', 1, 'General Amenities', 'Balcony/rooftop terrace', FALSE, 'OPTIONAL', '🌿')
ON CONFLICT (id) DO NOTHING;

INSERT INTO document_types (id, type_name, description, requires_renewal, renewal_frequency_days, is_archived, icon) VALUES
    (1, 'Land Use Rights Certificate', 'Red book / Pink book', FALSE, NULL, FALSE, '📄'),
    (2, 'Building Permit', 'Construction building permit', FALSE, NULL, FALSE, '🏗️'),
    (3, 'Fire Safety Certificate', 'Fire prevention and firefighting certificate', TRUE, 365, FALSE, '🔥'),
    (4, 'Home Insurance', 'Home insurance contract', TRUE, 365, FALSE, '🛡️'),
    (5, 'Land Lease Agreement', 'Land use rights lease contract', TRUE, 1825, FALSE, '📋')
ON CONFLICT (id) DO NOTHING;
