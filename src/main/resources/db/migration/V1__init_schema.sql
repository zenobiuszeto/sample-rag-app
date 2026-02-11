-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Core Banking Tables
-- ============================================================

CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(20) UNIQUE NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone           VARCHAR(20),
    date_of_birth   DATE,
    ssn_last4       VARCHAR(4),
    address_line1   VARCHAR(255),
    address_city    VARCHAR(100),
    address_state   VARCHAR(2),
    address_zip     VARCHAR(10),
    credit_score    INTEGER,
    customer_since  DATE NOT NULL,
    segment         VARCHAR(50),   -- RETAIL, PREMIUM, PRIVATE_BANKING
    risk_rating     VARCHAR(20),   -- LOW, MEDIUM, HIGH
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(20) UNIQUE NOT NULL,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    account_type    VARCHAR(50) NOT NULL,  -- CHECKING, SAVINGS, CREDIT_CARD, MORTGAGE, LOAN
    account_name    VARCHAR(100),
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) DEFAULT 'USD',
    interest_rate   DECIMAL(5,4),
    credit_limit    DECIMAL(15,2),
    status          VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, FROZEN, CLOSED
    opened_date     DATE NOT NULL,
    closed_date     DATE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transactions (
    id                  BIGSERIAL PRIMARY KEY,
    transaction_id      VARCHAR(36) UNIQUE NOT NULL,
    account_id          BIGINT NOT NULL REFERENCES accounts(id),
    transaction_type    VARCHAR(30) NOT NULL,  -- DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT, FEE, INTEREST
    amount              DECIMAL(15,2) NOT NULL,
    balance_after       DECIMAL(15,2),
    description         VARCHAR(500),
    merchant_name       VARCHAR(200),
    merchant_category   VARCHAR(100),
    channel             VARCHAR(50),   -- ONLINE, BRANCH, ATM, MOBILE, POS
    status              VARCHAR(20) DEFAULT 'COMPLETED',
    reference_number    VARCHAR(50),
    transaction_date    TIMESTAMP NOT NULL,
    posted_date         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- RAG Vector Store Tables
-- ============================================================

-- Stores document chunks with their vector embeddings
CREATE TABLE document_embeddings (
    id              BIGSERIAL PRIMARY KEY,
    content         TEXT NOT NULL,
    metadata        JSONB DEFAULT '{}',
    source_type     VARCHAR(50) NOT NULL,  -- CUSTOMER_PROFILE, ACCOUNT_SUMMARY, TRANSACTION_PATTERN, POLICY
    source_id       VARCHAR(100),
    embedding       vector(384),  -- pgvector column, 384 dimensions
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Stores conversation history for context
CREATE TABLE chat_history (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(36) NOT NULL,
    role            VARCHAR(20) NOT NULL,  -- USER, ASSISTANT, SYSTEM
    content         TEXT NOT NULL,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================

-- B-tree indexes for lookups
CREATE INDEX idx_customers_segment ON customers(segment);
CREATE INDEX idx_customers_risk ON customers(risk_rating);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE INDEX idx_accounts_type ON accounts(account_type);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_merchant_cat ON transactions(merchant_category);
CREATE INDEX idx_chat_session ON chat_history(session_id);

-- IVFFlat index for vector similarity search (created after data load)
-- We'll create this after inserting embeddings for better index quality
CREATE INDEX idx_embeddings_source ON document_embeddings(source_type);

-- GIN index on JSONB metadata
CREATE INDEX idx_embeddings_metadata ON document_embeddings USING gin(metadata);
