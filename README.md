# Distributed Order Orchestration

An order workflow split across five Spring Boot services, coordinated by a **saga orchestrator** over **Apache Kafka**, with a React dashboard for watching sagas run and compensate in real time.

```
 React dashboard ──► orchestrator ──commands──► order / payment / inventory / notification
   (port 3001)         (8080)   ◄───events────          (8081-8084)
                          │                                  │
                          └──────────── Kafka ───────────────┘            Postgres (schema per service)
```

## What it demonstrates

| Concern | How it is handled |
|---|---|
| **Distributed transaction** | Orchestrated saga: order → payment → inventory → notification, with compensation (refund payment, cancel order) when a step fails. |
| **No dual writes** | Transactional outbox in every service. State change and outgoing message commit in one DB transaction; a relay publishes to Kafka afterwards. |
| **At-least-once delivery** | Commands carry an idempotency key (`sagaId:commandType`) backed by a unique constraint. The orchestrator drops events that do not match the step it is waiting on. |
| **Failure handling** | Kafka consumers retry with exponential backoff, then dead-letter to `<topic>.DLT`. Resilience4j circuit breaker around the outbox relay. A watchdog fails sagas stuck longer than 5 minutes. |
| **Real inventory** | Stock is decremented with a conditional `UPDATE ... WHERE available >= qty`, so concurrent orders cannot oversell. |
| **Observability** | OpenTelemetry Java agent; trace context is stored in the outbox row, so one order is one trace across all five services (Tempo + Grafana). |

## Saga flow

```
ORDER_CREATE ─► PAYMENT_CHARGE ─► INVENTORY_RESERVE ─► NOTIFICATION_SEND ─► COMPLETED
                     │ fail              │ fail
                     ▼                   ▼
                ORDER_CANCEL      PAYMENT_REFUND ─► ORDER_CANCEL ─► COMPENSATED
```

A failed notification never triggers compensation; the saga completes anyway.

## Run it

Prerequisites: Java 21, Docker, Node 22 (only for local dashboard development).

```powershell
./mvnw -DskipTests package
docker compose up -d --build
```

| URL | What |
|---|---|
| http://localhost:3001 | Dashboard |
| http://localhost:8080/orders | Orchestrator API |
| http://localhost:8090 | Kafka UI (topics, consumer lag, DLTs) |

Optional tracing stack, then open Grafana at http://localhost:3000 → Explore → Tempo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

Postgres is published on host port 5433. Compose runs the `dev` profile, which injects random failures (10% per payment, inventory and notification step) so compensation is visible. Set `SPRING_PROFILES_ACTIVE=prod` to turn that off.

## API

```
POST /orders                 202 {"sagaId": "...", "status": "ACCEPTED"}
GET  /sagas                  list, newest first (?status=&limit=)
GET  /sagas/{id}             saga state
GET  /sagas/{id}/events      step-by-step timeline
GET  /sagas/stats            counts per status + average completion time
```

Body for `POST /orders`: `{"customerId": uuid, "productId": uuid, "quantity": 1, "totalAmount": 49.90}`.
Send header `X-Force-Payment-Failure: true` (dev profile) to force the compensation path.

Seeded products: `1111…` Laptop, `2222…` Headphones, `3333…` Limited Edition Console (only 20 in stock, good for showing out-of-stock compensation).

## Tests

```powershell
./mvnw verify                      # unit tests (saga state machine)
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 -e RATE=200 grafana/k6 run - < k6/load-test.js
```

### Measured load test

200 orders/s for 60 s (12,000 orders), Docker Desktop on a Windows laptop, all services and Kafka in containers, `dev` profile (so ~19% of sagas compensate by design):

- Order intake p95 **17.7 ms**, 0.04% request errors (6 of 12,000, during warm-up)
- **11,995 of 11,995** accepted sagas reached a terminal state before the test finished; 0 stuck, 0 failed

Numbers are from one machine and are not a capacity claim.

## Repository layout

```
common/                 message types, outbox + relay, Kafka config, failure simulator
orchestrator/           saga state machine, REST API, watchdog
order-service/ payment-service/ inventory-service/ notification-service/
dashboard/              React + TypeScript (Vite), served by nginx
observability/          Tempo and Grafana config
k6/                     load and chaos scenarios
.github/workflows/      CI: build + test, dashboard build, push images to GHCR
```

## Known limitations

- The watchdog marks a saga `FAILED` after a timeout; it does not try to compensate work already done, so a stuck saga needs manual review.
- Single Kafka broker and single instance per service (the outbox relay uses `SKIP LOCKED`, so it is safe to scale out, but this has not been load-tested that way).
- Railway deployment from the original plan is not set up.
