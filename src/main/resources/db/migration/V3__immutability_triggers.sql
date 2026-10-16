-- V3__immutability_triggers.sql
-- Prevent UPDATE/DELETE on ledger tables, except allowed status transition for ledger_transactions

-- Function to block modifications on ledger_entries
CREATE OR REPLACE FUNCTION block_ledger_entries_modifications()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries are immutable';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_ledger_entries_update
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION block_ledger_entries_modifications();

-- Function to block modifications on ledger_transactions, except allowed status change
CREATE OR REPLACE FUNCTION block_ledger_transactions_modifications()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- Allow only status change from PENDING to POSTED or FAILED (and posted_at can be set)
        IF OLD.status = 'PENDING' AND NEW.status IN ('POSTED','FAILED') THEN
            RETURN NEW; -- permit this specific transition
        END IF;
    END IF;
    RAISE EXCEPTION 'ledger_transactions are immutable except allowed status transition from PENDING to POSTED or FAILED';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_ledger_transactions_update
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW EXECUTE FUNCTION block_ledger_transactions_modifications();
