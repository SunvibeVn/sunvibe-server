-- V202610091201__create_users_table.sql
CREATE TABLE users (
    id VARCHAR(26) NOT NULL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(100),
    password VARCHAR(255) NOT NULL,
    profile_image VARCHAR(500),
    id_card_number TEXT NOT NULL,
    id_card_number_hash VARCHAR(64) NOT NULL,
    id_card_front_url TEXT,
    id_card_back_url TEXT,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    user_status VARCHAR(20) NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    last_update TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_users_username ON users (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_users_id_card_number_hash ON users (id_card_number_hash) WHERE deleted_at IS NULL;
