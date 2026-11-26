import http from 'k6/http';
import ws from 'k6/ws';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.K6_BASE_URL || 'http://host.docker.internal:8080/backend';
const WS_URL = __ENV.K6_WS_URL || BASE_URL.replace(/^http/, 'ws') + '/ws/pos';
const USERNAME = __ENV.K6_USERNAME || 'admin';
const PASSWORD = __ENV.K6_PASSWORD || 'Admin12345';
const TARGET_PRODUCT_ID = __ENV.K6_PRODUCT_ID || '';
const TARGET_PRODUCT_PRICE = Number(__ENV.K6_PRODUCT_PRICE || 35000);
const ORDER_QTY = Number(__ENV.K6_ORDER_QTY || 2);

const listingLatency = new Trend('pos_listing_latency_ms', true);
const searchLatency = new Trend('pos_search_latency_ms', true);
const dashboardLatency = new Trend('pos_dashboard_latency_ms', true);
const orderCreateLatency = new Trend('pos_order_create_latency_ms', true);
const paymentLatency = new Trend('pos_payment_latency_ms', true);
const wsHandshakeLatency = new Trend('pos_ws_connect_latency_ms', true);

const ordersCreatedCounter = new Counter('pos_k6_orders_created_ok');
const paymentsCompletedCounter = new Counter('pos_k6_payments_completed_ok');
const idempotentReplaysCounter = new Counter('pos_k6_idempotent_replays_ok');
const wsConnectSuccessCounter = new Counter('pos_k6_ws_connect_ok');
const traceHeaderPresentRate = new Rate('pos_k6_trace_header_rate');

export const options = {
  scenarios: {
    product_listing_and_search: {
      executor: 'ramping-vus',
      exec: 'productBrowseAndSearch',
      startVUs: 2,
      stages: [
        { duration: '8s', target: 10 },
        { duration: '15s', target: 18 },
        { duration: '7s', target: 4 },
      ],
      gracefulRampDown: '3s',
    },
    dashboard_summary: {
      executor: 'constant-vus',
      exec: 'dashboardSummary',
      vus: 4,
      duration: '30s',
    },
    order_checkout_and_payment: {
      executor: 'ramping-vus',
      exec: 'checkoutAndPay',
      startVUs: 1,
      stages: [
        { duration: '6s', target: 4 },
        { duration: '18s', target: 8 },
        { duration: '6s', target: 2 },
      ],
      gracefulRampDown: '3s',
    },
    realtime_websocket_probe: {
      executor: 'constant-vus',
      exec: 'realtimeWsProbe',
      vus: 2,
      duration: '25s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
    pos_listing_latency_ms: ['p(95)<250'],
    pos_search_latency_ms: ['p(95)<250'],
    pos_dashboard_latency_ms: ['p(95)<300'],
    pos_order_create_latency_ms: ['p(95)<500'],
    pos_payment_latency_ms: ['p(95)<700'],
    pos_k6_trace_header_rate: ['rate>0.99'],
  },
};

function randomHex(bytesLen) {
  const chars = '0123456789abcdef';
  let out = '';
  for (let i = 0; i < bytesLen * 2; i += 1) {
    out += chars[Math.floor(Math.random() * 16)];
  }
  return out;
}

function buildHeaders(token) {
  const traceId = randomHex(16);
  const spanId = randomHex(8);
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      traceparent: `00-${traceId}-${spanId}-01`,
    },
    traceId,
  };
}

function recordTraceHeader(res, expectedTraceId) {
  const got = res && res.headers ? (res.headers['X-Trace-Id'] || res.headers['x-trace-id']) : null;
  traceHeaderPresentRate.add(Boolean(got && got === expectedTraceId));
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/auth/token`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth_login' } }
  );

  check(loginRes, {
    'login returns 200': (r) => r.status === 200,
    'login returns token': (r) => Boolean(r.json('result.token')),
  });

  const token = loginRes.json('result.token');
  let productId = TARGET_PRODUCT_ID;
  let price = TARGET_PRODUCT_PRICE;

  if (!productId) {
    const prodRes = http.get(`${BASE_URL}/products?page=1&size=10`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const list = prodRes.json('result.content') || [];
    const available = list.find((p) => Number(p.stock) > 200) || list[0];
    if (available) {
      productId = available.id;
      price = Number(available.price || 35000);
    }
  }

  return { token, productId, price };
}

export function productBrowseAndSearch(data) {
  group('Product Listing & Search', () => {
    const listReq = buildHeaders(data.token);
    const listRes = http.get(`${BASE_URL}/products?page=1&size=10`, {
      headers: listReq.headers,
      tags: { endpoint: 'product_listing' },
    });
    listingLatency.add(listRes.timings.duration);
    recordTraceHeader(listRes, listReq.traceId);
    check(listRes, {
      'product list 200': (r) => r.status === 200,
    });

    const keywords = ['Ca', 'Tra', 'K6', 'Latte'];
    const kw = keywords[__ITER % keywords.length];
    const searchReq = buildHeaders(data.token);
    const searchRes = http.get(`${BASE_URL}/products/filter?keyword=${encodeURIComponent(kw)}&page=1&size=10`, {
      headers: searchReq.headers,
      tags: { endpoint: 'product_search' },
    });
    searchLatency.add(searchRes.timings.duration);
    recordTraceHeader(searchRes, searchReq.traceId);
    check(searchRes, {
      'product search 200': (r) => r.status === 200,
    });
  });
  sleep(0.15);
}

export function dashboardSummary(data) {
  const req = buildHeaders(data.token);
  const res = http.get(`${BASE_URL}/reports/dashboard`, {
    headers: req.headers,
    tags: { endpoint: 'dashboard' },
  });
  dashboardLatency.add(res.timings.duration);
  recordTraceHeader(res, req.traceId);
  check(res, {
    'dashboard 200': (r) => r.status === 200,
  });
  sleep(0.25);
}

export function checkoutAndPay(data) {
  if (!data.productId) {
    sleep(0.5);
    return;
  }

  let orderId = null;
  let finalAmount = data.price * ORDER_QTY;

  group('Order Creation & Payment Checkout', () => {
    const ordReq = buildHeaders(data.token);
    const ordRes = http.post(
      `${BASE_URL}/orders`,
      JSON.stringify({
        orderDetails: [{ productId: data.productId, quantity: ORDER_QTY }],
      }),
      {
        headers: ordReq.headers,
        tags: { endpoint: 'order_create' },
      }
    );
    orderCreateLatency.add(ordRes.timings.duration);
    recordTraceHeader(ordRes, ordReq.traceId);
    const ordOk = check(ordRes, {
      'order created 201': (r) => r.status === 201 || r.status === 200,
      'order id present': (r) => Boolean(r.json('result.id')),
    });
    if (!ordOk) {
      return;
    }
    orderId = ordRes.json('result.id');
    finalAmount = Number(ordRes.json('result.finalAmount') || finalAmount);
    ordersCreatedCounter.add(1);

    const idemKey = `k6-${orderId}-${__VU}-${__ITER}`;
    const payPayload = JSON.stringify({
      orderId,
      method: 'CASH',
      cashReceived: finalAmount,
      note: 'K6_LOAD_TEST',
      idempotencyKey: idemKey,
    });

    const payReq = buildHeaders(data.token);
    const payRes = http.post(`${BASE_URL}/payments`, payPayload, {
      headers: payReq.headers,
      tags: { endpoint: 'payment_checkout' },
    });
    paymentLatency.add(payRes.timings.duration);
    recordTraceHeader(payRes, payReq.traceId);
    const payOk = check(payRes, {
      'payment created 201': (r) => r.status === 201 || r.status === 200,
    });
    if (payOk) {
      paymentsCompletedCounter.add(1);
    }

    // Periodically verify idempotent payment replay under load does not double-charge
    if (__ITER % 5 === 0) {
      const replayReq = buildHeaders(data.token);
      const replayRes = http.post(`${BASE_URL}/payments`, payPayload, {
        headers: replayReq.headers,
        tags: { endpoint: 'payment_idempotent_replay' },
      });
      const replayOk = check(replayRes, {
        'idempotent replay 201': (r) => r.status === 201 || r.status === 200,
        'idempotent same payment id': (r) => r.json('result.id') === payRes.json('result.id'),
      });
      if (replayOk) {
        idempotentReplaysCounter.add(1);
      }
    }
  });
  sleep(0.2);
}

export function realtimeWsProbe() {
  const startedAt = Date.now();
  const res = ws.connect(WS_URL, null, (socket) => {
    socket.on('open', () => {
      wsHandshakeLatency.add(Date.now() - startedAt);
      socket.send('CONNECT\naccept-version:1.2,1.1,1.0\nhost:localhost\n\n\0');
    });
    socket.on('message', (msg) => {
      if (msg && msg.startsWith('CONNECTED')) {
        wsConnectSuccessCounter.add(1);
        socket.send('DISCONNECT\nreceipt:k6-close\n\n\0');
        socket.close();
      }
    });
    socket.setTimeout(() => {
      socket.close();
    }, 1500);
  });

  check(res, {
    'websocket upgrade 101': (r) => r && r.status === 101,
  });
  sleep(0.5);
}
