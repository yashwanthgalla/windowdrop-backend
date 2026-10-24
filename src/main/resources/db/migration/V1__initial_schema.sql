-- DropCode V1 Initial Migration
-- Compatible with MySQL 8.0+

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) NULL,
    display_name VARCHAR(255) NULL,
    username VARCHAR(64) NULL UNIQUE,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    INDEX idx_users_firebase_uid (firebase_uid),
    INDEX idx_users_email (email),
    INDEX idx_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL UNIQUE,
    plan_name VARCHAR(64) NOT NULL,
    price_paise BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    billing_interval_days INT NOT NULL DEFAULT 365,
    max_file_size_bytes BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert Free, Premium, and Pro Plans (with frontend compatibility aliases)
INSERT INTO subscription_plans (plan_code, plan_name, price_paise, currency, billing_interval_days, max_file_size_bytes, is_active)
VALUES
('free', 'Free Plan', 0, 'INR', 365, 500000000, TRUE),
('premium', 'Premium Plan', 4900, 'INR', 365, 5000000000, TRUE),
('standard', 'Standard Plan', 4900, 'INR', 365, 5000000000, TRUE),
('pro', 'Pro Plan', 7900, 'INR', 365, 10000000000, TRUE),
('large', 'Large Plan', 7900, 'INR', 365, 10000000000, TRUE)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    subscription_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    INDEX idx_subscriptions_user_status (user_id, subscription_status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS file_transfers (
    id VARCHAR(64) PRIMARY KEY,
    transfer_code_hash VARCHAR(128) NULL UNIQUE,
    code_salt VARCHAR(64) NULL,
    code_preview VARCHAR(8) NULL,
    sender_user_id BIGINT NULL,
    sender_display_name VARCHAR(255) NULL,
    original_filename VARCHAR(512) NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(128) NULL,
    has_folder BOOLEAN NOT NULL DEFAULT FALSE,
    transfer_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    storage_path VARCHAR(512) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_transfers_sender FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_transfers_code_hash (transfer_code_hash),
    INDEX idx_transfers_sender (sender_user_id),
    INDEX idx_transfers_status (transfer_status),
    INDEX idx_transfers_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transfer_items (
    id VARCHAR(64) PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL,
    item_index INT NOT NULL,
    filename VARCHAR(512) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128) NULL,
    is_downloaded BOOLEAN NOT NULL DEFAULT FALSE,
    storage_key VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_items_transfer FOREIGN KEY (transfer_id) REFERENCES file_transfers (id) ON DELETE CASCADE,
    INDEX idx_items_transfer_idx (transfer_id, item_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transfer_recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL,
    receiver_user_id BIGINT NULL,
    receiver_email VARCHAR(255) NULL,
    receiver_display_name VARCHAR(255) NULL,
    recipient_status VARCHAR(32) NOT NULL DEFAULT 'CLAIMED',
    download_token_hash VARCHAR(128) NULL,
    download_token_expires_at TIMESTAMP NULL,
    first_accessed_at TIMESTAMP NULL,
    downloaded_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipients_transfer FOREIGN KEY (transfer_id) REFERENCES file_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipients_user FOREIGN KEY (receiver_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_recipients_transfer (transfer_id),
    INDEX idx_recipients_user (receiver_user_id),
    INDEX idx_recipients_token_hash (download_token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transfer_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_status VARCHAR(32) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sanitized_metadata TEXT NULL,
    CONSTRAINT fk_events_transfer FOREIGN KEY (transfer_id) REFERENCES file_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_events_transfer (transfer_id),
    INDEX idx_events_type_time (event_type, event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS access_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    transfer_id VARCHAR(64) NULL,
    event_type VARCHAR(64) NOT NULL,
    encrypted_ip_address VARCHAR(255) NOT NULL,
    ip_key_version INT NOT NULL DEFAULT 1,
    user_agent VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_transfer FOREIGN KEY (transfer_id) REFERENCES file_transfers (id) ON DELETE SET NULL,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_transfer (transfer_id),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_retention (retention_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT NULL,
    plan_code VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'RAZORPAY',
    provider_order_id VARCHAR(128) NOT NULL UNIQUE,
    provider_payment_id VARCHAR(128) NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    payment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    provider_signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE SET NULL,
    INDEX idx_payments_user (user_id),
    INDEX idx_payments_order (provider_order_id),
    INDEX idx_payments_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS upload_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL,
    storage_upload_id VARCHAR(128) NOT NULL UNIQUE,
    expected_size_bytes BIGINT NOT NULL,
    upload_status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_upload_sessions_transfer FOREIGN KEY (transfer_id) REFERENCES file_transfers (id) ON DELETE CASCADE,
    INDEX idx_upload_sessions_transfer (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
