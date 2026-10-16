-- V5__seed.sql
-- Seed system accounts and a demo admin user

-- Ensure the pgcrypto extension is available for password hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, username, password_hash, role, created_at, version)
VALUES (
    gen_random_uuid(),
    'admin',
    '',
    'ADMIN',
    now(),
    0
);

-- Insert system accounts linked to the admin user
INSERT INTO accounts (id, user_id, account_number, balance, status, version, created_at)
SELECT
    gen_random_uuid(),
    u.id,
    acct.account_number,
    0,
    'ACTIVE',
    0,
    now()
FROM (
    SELECT 'SETTLEMENT' AS account_number UNION ALL
    SELECT 'FEE' UNION ALL
    SELECT 'SUSPENSE'
) acct,
    (SELECT id FROM users WHERE username = 'admin') u;
