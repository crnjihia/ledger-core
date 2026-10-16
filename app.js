// State Management for Ledger Core Sandbox
const state = {
  accounts: {
    'acc-1': { id: 'acc-1', number: 'ACC-2026-001', name: 'John Doe', type: 'SAVINGS', balance: 150000.00, currency: 'KES' },
    'acc-2': { id: 'acc-2', number: 'ACC-2026-002', name: 'Alice Wangari', type: 'CURRENT', balance: 45000.00, currency: 'KES' },
    'acc-suspense': { id: 'acc-suspense', number: 'SETTLEMENT', name: 'M-Pesa Clearing', type: 'SUSPENSE', balance: 0.00, currency: 'KES' }
  },
  ledgerEntries: [
    { id: 'le-init-1', txnRef: 'TXN-GENESIS-01', accNumber: 'ACC-2026-001', type: 'CREDIT', amount: 150000.00, runningBalance: 150000.00, timestamp: '10:00:00' },
    { id: 'le-init-2', txnRef: 'TXN-GENESIS-01', accNumber: 'SETTLEMENT', type: 'DEBIT', amount: 150000.00, runningBalance: -150000.00, timestamp: '10:00:00' },
    { id: 'le-init-3', txnRef: 'TXN-GENESIS-02', accNumber: 'ACC-2026-002', type: 'CREDIT', amount: 45000.00, runningBalance: 45000.00, timestamp: '10:05:00' },
    { id: 'le-init-4', txnRef: 'TXN-GENESIS-02', accNumber: 'SETTLEMENT', type: 'DEBIT', amount: 45000.00, runningBalance: -195000.00, timestamp: '10:05:00' }
  ],
  idempotencyStore: new Map(), // key -> { hash, response, statusCode, timestamp }
  rateLimiter: {
    transfersRemaining: 10,
    resetTime: Date.now() + 60000
  },
  metrics: {
    totalTransactions: 2,
    totalVolumeKES: 195000.00
  }
};

// Utilities
function formatCurrency(val) {
  return new Intl.NumberFormat('en-KE', { style: 'currency', currency: 'KES', minimumFractionDigits: 2 }).format(val);
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function showToast(message, type = 'success') {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="pulse-dot" style="background:${type==='success'?'#10b981':type==='error'?'#f43f5e':'#06b6d4'};"></span>
    <div>${message}</div>
  `;
  container.appendChild(toast);
  setTimeout(() => toast.classList.add('show'), 10);
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 350);
  }, 4000);
}

// Render Account Cards
function renderAccounts() {
  const container = document.getElementById('accountCardsContainer');
  if (!container) return;
  container.innerHTML = Object.values(state.accounts).map(acc => `
    <div class="acc-card" id="card-${acc.id}">
      <div class="acc-num">${acc.number}</div>
      <div class="acc-name">${acc.name}</div>
      <div class="acc-bal" id="bal-${acc.id}">${formatCurrency(acc.balance)}</div>
      <span class="acc-badge">${acc.type}</span>
    </div>
  `).join('');
}

// Render Ledger Table
function renderLedger() {
  const tbody = document.getElementById('ledgerTableBody');
  if (!tbody) return;
  const reversed = [...state.ledgerEntries].reverse().slice(0, 10);
  tbody.innerHTML = reversed.map(le => `
    <tr>
      <td style="font-family:var(--font-mono);font-size:0.8rem;color:var(--text-muted);">${le.timestamp}</td>
      <td style="font-family:var(--font-mono);font-weight:600;">${le.txnRef}</td>
      <td style="font-family:var(--font-mono);">${le.accNumber}</td>
      <td><span class="${le.type === 'DEBIT' ? 'pill-debit' : 'pill-credit'}">${le.type}</span></td>
      <td style="font-family:var(--font-mono);font-weight:700;">${formatCurrency(le.amount)}</td>
      <td style="font-family:var(--font-mono);color:var(--emerald-400);">${formatCurrency(le.runningBalance)}</td>
    </tr>
  `).join('');
}

// Update Reconciliation Metric
function updateReconciliation() {
  const credits = state.ledgerEntries
    .filter(e => e.type === 'CREDIT')
    .reduce((sum, e) => sum + e.amount, 0);
  const debits = state.ledgerEntries
    .filter(e => e.type === 'DEBIT')
    .reduce((sum, e) => sum + e.amount, 0);
  const diff = Math.abs(credits - debits);

  const mathEl = document.getElementById('reconcileMath');
  const statusEl = document.getElementById('reconcileStatus');
  if (mathEl) {
    mathEl.textContent = `∑ Credits (${formatCurrency(credits)}) - ∑ Debits (${formatCurrency(debits)}) = ${formatCurrency(diff)}`;
  }
  if (statusEl) {
    if (diff === 0) {
      statusEl.innerHTML = `✓ Zero-Sum Invariant Holds (Δ = 0.00)`;
      statusEl.style.color = 'var(--emerald-400)';
    } else {
      statusEl.innerHTML = `⚠ Discrepancy Detected (Δ = ${formatCurrency(diff)})`;
      statusEl.style.color = 'var(--rose-500)';
    }
  }

  // Update live counters in hero
  const volumeEl = document.getElementById('heroVolume');
  const countEl = document.getElementById('heroCount');
  if (volumeEl) volumeEl.textContent = formatCurrency(state.metrics.totalVolumeKES);
  if (countEl) countEl.textContent = state.metrics.totalTransactions;
}

// Step Visualizer helper
function setStep(stepIndex, status) {
  const steps = document.querySelectorAll('#pipelineSteps .step-item');
  if (stepIndex < steps.length) {
    const el = steps[stepIndex];
    el.className = `step-item ${status}`;
  }
}

function resetPipeline() {
  const steps = document.querySelectorAll('#pipelineSteps .step-item');
  steps.forEach(s => s.className = 'step-item');
}

// 1. RTGS Transfer Execution Simulation
async function executeTransfer() {
  const fromSelect = document.getElementById('xferFrom');
  const toSelect = document.getElementById('xferTo');
  const amountInput = document.getElementById('xferAmount');
  const keyInput = document.getElementById('xferIdempKey');

  const fromId = fromSelect.value;
  const toId = toSelect.value;
  const amount = parseFloat(amountInput.value);
  const idempotencyKey = keyInput.value.trim() || generateUUID();

  if (fromId === toId) {
    showToast('Source and Destination accounts must be different', 'error');
    return;
  }
  if (isNaN(amount) || amount <= 0) {
    showToast('Please enter a valid transfer amount', 'error');
    return;
  }

  resetPipeline();
  const btn = document.getElementById('btnExecuteXfer');
  btn.disabled = true;
  btn.innerHTML = `<span class="pulse-dot"></span> Processing RTGS Settlement...`;

  // Step 1: Rate Limiter (Bucket4j)
  setStep(0, 'active');
  await new Promise(r => setTimeout(r, 200));
  if (state.rateLimiter.transfersRemaining <= 0) {
    setStep(0, 'failed');
    showToast('HTTP 429: Rate limit exceeded (10 transfers/min)', 'error');
    btn.disabled = false;
    btn.innerHTML = `Execute RTGS Transfer`;
    return;
  }
  state.rateLimiter.transfersRemaining--;
  setStep(0, 'success');

  // Step 2: Multi-Tier Idempotency Check
  setStep(1, 'active');
  await new Promise(r => setTimeout(r, 220));
  const payloadHash = `${fromId}->${toId}:${amount}`;

  if (state.idempotencyStore.has(idempotencyKey)) {
    const cached = state.idempotencyStore.get(idempotencyKey);
    if (cached.hash !== payloadHash) {
      setStep(1, 'failed');
      showToast(`HTTP 409 Conflict: Idempotency Key reused with different payload!`, 'error');
      btn.disabled = false;
      btn.innerHTML = `Execute RTGS Transfer`;
      return;
    } else {
      // Replay identical response
      setStep(1, 'success');
      showToast(`Fast-Path Hit: Returned cached response in 0.8ms (Zero DB hit)`, 'info');
      btn.disabled = false;
      btn.innerHTML = `Execute RTGS Transfer`;
      return;
    }
  }
  setStep(1, 'success');

  // Step 3: Deterministic Pessimistic Locking
  setStep(2, 'active');
  await new Promise(r => setTimeout(r, 240));
  const sorted = [fromId, toId].sort();
  // Highlight locked accounts
  document.getElementById(`card-${fromId}`)?.classList.add('highlight');
  document.getElementById(`card-${toId}`)?.classList.add('highlight');
  setStep(2, 'success');

  // Step 4: Balance & Invariant Verification
  setStep(3, 'active');
  await new Promise(r => setTimeout(r, 200));
  const fromAcc = state.accounts[fromId];
  const toAcc = state.accounts[toId];

  if (fromAcc.type !== 'SUSPENSE' && fromAcc.balance < amount) {
    setStep(3, 'failed');
    showToast(`Transfer Failed: Insufficient funds in ${fromAcc.name} (Balance: ${formatCurrency(fromAcc.balance)})`, 'error');
    document.querySelectorAll('.acc-card').forEach(c => c.classList.remove('highlight'));
    btn.disabled = false;
    btn.innerHTML = `Execute RTGS Transfer`;
    return;
  }
  setStep(3, 'success');

  // Step 5: Double-Entry Posting & ACID Commit
  setStep(4, 'active');
  await new Promise(r => setTimeout(r, 220));
  fromAcc.balance -= amount;
  toAcc.balance += amount;

  const txnRef = `TXN-2026-${Math.floor(1000 + Math.random() * 9000)}`;
  const nowStr = new Date().toTimeString().split(' ')[0];

  // 1 DEBIT leg
  state.ledgerEntries.push({
    id: generateUUID(),
    txnRef,
    accNumber: fromAcc.number,
    type: 'DEBIT',
    amount,
    runningBalance: fromAcc.balance,
    timestamp: nowStr
  });

  // 1 CREDIT leg
  state.ledgerEntries.push({
    id: generateUUID(),
    txnRef,
    accNumber: toAcc.number,
    type: 'CREDIT',
    amount,
    runningBalance: toAcc.balance,
    timestamp: nowStr
  });

  // Save to Idempotency Store
  state.idempotencyStore.set(idempotencyKey, {
    hash: payloadHash,
    response: { txnRef, status: 'POSTED', amount, from: fromAcc.number, to: toAcc.number },
    statusCode: 200,
    timestamp: nowStr
  });

  state.metrics.totalTransactions++;
  state.metrics.totalVolumeKES += amount;

  setStep(4, 'success');
  renderAccounts();
  renderLedger();
  updateReconciliation();

  setTimeout(() => {
    document.querySelectorAll('.acc-card').forEach(c => c.classList.remove('highlight'));
  }, 1000);

  showToast(`✓ Transfer of ${formatCurrency(amount)} settled with zero-sum invariant!`, 'success');
  btn.disabled = false;
  btn.innerHTML = `Execute RTGS Transfer`;
}

// 2. M-Pesa B2C Disbursement Simulation
async function executeDisbursement() {
  const fromSelect = document.getElementById('disbFrom');
  const phoneInput = document.getElementById('disbPhone');
  const amountInput = document.getElementById('disbAmount');
  const scenarioSelect = document.getElementById('disbScenario');

  const fromId = fromSelect.value;
  const phone = phoneInput.value.trim();
  const amount = parseFloat(amountInput.value);
  const scenario = scenarioSelect.value;

  if (!phone.startsWith('+254') && !phone.startsWith('07') && !phone.startsWith('01')) {
    showToast('Please enter a valid Kenyan MSISDN (+254...)', 'error');
    return;
  }
  if (isNaN(amount) || amount <= 0) {
    showToast('Please enter a valid disbursement amount', 'error');
    return;
  }

  const fromAcc = state.accounts[fromId];
  const suspenseAcc = state.accounts['acc-suspense'];

  if (!fromAcc) {
    showToast('Invalid source account selected', 'error');
    return;
  }

  if (fromAcc.balance < amount) {
    showToast(`Disbursement Rejected: Insufficient balance in ${fromAcc.name}`, 'error');
    return;
  }

  // 1. Move funds to SETTLEMENT suspense account
  fromAcc.balance -= amount;
  suspenseAcc.balance += amount;

  const disbRef = `MPESA-B2C-${Math.floor(100000 + Math.random() * 900000)}`;
  const nowStr = new Date().toTimeString().split(' ')[0];

  state.ledgerEntries.push({
    id: generateUUID(),
    txnRef: disbRef,
    accNumber: fromAcc.number,
    type: 'DEBIT',
    amount,
    runningBalance: fromAcc.balance,
    timestamp: nowStr
  });

  state.ledgerEntries.push({
    id: generateUUID(),
    txnRef: disbRef,
    accNumber: suspenseAcc.number,
    type: 'CREDIT',
    amount,
    runningBalance: suspenseAcc.balance,
    timestamp: nowStr
  });

  renderAccounts();
  renderLedger();
  updateReconciliation();

  const receiptBox = document.getElementById('disbReceiptBox');

  if (scenario === 'SUCCESS') {
    const receipt = 'REC' + Math.floor(100000 + Math.random() * 900000);
    showToast(`M-Pesa B2C Succeeded! Safaricom Receipt: ${receipt}`, 'success');
    if (receiptBox) {
      receiptBox.innerHTML = `
        <div style="padding:14px;background:rgba(16,185,129,0.1);border:1px solid var(--border-accent);border-radius:8px;">
          <div style="font-weight:700;color:var(--emerald-400);">Status: CONFIRMED</div>
          <div style="font-family:var(--font-mono);font-size:0.85rem;margin-top:4px;">Receipt: ${receipt} | MSISDN: ${phone} | Amount: ${formatCurrency(amount)}</div>
        </div>
      `;
    }
  } else if (scenario === 'FAILURE') {
    // Immediate Auto-Reversal
    await new Promise(r => setTimeout(r, 600));
    suspenseAcc.balance -= amount;
    fromAcc.balance += amount;

    const revRef = `REV-${disbRef}`;
    state.ledgerEntries.push({
      id: generateUUID(),
      txnRef: revRef,
      accNumber: suspenseAcc.number,
      type: 'DEBIT',
      amount,
      runningBalance: suspenseAcc.balance,
      timestamp: new Date().toTimeString().split(' ')[0]
    });
    state.ledgerEntries.push({
      id: generateUUID(),
      txnRef: revRef,
      accNumber: fromAcc.number,
      type: 'CREDIT',
      amount,
      runningBalance: fromAcc.balance,
      timestamp: new Date().toTimeString().split(' ')[0]
    });

    renderAccounts();
    renderLedger();
    updateReconciliation();

    showToast(`Provider Rejected! Auto-reversal executed, funds restored to ${fromAcc.name}`, 'error');
    if (receiptBox) {
      receiptBox.innerHTML = `
        <div style="padding:14px;background:rgba(244,63,94,0.1);border:1px solid rgba(244,63,94,0.3);border-radius:8px;">
          <div style="font-weight:700;color:var(--rose-500);">Status: FAILED -> AUTO-REVERSED</div>
          <div style="font-size:0.85rem;margin-top:4px;">Disbursement failed at Safaricom. Double-entry refund legs generated. Balance restored.</div>
        </div>
      `;
    }
  } else if (scenario === 'TIMEOUT') {
    showToast(`Gateway Timeout: Status set to SENT. Awaiting M-Pesa webhook callback...`, 'info');
    if (receiptBox) {
      receiptBox.innerHTML = `
        <div style="padding:14px;background:rgba(6,182,212,0.1);border:1px solid var(--border-cyan);border-radius:8px;">
          <div style="font-weight:700;color:var(--cyan-400);">Status: SENT (Awaiting Callback)</div>
          <div style="font-size:0.85rem;margin-top:4px;">Funds held in suspense clearing account.</div>
          <button id="btnSimulateCallback" class="btn-small" style="margin-top:10px;padding:6px 12px;background:var(--cyan-500);color:#000;">Simulate M-Pesa Callback (SUCCESS)</button>
        </div>
      `;
      document.getElementById('btnSimulateCallback')?.addEventListener('click', () => {
        showToast('Webhook Received: M-Pesa confirmed settlement REC778899', 'success');
        receiptBox.innerHTML = `
          <div style="padding:14px;background:rgba(16,185,129,0.1);border:1px solid var(--border-accent);border-radius:8px;">
            <div style="font-weight:700;color:var(--emerald-400);">Status: CONFIRMED via Callback</div>
            <div style="font-family:var(--font-mono);font-size:0.85rem;">Receipt: REC778899 | Webhook reconciled successfully.</div>
          </div>
        `;
      });
    }
  }
}

// 3. API Showcase Controller
const apiDocs = {
  'accounts': {
    title: 'Create Account',
    endpoint: 'POST /api/v1/accounts',
    desc: 'Provisions a new bank or wallet account with generated unique account number and optimistic versioning.',
    curl: `curl -X POST http://localhost:8080/api/v1/accounts \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{
    "userId": "2f51a213-d725-4f77-b862-fd6d494d25e0",
    "type": "SAVINGS",
    "currency": "KES"
  }'`,
    response: `{
  "id": "e45465ca-79dc-405b-8b29-c4919e1f400b",
  "accountNumber": "2233440952",
  "type": "SAVINGS",
  "currency": "KES",
  "balance": 0.0000,
  "status": "ACTIVE",
  "createdAt": "2026-10-16T15:30:35Z"
}`
  },
  'transfers': {
    title: 'Initiate RTGS Transfer',
    endpoint: 'POST /api/v1/transfers',
    desc: 'Executes atomic fund transfer between accounts with deterministic locking and idempotency protection.',
    curl: `curl -X POST http://localhost:8080/api/v1/transfers \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" \\
  -H "Content-Type: application/json" \\
  -d '{
    "fromAccountId": "e45465ca-79dc-405b-8b29-c4919e1f400b",
    "toAccountId": "ef91c536-7d27-4142-a3c7-69bc6785720e",
    "amount": 2500.00,
    "description": "Supplier payment"
  }'`,
    response: `{
  "id": "b7820d48-1b02-4f7e-9a46-92d30e190843",
  "fromAccountId": "e45465ca-79dc-405b-8b29-c4919e1f400b",
  "toAccountId": "ef91c536-7d27-4142-a3c7-69bc6785720e",
  "amount": 2500.00,
  "status": "COMPLETED"
}`
  },
  'disbursements': {
    title: 'M-Pesa B2C Disbursement',
    endpoint: 'POST /api/v1/disbursements',
    desc: 'Disburses mobile money funds through clearing suspense account with automatic reversal fallback.',
    curl: `curl -X POST http://localhost:8080/api/v1/disbursements \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Idempotency-Key: e1a2b3c4-d5e6-7f80-91a2-b3c4d5e6f7a8" \\
  -H "Content-Type: application/json" \\
  -d '{
    "fromAccountId": "e45465ca-79dc-405b-8b29-c4919e1f400b",
    "msisdn": "+254712345678",
    "amount": 1000.00,
    "remarks": "Dividend payout"
  }'`,
    response: `{
  "id": "9f0a1081-f409-4615-b11d-196ab1bc96de",
  "msisdn": "+254712345678",
  "amount": 1000.00,
  "status": "CONFIRMED",
  "receipt": "REC123456"
}`
  },
  'reconciliation': {
    title: 'Run Ledger Reconciliation',
    endpoint: 'GET /api/v1/ledger/reconciliation',
    desc: 'Calculates sum of debits and credits across all ledger entries and asserts mathematical zero-sum invariant.',
    curl: `curl -X GET http://localhost:8080/api/v1/ledger/reconciliation \\
  -H "Authorization: Bearer $TOKEN"`,
    response: `{}`
  }
};

function selectApiEndpoint(key) {
  document.querySelectorAll('.api-nav-item').forEach(item => {
    item.classList.toggle('active', item.dataset.api === key);
  });
  const data = apiDocs[key];
  if (!data) return;

  document.getElementById('apiDetailTitle').textContent = data.title;
  document.getElementById('apiEndpointBadge').textContent = data.endpoint;
  document.getElementById('apiDesc').textContent = data.desc;
  document.getElementById('apiCurlCode').textContent = data.curl;
  document.getElementById('apiResponseCode').textContent = data.response;
}

// Initializer
document.addEventListener('DOMContentLoaded', () => {
  renderAccounts();
  renderLedger();
  updateReconciliation();

  // Button Listeners
  document.getElementById('btnExecuteXfer')?.addEventListener('click', executeTransfer);
  document.getElementById('btnExecuteDisb')?.addEventListener('click', executeDisbursement);
  document.getElementById('btnNewKey')?.addEventListener('click', () => {
    document.getElementById('xferIdempKey').value = generateUUID();
    showToast('Generated fresh idempotency key UUID', 'info');
  });

  // Tab switching
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
      btn.classList.add('active');
      const target = document.getElementById(btn.dataset.target);
      if (target) target.style.display = 'block';
    });
  });

  // API nav switching
  document.querySelectorAll('.api-nav-item').forEach(item => {
    item.addEventListener('click', () => selectApiEndpoint(item.dataset.api));
  });

  // Copy code buttons
  document.querySelectorAll('.btn-copy').forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.dataset.copyTarget;
      const el = document.getElementById(targetId);
      if (el) {
        navigator.clipboard.writeText(el.textContent);
        btn.textContent = 'Copied!';
        setTimeout(() => btn.textContent = 'Copy', 2000);
      }
    });
  });

  // Initialize with UUID in input
  const keyInput = document.getElementById('xferIdempKey');
  if (keyInput) keyInput.value = generateUUID();
});
