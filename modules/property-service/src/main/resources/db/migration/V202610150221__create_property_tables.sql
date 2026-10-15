-- Baseline for the property-service tables. Until now only Hibernate ddl-auto created them; this reproduces
-- that DDL (columns, types, nullability, keys, indexes) so Flyway owns the schema (ADR-002, wave W3).
-- IF NOT EXISTS: a database already built by Hibernate keeps its tables and indexes unchanged.
-- No CHECK on properties.property_type: enum values are validated by the application (see V202610121100).
-- Foreign keys get readable names instead of Hibernate's random ones. idx_property_code and idx_property_id_01
-- are UNIQUE constraints (not plain indexes) because that is what Hibernate builds for a unique @Index.
-- amenity_type_id, rule_category_id and document_type_id have no foreign key, as in the entities.

-- Master data (assigned ids; rows come from R__property_master_data.sql)
CREATE TABLE IF NOT EXISTS amenity_types (
    id INTEGER NOT NULL,
    amenity_name VARCHAR(100) NOT NULL,
    category_id INTEGER,
    category_name VARCHAR(100),
    description VARCHAR(500),
    estimated_price NUMERIC(15, 2),
    icon VARCHAR(100),
    is_standard BOOLEAN NOT NULL,
    level_of_need VARCHAR(50) NOT NULL,
    CONSTRAINT amenity_types_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS rule_categories (
    id INTEGER NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    default_severity VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon VARCHAR(100),
    is_negotiable BOOLEAN NOT NULL,
    CONSTRAINT rule_categories_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS document_types (
    id INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon VARCHAR(100),
    is_archived BOOLEAN NOT NULL,
    renewal_frequency_days INTEGER,
    requires_renewal BOOLEAN NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    CONSTRAINT document_types_pkey PRIMARY KEY (id)
);

-- Properties
CREATE TABLE IF NOT EXISTS properties (
    id VARCHAR(36) NOT NULL,
    bathrooms INTEGER NOT NULL,
    bedrooms INTEGER NOT NULL,
    city VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    deposit_price NUMERIC(15, 0) NOT NULL,
    description TEXT,
    district VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    min_rent_months INTEGER NOT NULL,
    monthly_price NUMERIC(15, 0) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    postal_code VARCHAR(20),
    property_code VARCHAR(50) NOT NULL,
    square_meters DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    street VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    property_type VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    CONSTRAINT properties_pkey PRIMARY KEY (id),
    CONSTRAINT idx_property_code UNIQUE (property_code)
);

CREATE INDEX IF NOT EXISTS idx_owner_id ON properties (owner_id);
CREATE INDEX IF NOT EXISTS idx_status ON properties (status);
CREATE INDEX IF NOT EXISTS idx_district ON properties (district);
CREATE INDEX IF NOT EXISTS idx_created_at ON properties (created_at);

CREATE TABLE IF NOT EXISTS property_images (
    id VARCHAR(36) NOT NULL,
    caption VARCHAR(255),
    display_order INTEGER NOT NULL,
    image_key VARCHAR(500),
    image_url TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL,
    property_id VARCHAR(36) NOT NULL,
    uploaded_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    CONSTRAINT property_images_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_images_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_property_id_02 ON property_images (property_id);
CREATE INDEX IF NOT EXISTS idx_is_primary ON property_images (is_primary);
CREATE INDEX IF NOT EXISTS idx_display_order ON property_images (display_order);

CREATE TABLE IF NOT EXISTS property_amenity (
    id VARCHAR(36) NOT NULL,
    amenity_name VARCHAR(255) NOT NULL,
    amenity_type_id INTEGER,
    category VARCHAR(100),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE,
    custom_note VARCHAR(500),
    description VARCHAR(500),
    is_included BOOLEAN NOT NULL,
    level_of_need VARCHAR(50),
    property_id VARCHAR(36) NOT NULL,
    CONSTRAINT property_amenity_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_amenity_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_amenity_property_id ON property_amenity (property_id);
CREATE INDEX IF NOT EXISTS idx_amenity_type_id ON property_amenity (amenity_type_id);
CREATE INDEX IF NOT EXISTS idx_amenity_is_included ON property_amenity (is_included);

CREATE TABLE IF NOT EXISTS property_rules (
    id VARCHAR(36) NOT NULL,
    category VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE,
    custom_note VARCHAR(500),
    description TEXT,
    is_mandatory BOOLEAN NOT NULL,
    property_id VARCHAR(36) NOT NULL,
    rule_category_id INTEGER,
    rule_name VARCHAR(255) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    CONSTRAINT property_rules_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_rules_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_rule_property_id ON property_rules (property_id);
CREATE INDEX IF NOT EXISTS idx_rule_category_id ON property_rules (rule_category_id);
CREATE INDEX IF NOT EXISTS idx_rule_severity ON property_rules (severity);
CREATE INDEX IF NOT EXISTS idx_rule_is_mandatory ON property_rules (is_mandatory);

CREATE TABLE IF NOT EXISTS property_document (
    id VARCHAR(36) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    document_type_id INTEGER,
    expiry_date DATE,
    file_key VARCHAR(500),
    file_path TEXT NOT NULL,
    issue_date DATE NOT NULL,
    issuer VARCHAR(255),
    notes TEXT,
    property_id VARCHAR(36) NOT NULL,
    uploaded_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    CONSTRAINT property_document_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_document_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_property_id_03 ON property_document (property_id);
CREATE INDEX IF NOT EXISTS idx_document_type ON property_document (document_type);
CREATE INDEX IF NOT EXISTS idx_issue_date ON property_document (issue_date);
CREATE INDEX IF NOT EXISTS idx_expiry_date ON property_document (expiry_date);

CREATE TABLE IF NOT EXISTS property_prices (
    id VARCHAR(36) NOT NULL,
    deposit_price NUMERIC(15, 0) NOT NULL,
    effective_date DATE NOT NULL,
    end_date DATE,
    is_current BOOLEAN NOT NULL,
    monthly_price NUMERIC(15, 0) NOT NULL,
    property_id VARCHAR(36) NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT property_prices_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_prices_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_property_id_04 ON property_prices (property_id);
CREATE INDEX IF NOT EXISTS idx_effective_date ON property_prices (effective_date);
CREATE INDEX IF NOT EXISTS idx_is_current ON property_prices (is_current);
CREATE INDEX IF NOT EXISTS idx_date_range ON property_prices (effective_date, end_date);

-- One availability row per property: the UNIQUE constraint is also the index for the foreign key.
CREATE TABLE IF NOT EXISTS property_availability (
    id VARCHAR(36) NOT NULL,
    available_from_date DATE NOT NULL,
    booking_id VARCHAR(36),
    property_id VARCHAR(36) NOT NULL,
    reserved_until_date DATE,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT property_availability_pkey PRIMARY KEY (id),
    CONSTRAINT idx_property_id_01 UNIQUE (property_id),
    CONSTRAINT fk_property_availability_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_availability_status ON property_availability (status);
CREATE INDEX IF NOT EXISTS idx_available_from_date ON property_availability (available_from_date);
CREATE INDEX IF NOT EXISTS idx_reserved_until_date ON property_availability (reserved_until_date);
