#!/usr/bin/env bash

set -euo pipefail

BASE_URL="http://localhost:8080/api/v1"

echo "Logging in as admin..."
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

echo "Token: $TOKEN"

function auth_header() {
  echo "Authorization: Bearer $TOKEN"
}

# Create two accounts for admin user (we will fetch user id via /api/v1/auth/me endpoint)
echo "Fetching current user id..."
USER_ID=$(curl -s -H "$(auth_header)" "$BASE_URL/auth/me" | jq -r '.userId')

echo "Creating account 1..."
ACC1=$(curl -s -X POST "$BASE_URL/accounts" -H "$(auth_header)" -H "Content-Type: application/json" -d "{\"userId\": \"$USER_ID\"}")
ACC1_ID=$(echo "$ACC1" | jq -r '.id')

echo "Account 1 ID: $ACC1_ID"

echo "Creating account 2..."
ACC2=$(curl -s -X POST "$BASE_URL/accounts" -H "$(auth_header)" -H "Content-Type: application/json" -d "{\"userId\": \"$USER_ID\"}")
ACC2_ID=$(echo "$ACC2" | jq -r '.id')

echo "Account 2 ID: $ACC2_ID"

# Deposit into account1 via ledger post (debit negative, credit positive) – we just credit the account
IDEMPOTENCY_KEY=$(uuidgen)
echo "Depositing 1000 into account 1..."
POSTING=$(cat <<EOF
{
  "entries": [
    {"accountId":"$ACC1_ID","amount":1000,"entryType":"CREDIT"}
  ]
}
EOF
)
curl -s -X POST "$BASE_URL/ledger/post" -H "$(auth_header)" -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H "Content-Type: application/json" -d "$POSTING"

# Transfer from account1 to account2
IDEMPOTENCY_KEY=$(uuidgen)
echo "Transferring 200 from account 1 to account 2..."
TRANSFER=$(cat <<EOF
{
  "fromAccountId":"$ACC1_ID",
  "toAccountId":"$ACC2_ID",
  "amount":200,
  "description":"demo transfer"
}
EOF
)
curl -s -X POST "$BASE_URL/transfers" -H "$(auth_header)" -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H "Content-Type: application/json" -d "$TRANSFER"

# Repeat same Idempotency-Key to show no double spend
echo "Repeating same transfer with same Idempotency-Key (should be idempotent)..."
curl -s -X POST "$BASE_URL/transfers" -H "$(auth_header)" -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H "Content-Type: application/json" -d "$TRANSFER"

# Disburse to an msisdn
IDEMPOTENCY_KEY=$(uuidgen)
echo "Creating disbursement..."
DISBURSE=$(cat <<EOF
{
  "fromAccountId":"$ACC1_ID",
  "msisdn":"+254700000001",
  "amount":150,
  "remarks":"demo disbursement"
}
EOF
)
curl -s -X POST "$BASE_URL/disbursements" -H "$(auth_header)" -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H "Content-Type: application/json" -d "$DISBURSE"

# Fetch statement for account1
echo "Fetching statement for account 1..."
curl -s -X GET "$BASE_URL/accounts/$ACC1_ID/statement" -H "$(auth_header)"

# Reconciliation
echo "Running reconciliation..."
curl -s -X GET "$BASE_URL/ledger/reconciliation" -H "$(auth_header)"

echo "Demo script completed."
