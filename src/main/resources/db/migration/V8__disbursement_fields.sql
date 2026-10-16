-- V8__disbursement_fields.sql
-- Add additional columns to disbursements for B2C functionality

ALTER TABLE disbursements
    ADD COLUMN from_account_id UUID,
    ADD COLUMN msisdn VARCHAR(20),
    ADD COLUMN remarks VARCHAR(255),
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN receipt VARCHAR(50);

-- Add foreign key constraint for from_account_id (optional, will be enforced if not null)
ALTER TABLE disbursements
    ADD CONSTRAINT fk_disbursement_from_account FOREIGN KEY (from_account_id) REFERENCES accounts(id);
