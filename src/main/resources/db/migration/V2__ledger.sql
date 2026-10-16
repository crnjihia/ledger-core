-- V2__ledger.sql
-- Ledger transactions and entries

CREATE TYPE transaction_status AS ENUM ('PENDING', 'POSTED', 'FAILED');

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_number VARCHAR(50) NOT NULL UNIQUE,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    posted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

-- Indexes for performance
CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_entries_tx ON ledger_entries (transaction_id);
