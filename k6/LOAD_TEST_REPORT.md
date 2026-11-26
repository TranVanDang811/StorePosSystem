# POS System — k6 Load Testing & Data Consistency Report (Phase 5)

**Execution Date:** `2026-11-25`  
**Target Stack:** Spring Boot `3.4.5` (`Java 21`, Virtual Threads enabled, `HikariCP max=10`) + MySQL `8.0` + Redis `7.4`  
**Load Generator:** `grafana/k6` (`Docker`) simulating 32 max VUs across 4 concurrent scenarios for `30.3s` (`RATE_LIMIT_ENABLED=false` during raw throughput benchmark so synthetic stress is not capped by per-user `60 req/min` checkout quota).

---

## 1. Benchmark Scenarios & Configuration

| Scenario | Executor | Concurrency | Covered Endpoints |
|---|---|---|---|
| `product_listing_and_search` | `ramping-vus` | `2 -> 18` VUs (`30s`) | `GET /backend/products?page=1&size=10`, `GET /backend/products/filter?keyword=...` |
| `dashboard_summary` | `constant-vus` | `4` VUs (`30s`) | `GET /backend/reports/dashboard` (Redis cached) |
| `order_checkout_and_payment` | `ramping-vus` | `1 -> 8` VUs (`30s`) | `POST /backend/orders`, `POST /backend/payments` (+ 1-in-5 Idempotent Replay) |
| `realtime_websocket_probe` | `constant-vus` | `2` VUs (`25s`) | `ws://.../backend/ws/pos` STOMP `CONNECT -> CONNECTED -> DISCONNECT` |

Every authenticated HTTP request carried a W3C `traceparent` header (`00-<traceId>-<spanId>-01`), matching React's [`axiosInstance.jsx`](../frontend/src/services/axiosInstance.jsx).

---

## 2. Measured Latency, Throughput & Error Rate

- **Total HTTP Requests:** `2,096` (`69.14 req/s`)
- **HTTP Failure Rate (`http_req_failed`):** **`0.00%`** (`0 / 2,096`)
- **Functional Assertions (`checks`):** **`100.00%`** (`2,375 / 2,375` passed)
- **WebSocket Sessions:** `90` sessions (`2.97/s`, `100%` upgrade `101`, `180` STOMP frames sent, `90` `CONNECTED` replies)
- **Distributed Trace Propagation (`X-Trace-Id` match):** **`100.00%`** (`2,062 / 2,062`)

| Flow / Metric | Avg (ms) | Med (ms) | P95 (ms) | P99 (ms) | Max (ms) | k6 SLO Status |
|---|---:|---:|---:|---:|---:|---|
| **Overall HTTP (`http_req_duration`)** | `182.91` | `142.41` | `504.28` | `875.92` | `1,590.69` | P95 `> 500ms`, P99 `< 1200ms` ✓ |
| **Product Listing (`GET /products`)** | `147.87` | `127.04` | `320.22` | `462.21` | `567.42` | P95 `> 250ms` |
| **Product Search (`GET /products/filter`)** | `148.74` | `132.13` | `325.06` | `479.59` | `563.87` | P95 `> 250ms` |
| **Dashboard (`GET /reports/dashboard`)** | `174.23` | `157.78` | `365.47` | `474.85` | `612.76` | P95 `> 300ms` |
| **Order Creation (`POST /orders`)** | `306.11` | `229.21` | `771.68` | `1,113.79` | `1,143.71` | P95 `> 500ms` |
| **Payment Checkout (`POST /payments`)** | `411.95` | `314.77` | `926.11` | `1,337.52` | `1,590.69` | P95 `> 700ms` |
| **WebSocket STOMP Connect (`ws/pos`)** | `43.74` | `22.00` | `125.90` | `235.55` | `240.00` | Healthy (`< 250ms` P99) |

---

## 3. Server Runtime Telemetry (Actuator & Prometheus)

Sampled every `2s` (`15` samples) during active load + Prometheus delta comparison:

| Resource / Telemetry Metric | Measured Value |
|---|---|
| JVM Process CPU (`process.cpu.usage`) | Avg **`19.47%`**, Peak **`23.35%`** |
| Host System CPU (`system.cpu.usage`) | Avg **`99.08%`**, Peak **`100.00%`** (co-hosted MySQL + Redis + k6 + JVM) |
| JVM Heap Memory (`area=heap`) | Avg **`187.90 MB`**, Peak **`297.85 MB`** |
| JVM Non-Heap Memory | Peak **`161.97 MB`** |
| JVM GC Pauses (`jvm_gc_pause_seconds`) | `25` pauses, `0.258s` cumulative (`~10.3ms` avg) |
| HikariCP Pool (`max=10`) | Peak Active **`10 / 10` (100% saturated)**, Peak Pending **`15` blocked threads** |
| Redis Cache Activity | `+1,241` hits, `+511` misses (**`67.71%`** overall hit rate during write load) |
| Business Counters Delta | `+155` Orders Placed, `+155` Payments Completed, `+155` Inventory Adjustments |

---

## 4. Post-Load Data Consistency Verification (MySQL 8.0)

Executed against `pos-mysql` after benchmark completion:

| Integrity Check | Measured Result | Verdict |
|---|---|---|
| Stock deduction parity (`K6-BENCH-001`) | `50,000 -> 49,690` (`stockDelta=310 == 155 * 2 = unitsSoldDelta=310`) | **PASS** |
| Negative stock count across all `product` rows | `0` products with `stock < 0` | **PASS** |
| Order vs Payment parity | `paidOrdersDelta=155 == paymentsDelta=155` | **PASS** |
| Concurrent idempotent payment replays (`1-in-5`) | `33 / 33` replays returned identical `payment.id` with zero extra charge | **PASS** |
| Orphan `PAID` orders without `SUCCESS` payment | `0` | **PASS** |
| Duplicate `SUCCESS` payments per `order_id` | `0` | **PASS** |
| Transactional Outbox integrity | `0` `FAILED`, `0` `DEAD_LETTER` (`425` drained during `46.57s` observation) | **PASS (lag noted below)** |

---

## 5. Bottleneck Analysis & Recommendations

1. **HikariCP Pool Saturation (`10/10` active, `15` pending threads) & Per-Request Auth Queries (`CustomJwtDecoder.java`)**
   - *Evidence:* `CustomJwtDecoder.java` lines `44–61` execute `invalidatedTokenRepository.existsById(jti)` and `userRepository.findAuthStateByUsername(username)` on **every** authenticated request, forcing Redis-cached read APIs (`GET /products`, `GET /reports/dashboard`) to acquire DB connections and wait behind Checkout `SELECT ... FOR UPDATE` locks.
   - *Recommendation:* Cache active user auth state (`status`, `passwordChangedAt`) in Redis with short TTL (15–30s) plus active invalidation on `/auth/logout` and password changes; set `spring.jpa.open-in-view: false` globally in `application.yaml`; tune `HIKARI_MAX_POOL_SIZE` to `20–25`.
2. **Cache Eviction Churn on Every Checkout (`67.71%` hit rate)**
   - *Evidence:* `OrderServiceImpl` & `PaymentServiceImpl` evict `PRODUCTS_LIST` & `DASHBOARD` on every order/payment (`~5x/s`), triggering `511` cache rebuild misses during 30s.
   - *Recommendation:* Keep `stock` read live only at payment lock time (`findAllByIdWithLock`) while tolerating short 15–30s TTL on catalog list/dashboard projections instead of synchronous full-region `evictAll` on every checkout.
3. **Single-Row Pessimistic Lock Contention on Hot SKUs (`PaymentServiceImpl.java`)**
   - *Evidence:* Payment `P95 = 926.11ms` / `P99 = 1,337.52ms` when 8 VUs simultaneously execute `productRepository.findAllByIdWithLock` on `K6-BENCH-001`.
   - *Recommendation:* Keep pessimistic locking for correctness or switch non-scarcity SKUs to atomic SQL conditional updates (`UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?`) / short-lived transactions excluding PDF/external calls.
4. **Outbox Poller Drain Ceiling (`~9.1 events/s`)**
   - *Evidence:* `OutboxProperties` (`batchSize=30`, `pollIntervalMs=2000`) plus per-event ordering/claim transactions left `1,015 PENDING` queued events after back-to-back runs while draining `425` over `46.57s`.
   - *Recommendation:* Configure `OUTBOX_BATCH_SIZE=100`, `OUTBOX_POLL_INTERVAL_MS=500`, and enable Kafka transport (`OUTBOX_TRANSPORT=kafka`, `KAFKA_ENABLED=true`) for high-burst fan-out.
