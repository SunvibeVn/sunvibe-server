-- Administrative-area catalog (acquisition-owner-intake brief BR-6): a province (code, name) and the wards of a
-- province (code, name, type Phường / Xã / Đặc khu). Codes are the official ones. There is no district level: the
-- 2025 reform removed it. Expand only: new tables, no existing table, column or row changes.
-- type holds WARD, COMMUNE or SPECIAL_ZONE; the CHECK keeps a typo out, because owners pick a ward from this list.
-- A ward name is unique within its province and type. idx_wards_province_code is the index of the foreign key
-- (flyway rule), even though uk_wards_province_code_name_type also starts with province_code.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS provinces (
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT provinces_pkey PRIMARY KEY (code)
);

CREATE TABLE IF NOT EXISTS wards (
    code VARCHAR(10) NOT NULL,
    province_code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT wards_pkey PRIMARY KEY (code),
    CONSTRAINT fk_wards_province_code FOREIGN KEY (province_code) REFERENCES provinces (code),
    CONSTRAINT ck_wards_type CHECK (type IN ('WARD', 'COMMUNE', 'SPECIAL_ZONE')),
    CONSTRAINT uk_wards_province_code_name_type UNIQUE (province_code, name, type)
);

CREATE INDEX IF NOT EXISTS idx_wards_province_code ON wards (province_code);
