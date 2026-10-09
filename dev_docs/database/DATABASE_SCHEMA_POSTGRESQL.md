# 06. LƯỢC ĐỒ CƠ SỞ DỮ LIỆU POSTGRESQL (DATABASE SCHEMA & DDL)
## Relational Schema, Indexes, Foreign Keys & PostGIS Extensions

---

## 1. MÔ HÌNH THỰC THỂ QUAN HỆ (ERD CONCEPTUAL MODEL)

```
[users] ───────── 1:N ───────── [properties] ───────── 1:1 ───────── [short_term_configs]
   │                                   │                                    │
   │ 1:1                               │ 1:N                                │ 1:N
   ▼                                   ▼                                    ▼
[agent_profiles]                [property_images]                  [price_calendars]
   │                                   │                                    │
   │ 1:N                               │ 1:1                                │ 1:N
   ▼                                   ▼                                    ▼
[customer_leads]                [long_term_configs]                [short_term_bookings]
   │                                   │                                    │
   │ 1:N                               │ 1:N                                │
   ▼                                   ▼                                    │
[viewing_tours] ───────────────> [lease_agreements]                         │
                                       │                                    │
                                       ├─────── 1:N ──────── [lease_invoices]
                                       │
                                       ▼ 1:1
                                [deal_commissions] ◄────────────────────────┘
                                       │
                                       ▼ 1:N
                                [commission_split_items]
                                       │
                                       ▼ N:1
                                [payout_transactions]
```

---

## 2. KỊCH BẢN DDL SQL CHI TIẾT (POSTGRESQL 16 + POSTGIS)

```sql
-- 1. Kích hoạt Extension mở rộng không gian PostGIS và UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ============================================================================
-- PHÂN HỆ 1: NGƯỜI DÙNG, PHÂN QUYỀN & NHÂN VIÊN (IDENTITY & RBAC)
-- ============================================================================

-- Source of truth: Flyway migrations in modules/auth-service/src/main/resources/db/migration/; id_card_number holds AES-256-GCM ciphertext (blind index in id_card_number_hash).
CREATE TABLE users (
    id VARCHAR(26) NOT NULL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(100),
    password VARCHAR(255) NOT NULL,
    profile_image VARCHAR(500),
    id_card_number TEXT, -- optional: can be added to the profile after registration
    id_card_number_hash VARCHAR(64),
    id_card_front_url TEXT,
    id_card_back_url TEXT,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    user_status VARCHAR(20) NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    last_update TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0, -- số lần sai mật khẩu liên tiếp (khóa tạm lũy tiến)
    locked_until TIMESTAMP WITH TIME ZONE              -- từ chối đăng nhập bằng mật khẩu tới thời điểm này
);

CREATE UNIQUE INDEX idx_users_username ON users (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_users_id_card_number_hash ON users (id_card_number_hash) WHERE deleted_at IS NULL;

CREATE TABLE roles (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Refresh token của phiên đăng nhập: mỗi lần refresh sinh một dòng mới (rotation),
-- các token cùng phiên chung family_id. Chỉ lưu SHA-256 (hex) của token.
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL, -- ACTIVE | USED | REVOKED
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, -- hạn idle (7 ngày, trượt theo mỗi lần refresh)
    session_expires_at TIMESTAMP WITH TIME ZONE NOT NULL, -- hạn tuyệt đối của phiên (30 ngày kể từ login)
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(30), -- LOGOUT | LOGOUT_ALL | PASSWORD_CHANGED | PASSWORD_RESET | REUSE_DETECTED | ACCOUNT_INACTIVE
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- Yêu cầu đặt lại mật khẩu qua email: reset token + OTP 6 số, sống 15 phút.
-- Token bị hủy (is_used = true) khi dùng xong, khi nhập sai OTP 5 lần, hoặc khi cấp mã mới.
CREATE TABLE password_reset_token (
    id BIGINT NOT NULL PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0 -- số lần nhập sai OTP
);
CREATE UNIQUE INDEX idx_password_reset_token_token ON password_reset_token (token);
CREATE INDEX idx_password_reset_token_email ON password_reset_token (email);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);

-- Nhật ký bảo mật của auth-service (append-only, giữ 180 ngày). Xem dev_docs/auth/04_AUTH_AUDIT_LOG.md
CREATE TABLE auth_audit_events (
    id BIGINT NOT NULL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL, -- LOGIN_SUCCEEDED, LOGIN_FAILED, ACCOUNT_TEMPORARILY_LOCKED, ...
    user_id VARCHAR(26),             -- NULL khi không xác định được tài khoản
    subject VARCHAR(100),            -- số điện thoại / email client đã dùng
    details VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_auth_audit_events_user_id ON auth_audit_events (user_id, occurred_at);
CREATE INDEX idx_auth_audit_events_type ON auth_audit_events (event_type, occurred_at);
CREATE INDEX idx_auth_audit_events_ip ON auth_audit_events (ip_address, occurred_at);
CREATE INDEX idx_auth_audit_events_occurred_at ON auth_audit_events (occurred_at);

CREATE TABLE agent_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    agent_code VARCHAR(30) NOT NULL UNIQUE, -- Mã nhân viên: AGENT-00123
    manager_agent_id UUID REFERENCES users(id), -- Trưởng nhóm phụ trách
    assigned_territory VARCHAR(100), -- Khu vực địa bàn: "Quận Bình Thạnh, TP.HCM"
    bank_name VARCHAR(100),
    bank_account_number VARCHAR(50),
    bank_account_name VARCHAR(150),
    total_deals_closed INT DEFAULT 0,
    total_commission_earned NUMERIC(15, 2) DEFAULT 0.00,
    rating_score NUMERIC(3, 2) DEFAULT 5.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- PHÂN HỆ 2: BẤT ĐỘNG SẢN & KHO HÀNG (PROPERTY MANAGEMENT)
-- ============================================================================

CREATE TABLE properties (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users(id), -- Chủ nhà (Client)
    listing_agent_id UUID REFERENCES users(id),  -- Agent phụ trách nguồn
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    property_type VARCHAR(50) NOT NULL, -- APARTMENT, HOUSE, VILLA, OFFICE, STUDIO
    rental_operation_mode VARCHAR(30) NOT NULL, -- SHORT_TERM_ONLY, LONG_TERM_ONLY, HYBRID
    address_line TEXT NOT NULL,
    ward VARCHAR(100),
    district VARCHAR(100) NOT NULL,
    city_province VARCHAR(100) NOT NULL,
    location GEOGRAPHY(POINT, 4326), -- Tọa độ PostGIS (Longitude, Latitude)
    bedrooms INT NOT NULL DEFAULT 1,
    bathrooms INT NOT NULL DEFAULT 1,
    area_sqm NUMERIC(8, 2) NOT NULL, -- Diện tích m2
    floor_number INT,
    description TEXT,
    house_rules TEXT,
    furnishing_status VARCHAR(50) DEFAULT 'FULLY_FURNISHED', -- UNFURNISHED, BASIC, FULLY_FURNISHED
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL', -- DRAFT, PENDING_APPROVAL, AVAILABLE, RENTED, MAINTENANCE, INACTIVE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0, -- số lần sai mật khẩu liên tiếp (khóa tạm lũy tiến)
    locked_until TIMESTAMP WITH TIME ZONE              -- từ chối đăng nhập bằng mật khẩu tới thời điểm này
);

CREATE INDEX idx_properties_location ON properties USING GIST (location);
CREATE INDEX idx_properties_filter ON properties (city_province, district, property_type, rental_operation_mode, status);

CREATE TABLE property_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    caption VARCHAR(255),
    display_order INT DEFAULT 0,
    is_cover BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE property_amenities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL, -- INDOOR, OUTDOOR, BUILDING, SAFETY
    icon_name VARCHAR(50)
);

CREATE TABLE property_amenity_mapping (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    amenity_id INT NOT NULL REFERENCES property_amenities(id) ON DELETE CASCADE,
    PRIMARY KEY (property_id, amenity_id)
);

-- ============================================================================
-- PHÂN HỆ 3: CẤU HÌNH & GIAO DỊCH CHO THUÊ NGẮN HẠN (SHORT-TERM RENTAL)
-- ============================================================================

CREATE TABLE short_term_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id UUID NOT NULL UNIQUE REFERENCES properties(id) ON DELETE CASCADE,
    base_night_price NUMERIC(12, 2) NOT NULL, -- Giá ngày trong tuần (T2 - T5)
    weekend_night_price NUMERIC(12, 2),       -- Giá cuối tuần (T6 - CN)
    cleaning_fee NUMERIC(12, 2) DEFAULT 0,    -- Phí vệ sinh dọn phòng
    security_deposit NUMERIC(12, 2) DEFAULT 0,-- Cọc hư hại
    standard_guest_count INT NOT NULL DEFAULT 2,
    max_guest_count INT NOT NULL DEFAULT 4,
    extra_guest_fee NUMERIC(12, 2) DEFAULT 0, -- Phụ thu khách thêm
    checkin_time_start TIME DEFAULT '14:00:00',
    checkout_time_end TIME DEFAULT '12:00:00',
    booking_policy VARCHAR(30) NOT NULL DEFAULT 'INSTANT_BOOKING', -- INSTANT_BOOKING, HOST_APPROVAL
    cancellation_policy VARCHAR(30) NOT NULL DEFAULT 'MODERATE',   -- FLEXIBLE, MODERATE, STRICT
    smart_lock_pin_code VARCHAR(50),
    checkin_instructions TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE price_calendars (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    calendar_date DATE NOT NULL,
    custom_price NUMERIC(12, 2), -- Nếu NULL thì lấy base_night_price hoặc weekend_night_price
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, HELD, BOOKED, BLOCKED
    held_until TIMESTAMP WITH TIME ZONE, -- Dùng cho Distributed Lock TTL (15 phút)
    min_stay_nights INT DEFAULT 1,
    note VARCHAR(255),
    UNIQUE(property_id, calendar_date)
);

CREATE INDEX idx_calendar_lookup ON price_calendars (property_id, calendar_date, status);

CREATE TABLE short_term_bookings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_code VARCHAR(30) NOT NULL UNIQUE, -- Mã đặt phòng: STB-20261007-XXXX
    property_id UUID NOT NULL REFERENCES properties(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    total_nights INT NOT NULL,
    guest_count INT NOT NULL,
    nightly_total_amount NUMERIC(12, 2) NOT NULL,
    cleaning_fee_amount NUMERIC(12, 2) DEFAULT 0,
    service_fee_platform NUMERIC(12, 2) NOT NULL, -- Phí hoa hồng sàn (VD: 12%)
    security_deposit_amount NUMERIC(12, 2) DEFAULT 0,
    total_paid_by_customer NUMERIC(12, 2) NOT NULL,
    host_payout_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    -- Trạng thái: PENDING_APPROVAL, PENDING_PAYMENT, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, REFUNDED
    payment_method VARCHAR(50),
    payment_transaction_id VARCHAR(100),
    escrow_released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- PHÂN HỆ 4: CẤU HÌNH & GIAO DỊCH CHO THUÊ DÀI HẠN (LONG-TERM RENTAL)
-- ============================================================================

CREATE TABLE long_term_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id UUID NOT NULL UNIQUE REFERENCES properties(id) ON DELETE CASCADE,
    monthly_rent_price NUMERIC(12, 2) NOT NULL,
    deposit_months_required INT NOT NULL DEFAULT 2, -- Số tháng cọc bắt buộc
    min_lease_months INT NOT NULL DEFAULT 12,        -- Thời hạn thuê tối thiểu (tháng)
    payment_cycle_months INT NOT NULL DEFAULT 1,     -- Thanh toán định kỳ (1 tháng/lần, 3 tháng/lần)
    electricity_rate_type VARCHAR(30) DEFAULT 'STATE_RATE', -- STATE_RATE (giá nhà nước) hoặc FIXED_PER_KWH (giá cố định)
    electricity_unit_price NUMERIC(10, 2),
    water_unit_price NUMERIC(10, 2),
    building_management_fee NUMERIC(12, 2) DEFAULT 0,
    motorcycle_parking_fee NUMERIC(10, 2) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE viewing_tours (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id UUID NOT NULL REFERENCES properties(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    assigned_agent_id UUID NOT NULL REFERENCES users(id),
    scheduled_datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    customer_notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED', 
    -- Trạng thái: REQUESTED, CONFIRMED, AGENT_CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW
    agent_checkin_latitude NUMERIC(10, 7),
    agent_checkin_longitude NUMERIC(10, 7),
    agent_checkin_time TIMESTAMP WITH TIME ZONE,
    agent_feedback_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lease_agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_number VARCHAR(50) NOT NULL UNIQUE, -- HĐ-2026-XXXX
    property_id UUID NOT NULL REFERENCES properties(id),
    landlord_id UUID NOT NULL REFERENCES users(id),
    tenant_id UUID NOT NULL REFERENCES users(id),
    broker_agent_id UUID NOT NULL REFERENCES users(id), -- Agent chốt deal
    listing_agent_id UUID NOT NULL REFERENCES users(id),-- Agent nguồn BĐS
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_months INT NOT NULL,
    monthly_rent NUMERIC(12, 2) NOT NULL,
    security_deposit NUMERIC(12, 2) NOT NULL,
    payment_day_of_month INT NOT NULL DEFAULT 5, -- Ngày đóng tiền thuê hàng tháng
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT, AWAITING_TENANT_SIGN, AWAITING_LANDLORD_SIGN, SIGNED, ACTIVE, EXPIRED, TERMINATED
    pdf_contract_url TEXT,
    tenant_signed_at TIMESTAMP WITH TIME ZONE,
    landlord_signed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lease_invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_code VARCHAR(50) NOT NULL UNIQUE,
    lease_id UUID NOT NULL REFERENCES lease_agreements(id),
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    rent_amount NUMERIC(12, 2) NOT NULL,
    electricity_amount NUMERIC(12, 2) DEFAULT 0,
    water_amount NUMERIC(12, 2) DEFAULT 0,
    management_fee NUMERIC(12, 2) DEFAULT 0,
    total_amount NUMERIC(12, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNPAID', -- UNPAID, PAID, OVERDUE, CANCELLED
    paid_at TIMESTAMP WITH TIME ZONE,
    payment_reference VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- PHÂN HỆ 5: CRM MÔI GIỚI & HOA HỒNG (CRM & COMMISSIONS)
-- ============================================================================

CREATE TABLE customer_leads (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lead_code VARCHAR(30) NOT NULL UNIQUE,
    customer_id UUID REFERENCES users(id),
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    target_property_id UUID REFERENCES properties(id),
    assigned_agent_id UUID REFERENCES users(id),
    rental_type VARCHAR(30) NOT NULL, -- SHORT_TERM, LONG_TERM
    budget_max NUMERIC(12, 2),
    move_in_date_target DATE,
    pipeline_stage VARCHAR(30) NOT NULL DEFAULT 'NEW',
    -- NEW, CONTACTED, TOUR_SCHEDULED, TOUR_COMPLETED, IN_NEGOTIATION, UNDER_CONTRACT, CLOSED_WON, CLOSED_LOST
    loss_reason VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE deal_commissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    commission_code VARCHAR(30) NOT NULL UNIQUE,
    lease_id UUID REFERENCES lease_agreements(id),
    short_term_booking_id UUID REFERENCES short_term_bookings(id),
    deal_type VARCHAR(30) NOT NULL, -- LONG_TERM_LEASE, SHORT_TERM_BOOKING
    gross_deal_value NUMERIC(15, 2) NOT NULL,
    total_commission_collected NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SETTLEMENT', -- PENDING_SETTLEMENT, APPROVED, PAID_OUT
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE commission_split_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    deal_commission_id UUID NOT NULL REFERENCES deal_commissions(id) ON DELETE CASCADE,
    recipient_user_id UUID REFERENCES users(id), -- NULL nếu là phần của Công ty (Company Pool)
    recipient_role VARCHAR(50) NOT NULL, -- COMPANY_POOL, CLOSING_AGENT, LISTING_AGENT, TEAM_LEADER
    split_percentage NUMERIC(5, 2) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    personal_income_tax_deducted NUMERIC(12, 2) DEFAULT 0,
    net_payout_amount NUMERIC(15, 2) NOT NULL,
    payout_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID', -- UNPAID, SCHEDULED, PAID
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payout_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payout_code VARCHAR(50) NOT NULL UNIQUE,
    recipient_user_id UUID NOT NULL REFERENCES users(id),
    payout_type VARCHAR(30) NOT NULL, -- AGENT_COMMISSION, LANDLORD_RENTAL_PROCEEDS
    total_amount NUMERIC(15, 2) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50) NOT NULL,
    bank_account_name VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING', -- PROCESSING, SUCCESS, FAILED
    processed_by_admin_id UUID REFERENCES users(id),
    bank_transfer_reference VARCHAR(100),
    transferred_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```
