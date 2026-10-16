-- V6__entry_type.sql
-- Add entry_type column to ledger_entries

ALTER TABLE ledger_entries ADD COLUMN entry_type VARCHAR(10) NOT NULL;
