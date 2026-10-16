-- V7__transaction_flags.sql
-- Add reversal flags to ledger_transactions

ALTER TABLE ledger_transactions ADD COLUMN reversed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ledger_transactions ADD COLUMN reversal BOOLEAN NOT NULL DEFAULT FALSE;
