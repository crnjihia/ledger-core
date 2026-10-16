-- V9__complete_banking_domain.sql
-- Complete banking domain models: Account fields, LedgerEntry fields, Transaction fields, AuditLog table, trigger updates

-- 1. Accounts updates: customer_id, type, currency, allow FROZEN status
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'SAVINGS';
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'KES';

UPDATE accounts SET customer_id = user_id WHERE customer_id IS NULL;

ALTER TYPE account_status ADD VALUE IF NOT EXISTS 'FROZEN';
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'TELLER';
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'AUDITOR';

-- Disbursements updates: add ledger_transaction_id
ALTER TABLE disbursements ADD COLUMN IF NOT EXISTS ledger_transaction_id UUID REFERENCES ledger_transactions(id);

-- Transfers table
CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id UUID NOT NULL REFERENCES accounts(id),
    to_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    description VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfers_from_account ON transfers (from_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to_account ON transfers (to_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_ledger_tx ON transfers (ledger_transaction_id);

-- 2. Ledger transactions updates: reference, type, amount, currency, debit_account_id, credit_account_id, idempotency_key, description
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS reference VARCHAR(100);
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS type VARCHAR(30) NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS amount NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'KES';
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS debit_account_id UUID REFERENCES accounts(id);
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS credit_account_id UUID REFERENCES accounts(id);
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS description TEXT;

UPDATE ledger_transactions SET reference = transaction_number WHERE reference IS NULL;

ALTER TYPE transaction_status ADD VALUE IF NOT EXISTS 'REVERSED';

CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_transactions_reference ON ledger_transactions(reference);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_transactions_idem_key ON ledger_transactions(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 3. Ledger entries updates: running_balance, currency
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS running_balance NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'KES';

-- 4. Audit logs table (full structured audit trail)
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    before_state TEXT,
    after_state TEXT,
    ip_address VARCHAR(50),
    user_agent TEXT,
    correlation_id VARCHAR(100),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs (timestamp);

-- 5. Update Idempotency records table to support string key, request_hash, and expires_at
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS response_body TEXT;
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS status_code INT;
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'idempotency_records' AND column_name = 'idem_key') THEN
        ALTER TABLE idempotency_records ALTER COLUMN idem_key DROP NOT NULL;
        UPDATE idempotency_records SET idempotency_key = idem_key WHERE idempotency_key IS NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_idempotency_records_key') THEN
        ALTER TABLE idempotency_records ADD CONSTRAINT uq_idempotency_records_key UNIQUE (idempotency_key);
    END IF;
END $$;

-- 6. Update immutability trigger for ledger_transactions to allow transition to REVERSED and setting reversed flag
CREATE OR REPLACE FUNCTION block_ledger_transactions_modifications()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- Allow status change from PENDING to POSTED or FAILED
        IF OLD.status = 'PENDING' AND NEW.status IN ('POSTED','FAILED') THEN
            RETURN NEW;
        END IF;
        -- Allow reversal: status transition to REVERSED or setting reversed = TRUE
        IF OLD.status = 'POSTED' AND (NEW.status = 'REVERSED' OR NEW.reversed = TRUE) THEN
            RETURN NEW;
        END IF;
        IF OLD.reversed = FALSE AND NEW.reversed = TRUE THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'ledger_transactions are immutable except allowed status transitions (PENDING -> POSTED/FAILED, POSTED -> REVERSED)';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
