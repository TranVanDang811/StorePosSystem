import { execSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const BASE_URL = process.env.K6_BASE_URL || 'http://localhost:8080/backend';
const DOCKER_TARGET_URL = process.env.K6_DOCKER_URL || 'http://host.docker.internal:8080/backend';
const USERNAME = process.env.K6_USERNAME || 'admin';
const PASSWORD = process.env.K6_PASSWORD || 'Admin12345';
const K6_DIR = path.resolve('d:/Hoc/Java/Pos-System/backend/k6');

function mysqlQuery(sql) {
  const cmd = `docker exec pos-mysql mysql -u root -proot -N -B -e "${sql.replace(/"/g, '\\"')}" backend`;
  return execSync(cmd, { encoding: 'utf8' }).trim();
}

async function fetchJson(url, opts = {}) {
  const res = await fetch(url, opts);
  const text = await res.text();
  try {
    return { status: res.status, body: JSON.parse(text), text };
  } catch {
    return { status: res.status, body: null, text };
  }
}

function parsePrometheus(rawText) {
  const byMetricName = {};
  const lines = [];
  for (const line of rawText.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    const idx = line.lastIndexOf(' ');
    if (idx <= 0) continue;
    const key = line.slice(0, idx).trim();
    const val = Number(line.slice(idx + 1).trim());
    if (Number.isNaN(val)) continue;
    const braceIdx = key.indexOf('{');
    const name = braceIdx > 0 ? key.slice(0, braceIdx) : key;
    byMetricName[name] = (byMetricName[name] || 0) + val;
    lines.push({ key, name, val });
  }
  return { byMetricName, lines };
}

async function fetchPrometheusMap() {
  const res = await fetch(`${BASE_URL}/actuator/prometheus`);
  const text = await res.text();
  return parsePrometheus(text);
}

async function waitForBackendReady(maxWaitMs = 60000) {
  const deadline = Date.now() + maxWaitMs;
  while (Date.now() < deadline) {
    try {
      const h = await fetchJson(`${BASE_URL}/actuator/health`);
      if (h.status === 200 && h.body?.status === 'UP') {
        return;
      }
    } catch {
      // ignore while booting
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error('Backend did not become UP within timeout');
}

async function sampleRuntimeResources(token) {
  const authHeaders = { Authorization: `Bearer ${token}` };
  try {
    const [cpuRes, sysCpuRes, heapRes, nonHeapRes, hikariActiveRes, hikariIdleRes, hikariPendingRes] = await Promise.all([
      fetchJson(`${BASE_URL}/actuator/metrics/process.cpu.usage`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/system.cpu.usage`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/jvm.memory.used?tag=area:heap`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/jvm.memory.used?tag=area:nonheap`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/hikaricp.connections.active`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/hikaricp.connections.idle`, { headers: authHeaders }),
      fetchJson(`${BASE_URL}/actuator/metrics/hikaricp.connections.pending`, { headers: authHeaders }),
    ]);
    const cpu = cpuRes.body?.measurements?.[0]?.value ?? null;
    const sysCpu = sysCpuRes.body?.measurements?.[0]?.value ?? null;
    const heapBytes = heapRes.body?.measurements?.[0]?.value ?? null;
    const nonHeapBytes = nonHeapRes.body?.measurements?.[0]?.value ?? null;
    return {
      cpuPercent: cpu !== null ? Number((cpu * 100).toFixed(2)) : null,
      systemCpuPercent: sysCpu !== null ? Number((sysCpu * 100).toFixed(2)) : null,
      heapMb: heapBytes !== null ? Number((heapBytes / (1024 * 1024)).toFixed(2)) : null,
      nonHeapMb: nonHeapBytes !== null ? Number((nonHeapBytes / (1024 * 1024)).toFixed(2)) : null,
      hikariActive: hikariActiveRes.body?.measurements?.[0]?.value ?? null,
      hikariIdle: hikariIdleRes.body?.measurements?.[0]?.value ?? null,
      hikariPending: hikariPendingRes.body?.measurements?.[0]?.value ?? null,
    };
  } catch {
    return null;
  }
}

async function main() {
  console.log('0. Waiting for backend /actuator/health UP...');
  await waitForBackendReady(60000);
  console.log('1. Authenticating admin user & preparing deterministic K6 test fixtures...');
  const login = await fetchJson(`${BASE_URL}/auth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
  });
  if (login.status !== 200 || !login.body?.result?.token) {
    throw new Error(`Login failed: status=${login.status} body=${login.text}`);
  }
  const token = login.body.result.token;

  // Ensure an active OPEN shift exists
  const curShift = await fetchJson(`${BASE_URL}/shifts/current`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!curShift.body?.result || curShift.body.result.status !== 'OPEN') {
    const opened = await fetchJson(`${BASE_URL}/shifts/open`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ openingCash: 1000000, note: 'K6 Load Test Shift', terminalId: 'MAIN_COUNTER' }),
    });
    console.log(`Shift opened check status=${opened.status}`);
  }

  // Ensure isolated load-test product K6-BENCH-001 exists in `product` table with plenty of stock (50000)
  let productId = mysqlQuery("SELECT id FROM product WHERE product_code = 'K6-BENCH-001' LIMIT 1");
  if (!productId) {
    const catId = mysqlQuery('SELECT id FROM category LIMIT 1');
    const supId = mysqlQuery('SELECT id FROM supplier LIMIT 1');
    mysqlQuery(`INSERT INTO product (id, product_code, name, price, cost, stock, unit, status, category_id, supplier_id, created_at, updated_at, version) VALUES (UUID(), 'K6-BENCH-001', 'Ca Phe K6 Benchmark', 35000.00, 15000.00, 50000, 'LY', 'ACTIVE', '${catId}', '${supId}', NOW(), NOW(), 0)`);
    productId = mysqlQuery("SELECT id FROM product WHERE product_code = 'K6-BENCH-001' LIMIT 1");
  } else {
    mysqlQuery(`UPDATE product SET stock = 50000, status = 'ACTIVE' WHERE id = '${productId}'`);
  }

  // Warm up cache before baseline snapshot
  await fetch(`${BASE_URL}/products?page=1&size=10`, { headers: { Authorization: `Bearer ${token}` } });
  await fetch(`${BASE_URL}/reports/dashboard`, { headers: { Authorization: `Bearer ${token}` } });

  const stockBefore = Number(mysqlQuery(`SELECT stock FROM product WHERE id = '${productId}'`));
  const paidOrdersBefore = Number(mysqlQuery("SELECT COUNT(*) FROM orders WHERE status = 'PAID'"));
  const successPaymentsBefore = Number(mysqlQuery("SELECT COUNT(*) FROM payment WHERE status = 'SUCCESS'"));
  const unitsSoldBefore = Number(mysqlQuery(
    `SELECT COALESCE(SUM(od.quantity), 0) FROM order_detail od JOIN orders o ON od.order_id = o.id WHERE od.product_id = '${productId}' AND o.status = 'PAID'`
  ));
  const outboxProcessedBefore = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSED'"));
  const promBefore = await fetchPrometheusMap();

  console.log(`Baseline captured: productId=${productId}, stockBefore=${stockBefore}, paidOrdersBefore=${paidOrdersBefore}`);

  // Poll CPU / Heap / Hikari during k6 execution
  const samples = [];
  const sampler = setInterval(async () => {
    const s = await sampleRuntimeResources(token);
    if (s) samples.push(s);
  }, 2000);

  console.log('2. Running grafana/k6 load suite via Docker...');
  await new Promise((resolve, reject) => {
    const dockerArgs = [
      'run', '--rm',
      '--add-host=host.docker.internal:host-gateway',
      '-v', `${K6_DIR}:/scripts`,
      '-e', `K6_BASE_URL=${DOCKER_TARGET_URL}`,
      '-e', `K6_USERNAME=${USERNAME}`,
      '-e', `K6_PASSWORD=${PASSWORD}`,
      '-e', `K6_PRODUCT_ID=${productId}`,
      '-e', 'K6_PRODUCT_PRICE=35000',
      '-e', 'K6_ORDER_QTY=2',
      'grafana/k6', 'run',
      '--summary-trend-stats=avg,min,med,max,p(90),p(95),p(99)',
      '--summary-export=/scripts/k6-summary.json',
      '/scripts/pos-load-test.js'
    ];
    const proc = spawn('docker', dockerArgs, { stdio: 'inherit' });
    proc.on('exit', (code) => {
      clearInterval(sampler);
      if (code === 0 || code === 99) {
        resolve(code);
      } else {
        reject(new Error(`k6 exited with code ${code}`));
      }
    });
  });

  // Wait up to 45s for OutboxProcessor (batchSize=30, poll=2s -> ~15 events/s) to drain completely
  console.log('3. Monitoring background Outbox worker drain...');
  const drainStart = Date.now();
  while (Date.now() - drainStart < 45000) {
    const rem = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status IN ('PENDING','PROCESSING')"));
    if (rem === 0) break;
    await new Promise((r) => setTimeout(r, 2000));
  }
  const drainDurationSeconds = Number(((Date.now() - drainStart) / 1000).toFixed(2));

  const promAfter = await fetchPrometheusMap();
  const stockAfter = Number(mysqlQuery(`SELECT stock FROM product WHERE id = '${productId}'`));
  const negativeStockCount = Number(mysqlQuery('SELECT COUNT(*) FROM product WHERE stock < 0'));
  const paidOrdersAfter = Number(mysqlQuery("SELECT COUNT(*) FROM orders WHERE status = 'PAID'"));
  const successPaymentsAfter = Number(mysqlQuery("SELECT COUNT(*) FROM payment WHERE status = 'SUCCESS'"));
  const unitsSoldAfter = Number(mysqlQuery(
    `SELECT COALESCE(SUM(od.quantity), 0) FROM order_detail od JOIN orders o ON od.order_id = o.id WHERE od.product_id = '${productId}' AND o.status = 'PAID'`
  ));
  const orphanPaidOrders = Number(mysqlQuery(
    "SELECT COUNT(*) FROM orders o LEFT JOIN payment p ON p.order_id = o.id AND p.status = 'SUCCESS' WHERE o.status = 'PAID' AND p.id IS NULL"
  ));
  const duplicatePaymentsPerOrder = Number(mysqlQuery(
    "SELECT COUNT(*) FROM (SELECT order_id FROM payment WHERE status = 'SUCCESS' GROUP BY order_id HAVING COUNT(*) > 1) dup"
  ));
  const outboxPending = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'"));
  const outboxFailed = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED'"));
  const outboxDeadLetter = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status = 'DEAD_LETTER'"));
  const outboxProcessedAfter = Number(mysqlQuery("SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSED'"));

  const stockDelta = stockBefore - stockAfter;
  const soldDelta = unitsSoldAfter - unitsSoldBefore;
  const paidOrdersDelta = paidOrdersAfter - paidOrdersBefore;
  const paymentsDelta = successPaymentsAfter - successPaymentsBefore;

  const summary = JSON.parse(fs.readFileSync(path.join(K6_DIR, 'k6-summary.json'), 'utf8'));

  const validCpu = samples.map((s) => s.cpuPercent).filter((x) => x !== null);
  const validSysCpu = samples.map((s) => s.systemCpuPercent).filter((x) => x !== null);
  const validHeap = samples.map((s) => s.heapMb).filter((x) => x !== null);
  const validNonHeap = samples.map((s) => s.nonHeapMb).filter((x) => x !== null);
  const validHikari = samples.map((s) => s.hikariActive).filter((x) => x !== null);
  const validHikariPending = samples.map((s) => s.hikariPending).filter((x) => x !== null);

  const reportData = {
    executedAt: new Date().toISOString(),
    k6Summary: summary.metrics,
    resourceUsageDuringLoad: {
      sampleCount: samples.length,
      peakProcessCpuPercent: validCpu.length ? Math.max(...validCpu) : null,
      avgProcessCpuPercent: validCpu.length ? Number((validCpu.reduce((a, b) => a + b, 0) / validCpu.length).toFixed(2)) : null,
      peakSystemCpuPercent: validSysCpu.length ? Math.max(...validSysCpu) : null,
      avgSystemCpuPercent: validSysCpu.length ? Number((validSysCpu.reduce((a, b) => a + b, 0) / validSysCpu.length).toFixed(2)) : null,
      peakHeapMb: validHeap.length ? Math.max(...validHeap) : null,
      avgHeapMb: validHeap.length ? Number((validHeap.reduce((a, b) => a + b, 0) / validHeap.length).toFixed(2)) : null,
      peakNonHeapMb: validNonHeap.length ? Math.max(...validNonHeap) : null,
      peakHikariActive: validHikari.length ? Math.max(...validHikari) : null,
      peakHikariPending: validHikariPending.length ? Math.max(...validHikariPending) : null,
    },
    prometheusDeltas: {
      cacheHitsDelta: (promAfter.byMetricName['pos_cache_hits_total'] || 0) - (promBefore.byMetricName['pos_cache_hits_total'] || 0),
      cacheMissesDelta: (promAfter.byMetricName['pos_cache_misses_total'] || 0) - (promBefore.byMetricName['pos_cache_misses_total'] || 0),
      cacheHitRatePercent: promAfter.byMetricName['pos_cache_hit_rate_percent'] ?? null,
      ordersPlacedDelta: (promAfter.byMetricName['pos_orders_placed_total'] || 0) - (promBefore.byMetricName['pos_orders_placed_total'] || 0),
      paymentsCompletedDelta: (promAfter.byMetricName['pos_payments_completed_total'] || 0) - (promBefore.byMetricName['pos_payments_completed_total'] || 0),
      inventoryAdjustmentsDelta: (promAfter.byMetricName['pos_inventory_adjustments_total'] || 0) - (promBefore.byMetricName['pos_inventory_adjustments_total'] || 0),
      gcPauseSecondsSum: promAfter.byMetricName['jvm_gc_pause_seconds_sum'] ?? null,
      gcPauseCount: promAfter.byMetricName['jvm_gc_pause_seconds_count'] ?? null,
      outboxDrainWaitSeconds: drainDurationSeconds,
    },
    dataConsistencyAudit: {
      stockBefore,
      stockAfter,
      stockDelta,
      unitsSoldDelta: soldDelta,
      stockMatchesUnitsSold: stockDelta === soldDelta,
      negativeStockProducts: negativeStockCount,
      paidOrdersDelta,
      paymentsDelta,
      paidOrdersMatchPayments: paidOrdersDelta === paymentsDelta,
      orphanPaidOrders,
      duplicatePaymentsPerOrder,
      outboxPending,
      outboxFailed,
      outboxDeadLetter,
      outboxProcessedDelta: outboxProcessedAfter - outboxProcessedBefore,
    },
  };

  fs.writeFileSync(path.join(K6_DIR, 'k6-benchmark-results.json'), JSON.stringify(reportData, null, 2));
  console.log('\n=== CONSISTENCY & RESOURCE AUDIT ===');
  console.log(JSON.stringify(reportData.dataConsistencyAudit, null, 2));
  console.log(JSON.stringify(reportData.resourceUsageDuringLoad, null, 2));
  console.log(JSON.stringify(reportData.prometheusDeltas, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
