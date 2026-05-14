-- FD Shield — Neon PostgreSQL Schema
-- Run this once on your Neon database before starting the services

CREATE TABLE IF NOT EXISTS users (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL UNIQUE,
    monthly_income   NUMERIC(15, 2) NOT NULL,
    monthly_expenses NUMERIC(15, 2) NOT NULL,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fixed_deposits (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    principal        NUMERIC(15, 2) NOT NULL,
    interest_rate    NUMERIC(5, 2) NOT NULL,
    duration_months  INT NOT NULL,
    maturity_amount  NUMERIC(15, 2),
    start_date       DATE NOT NULL,
    maturity_date    DATE,
    fd_type          VARCHAR(20) NOT NULL CHECK (fd_type IN ('SHORT_TERM', 'LONG_TERM')),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MATURED', 'WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS withdrawal_logs (
    id               BIGSERIAL PRIMARY KEY,
    fd_id            BIGINT NOT NULL REFERENCES fixed_deposits(id),
    withdrawal_type  VARCHAR(20) NOT NULL CHECK (withdrawal_type IN ('PREMATURE', 'MATURITY')),
    penalty_amount   NUMERIC(15, 2) DEFAULT 0,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_financial_profile (
    user_id               BIGINT PRIMARY KEY REFERENCES users(id),
    persona               VARCHAR(100),
    liquidity_score       NUMERIC(5, 2),
    maturity_spread_score NUMERIC(5, 2),
    penalty_risk          NUMERIC(5, 2),
    portfolio_health_score NUMERIC(5, 2),
    recommendation        TEXT,
    updated_at            TIMESTAMP DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_fd_user_id     ON fixed_deposits(user_id);
CREATE INDEX IF NOT EXISTS idx_fd_status      ON fixed_deposits(status);
CREATE INDEX IF NOT EXISTS idx_wlog_fd_id     ON withdrawal_logs(fd_id);

ALTER TABLE user_financial_profile
    ADD COLUMN IF NOT EXISTS concentration_risk   NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS ladder_score         NUMERIC(5, 2);