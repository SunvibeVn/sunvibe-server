-- First catalog data (acquisition-owner-intake brief BR-6): Đà Nẵng (code 48) and its 16 wards, communes and special
-- zone, exactly as in the brief. Phường = WARD, Xã = COMMUNE, Đặc khu = SPECIAL_ZONE. Another province or ward is
-- added by a new migration like this one; there is no admin screen yet.
-- Repeatable: ON CONFLICT (code) DO NOTHING keeps existing rows, so running it again changes nothing.
-- Expand only: it inserts rows into the new catalog tables and touches no other table.
INSERT INTO provinces (code, name) VALUES
    ('48', 'Đà Nẵng')
ON CONFLICT (code) DO NOTHING;

INSERT INTO wards (code, province_code, name, type) VALUES
    ('20242', '48', 'Hải Châu', 'WARD'),
    ('20257', '48', 'Hòa Cường', 'WARD'),
    ('20209', '48', 'Thanh Khê', 'WARD'),
    ('20305', '48', 'An Khê', 'WARD'),
    ('20275', '48', 'An Hải', 'WARD'),
    ('20263', '48', 'Sơn Trà', 'WARD'),
    ('20285', '48', 'Ngũ Hành Sơn', 'WARD'),
    ('20200', '48', 'Hòa Khánh', 'WARD'),
    ('20194', '48', 'Hải Vân', 'WARD'),
    ('20197', '48', 'Liên Chiểu', 'WARD'),
    ('20260', '48', 'Cẩm Lệ', 'WARD'),
    ('20314', '48', 'Hòa Xuân', 'WARD'),
    ('20320', '48', 'Hòa Vang', 'COMMUNE'),
    ('20332', '48', 'Hòa Tiến', 'COMMUNE'),
    ('20308', '48', 'Bà Nà', 'COMMUNE'),
    ('20333', '48', 'Hoàng Sa', 'SPECIAL_ZONE')
ON CONFLICT (code) DO NOTHING;
