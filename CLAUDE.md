# Distributed Order Orchestration System — Build Skill

> **Update:** RabbitMQ was replaced by Apache Kafka, and a `common` module (messaging types, transactional outbox, Kafka config) plus a React `dashboard/` were added. Phases 3-6 below describe the original RabbitMQ design; see README.md for the current architecture.


## Project Overview

Build a production-grade distributed backend system in Java 21 + Spring Boot 3.3. Five microservices coordinate an e-commerce order workflow via saga orchestration over RabbitMQ. Deployed to Railway (free tier).

**Stack:** Java 21, Spring Boot 3.3, Maven multi-module, PostgreSQL 15, RabbitMQ 3, Resilience4j, OpenTelemetry Java agent, Docker Compose, k6, Railway deployment.

---

## Phase 1 — Maven Multi-Module Skeleton

### Task
Create a Maven multi-module project. Root `pom.xml` is the parent. Five child modules: `orchestrator`, `order-service`, `payment-service`, `inventory-service`, `notification-service`.

### Parent pom.xml
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.0</version>
</parent>
<groupId>com.orderorchestration</groupId>
<artifactId>order-orchestration</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<modules>
  <module>orchestrator</module>
  <module>order-service</module>
  <module>payment-service</module>
  <module>inventory-service</module>
  <module>notification-service</module>
</modules>
```

Managed dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-amqp`, `resilience4j-spring-boot3`, `postgresql`, `flyway-core`. Java version: 21.

### Each Child Module
- Main class annotated `@SpringBootApplication`
- `GET /actuator/health` (auto via actuator)
- `GET /health` returning `{"status":"ok","service":"<n>"}`
- Port: orchestrator=8080, order=8081, payment=8082, inventory=8083, notification=8084

### application.yml (each service, fill in schema name)
```yaml
spring:
  application:
    name: ${SERVICE_NAME}
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/orders}
    username: ${DATABASE_USER:postgres}
    password: ${DATABASE_PASSWORD:postgres}
    hikari:
      schema: ${DB_SCHEMA:<schema_name>}
      maximum-pool-size: ${HIKARI_POOL_SIZE:20}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: ${DB_SCHEMA:<schema_name>}
  rabbitmq:
    addresses: ${RABBITMQ_URL:amqp://guest:guest@localhost:5672}
  flyway:
    schemas: ${DB_SCHEMA:<schema_name>}
management:
  endpoints:
    web:
      exposure:
        include: health,info,circuitbreakers,metrics
```

### docker-compose.yml
```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]

  orchestrator:
    build: ./orchestrator
    ports: ["8080:8080"]
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/orders
      DATABASE_USER: postgres
      DATABASE_PASSWORD: postgres
      DB_SCHEMA: orchestrator
      RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672
    depends_on: [postgres, rabbitmq]

  # Repeat pattern for order(8081), payment(8082), inventory(8083), notification(8084)
  # Each gets its own DB_SCHEMA value: order_service, payment_service, inventory_service, notification_service
```

### init.sql
```sql
CREATE SCHEMA IF NOT EXISTS orchestrator;
CREATE SCHEMA IF NOT EXISTS order_service;
CREATE SCHEMA IF NOT EXISTS payment_service;
CREATE SCHEMA IF NOT EXISTS inventory_service;
CREATE SCHEMA IF NOT EXISTS notification_service;
```

### Each Service Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Phase 2 — Database Migrations (Flyway)

Create `src/main/resources/db/migration/V1__init.sql` in each module.

### orchestrator — V1__init.sql
```sql
CREATE TABLE orchestrator.saga_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID,
  status VARCHAR(50) NOT NULL,
  current_step VARCHAR(50),
  payload JSONB,
  failure_reason VARCHAR(500),
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_saga_status ON orchestrator.saga_instances(status);
```

### order-service — V1__init.sql
```sql
CREATE TABLE order_service.orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  customer_id UUID NOT NULL,
  product_id UUID NOT NULL,
  quantity INT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT now()
);
```

### payment-service — V1__init.sql
```sql
CREATE TABLE payment_service.payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  order_id UUID NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(255) UNIQUE NOT NULL,
  created_at TIMESTAMP DEFAULT now()
);
```

### inventory-service — V1__init.sql
```sql
CREATE TABLE inventory_service.reservations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  order_id UUID NOT NULL,
  product_id UUID NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(255) UNIQUE NOT NULL,
  created_at TIMESTAMP DEFAULT now()
);
```

### notification-service — V1__init.sql
```sql
CREATE TABLE notification_service.notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  order_id UUID NOT NULL,
  type VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(255) UNIQUE NOT NULL,
  sent_at TIMESTAMP
);
```

### JPA Entities
Create `@Entity` classes mapping to each table. Use `@Column` annotations. Enums for status fields:
- `SagaStatus`: `STARTED, ORDER_PENDING, PAYMENT_PENDING, INVENTORY_PENDING, NOTIFICATION_PENDING, COMPLETED, COMPENSATING, COMPENSATED, FAILED`
- `OrderStatus`: `CREATED, CANCELLED`
- `PaymentStatus`: `CHARGED, REFUNDED, FAILED`
- `ReservationStatus`: `RESERVED, RELEASED, FAILED`
- `NotificationStatus`: `SENT, FAILED`

---

## Phase 3 — RabbitMQ Topology

### Create in orchestrator: `RabbitMQConfig.java`

```java
@Configuration
public class RabbitMQConfig {

  public static final String ORDER_COMMANDS_EX = "order.commands";
  public static final String PAYMENT_COMMANDS_EX = "payment.commands";
  public static final String INVENTORY_COMMANDS_EX = "inventory.commands";
  public static final String NOTIFICATION_COMMANDS_EX = "notification.commands";
  public static final String SAGA_EVENTS_EX = "saga.events";
  public static final String DLX = "saga.dlx";

  public static final String ORDER_QUEUE = "order.commands.queue";
  public static final String PAYMENT_QUEUE = "payment.commands.queue";
  public static final String INVENTORY_QUEUE = "inventory.commands.queue";
  public static final String NOTIFICATION_QUEUE = "notification.commands.queue";
  public static final String SAGA_EVENTS_QUEUE = "saga.events.queue";

  @Bean
  public TopicExchange sagaEventsExchange() { return new TopicExchange(SAGA_EVENTS_EX); }

  @Bean
  public DirectExchange dlxExchange() { return new DirectExchange(DLX); }

  private Map<String, Object> dlqArgs(String dlqName) {
    return Map.of(
      "x-dead-letter-exchange", DLX,
      "x-dead-letter-routing-key", dlqName,
      "x-message-ttl", 30000
    );
  }

  // Declare all 4 command exchanges as DirectExchange
  // Declare all 4 command queues with dlqArgs
  // Declare DLQ queues: order.commands.queue.dlq, payment.commands.queue.dlq, etc.
  // Bind each queue to its exchange with routing key = queue name
  // Bind saga.events.queue to saga.events exchange with routing key "#"
}
```

### Message Envelopes (shared records, put in each module)

```java
public record SagaCommand(
  String sagaId,
  String commandType,
  String idempotencyKey,   // sagaId + ":" + commandType
  Map<String, Object> payload,
  Instant issuedAt
) {}

public record SagaEvent(
  String sagaId,
  String eventType,
  boolean success,
  Map<String, Object> payload,
  String failureReason,
  Instant occurredAt
) {}
```

Command types (constants): `ORDER_CREATE, ORDER_CANCEL, PAYMENT_CHARGE, PAYMENT_REFUND, INVENTORY_RESERVE, INVENTORY_RELEASE, NOTIFICATION_SEND`

Event types (constants): `ORDER_CREATED, ORDER_CREATION_FAILED, ORDER_CANCELLED, PAYMENT_CHARGED, PAYMENT_CHARGE_FAILED, PAYMENT_REFUNDED, INVENTORY_RESERVED, INVENTORY_RESERVATION_FAILED, INVENTORY_RELEASED, NOTIFICATION_SENT`

### RabbitMQ Publisher (orchestrator)

```java
@Component
public class SagaCommandPublisher {
  private final RabbitTemplate rabbitTemplate;
  public void publishOrderCommand(SagaCommand cmd) {
    rabbitTemplate.convertAndSend(ORDER_COMMANDS_EX, ORDER_QUEUE, cmd);
  }
  // Same pattern for payment, inventory, notification
}
```

---

## Phase 4 — Saga Orchestrator (Core Logic)

### OrderController
```java
@PostMapping("/orders")
@ResponseStatus(HttpStatus.ACCEPTED)
public Map<String, String> createOrder(@RequestBody OrderRequest request) {
  String sagaId = sagaOrchestrator.startSaga(request);
  return Map.of("sagaId", sagaId, "status", "ACCEPTED");
}

@GetMapping("/orders/{sagaId}")
public SagaInstance getSagaStatus(@PathVariable UUID sagaId) {
  return sagaRepository.findById(sagaId).orElseThrow();
}
```

### SagaOrchestrator — startSaga
```java
@Transactional
public String startSaga(OrderRequest req) {
  SagaInstance saga = new SagaInstance();
  saga.setStatus(SagaStatus.STARTED);
  saga.setPayload(toJson(req));
  sagaRepository.save(saga);
  saga.setStatus(SagaStatus.ORDER_PENDING);
  sagaRepository.save(saga);
  publisher.publishOrderCommand(new SagaCommand(
    saga.getId().toString(), ORDER_CREATE,
    saga.getId() + ":" + ORDER_CREATE,
    Map.of("customerId", req.customerId(), "productId", req.productId(),
           "quantity", req.quantity(), "totalAmount", req.totalAmount()),
    Instant.now()
  ));
  return saga.getId().toString();
}
```

### SagaOrchestrator — handleEvent
```java
@RabbitListener(queues = "#{T(com.orderorchestration.config.RabbitMQConfig).SAGA_EVENTS_QUEUE}")
@Transactional
public void handleEvent(SagaEvent event) {
  SagaInstance saga = sagaRepository.findById(UUID.fromString(event.sagaId())).orElseThrow();
  switch (event.eventType()) {
    case ORDER_CREATED                -> onOrderCreated(saga, event);
    case ORDER_CREATION_FAILED        -> onOrderFailed(saga, event);
    case PAYMENT_CHARGED              -> onPaymentCharged(saga, event);
    case PAYMENT_CHARGE_FAILED        -> onPaymentFailed(saga, event);
    case PAYMENT_REFUNDED             -> onPaymentRefunded(saga, event);
    case INVENTORY_RESERVED           -> onInventoryReserved(saga, event);
    case INVENTORY_RESERVATION_FAILED -> onInventoryFailed(saga, event);
    case INVENTORY_RELEASED           -> onInventoryReleased(saga, event);
    case ORDER_CANCELLED              -> onOrderCancelled(saga, event);
    case NOTIFICATION_SENT            -> onNotificationSent(saga, event);
  }
}
```

### State Transitions (implement each as a private method)
```
onOrderCreated:       status=PAYMENT_PENDING   → publish PAYMENT_CHARGE
onOrderFailed:        status=FAILED            → set failureReason
onPaymentCharged:     status=INVENTORY_PENDING → publish INVENTORY_RESERVE
onPaymentFailed:      status=COMPENSATING      → publish ORDER_CANCEL
onPaymentRefunded:    status=COMPENSATING      → publish ORDER_CANCEL
onInventoryReserved:  status=NOTIFICATION_PENDING → publish NOTIFICATION_SEND
onInventoryFailed:    status=COMPENSATING      → publish PAYMENT_REFUND
onInventoryReleased:  no-op (wait for payment refund)
onOrderCancelled:     status=COMPENSATED
onNotificationSent:   status=COMPLETED
```

Rule: `sagaRepository.save(saga)` MUST be called before every `publisher.publish*()` call.

---

## Phase 5 — Service Handlers (All 4 Services)

Each service follows this exact pattern:

```java
@RabbitListener(queues = "<service>.commands.queue")
public void handleCommand(SagaCommand cmd) {
  // 1. Idempotency check
  if (repository.existsByIdempotencyKey(cmd.idempotencyKey())) {
    republishPreviousResult(cmd.idempotencyKey()); return;
  }
  // 2. Execute (10% random failure on dev profile)
  boolean success = executeLogic(cmd);
  // 3. Persist
  persistResult(cmd, success);
  // 4. Publish event
  rabbitTemplate.convertAndSend(SAGA_EVENTS_EX, "#", new SagaEvent(
    cmd.sagaId(), success ? SUCCESS_EVENT : FAILURE_EVENT, success,
    Map.of(), success ? null : "Simulated failure", Instant.now()
  ));
}
```

**order-service:** `ORDER_CREATE` → insert order, emit `ORDER_CREATED`/`ORDER_CREATION_FAILED`. `ORDER_CANCEL` → update status, emit `ORDER_CANCELLED`.

**payment-service:** `PAYMENT_CHARGE` → insert payment, emit `PAYMENT_CHARGED`/`PAYMENT_CHARGE_FAILED`. `PAYMENT_REFUND` → update status, emit `PAYMENT_REFUNDED`.

**inventory-service:** `INVENTORY_RESERVE` → insert reservation, emit `INVENTORY_RESERVED`/`INVENTORY_RESERVATION_FAILED`. `INVENTORY_RELEASE` → update status, emit `INVENTORY_RELEASED`.

**notification-service:** `NOTIFICATION_SEND` → insert notification, emit `NOTIFICATION_SENT`. No compensation. Orchestrator marks COMPLETED regardless of failure.

---

## Phase 6 — Resilience4j Circuit Breakers

Add to orchestrator `application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      orderService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
      paymentService: { slidingWindowSize: 10, failureRateThreshold: 50, waitDurationInOpenState: 30s, permittedNumberOfCallsInHalfOpenState: 3 }
      inventoryService: { slidingWindowSize: 10, failureRateThreshold: 50, waitDurationInOpenState: 30s, permittedNumberOfCallsInHalfOpenState: 3 }
  retry:
    instances:
      defaultRetry:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

Wrap each `publisher.publish*Command()` with `@CircuitBreaker(name = "orderService", fallbackMethod = "fallback")`. Fallback: set saga status to `WAITING_FOR_CIRCUIT`, save, log WARN.

Add `@Scheduled(fixedDelay = 120000)` recovery job: find all `WAITING_FOR_CIRCUIT` sagas older than 2 minutes → mark `FAILED`.

---

## Phase 7 — OpenTelemetry

### docker-compose additions
```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    volumes: ["./otel-config.yaml:/etc/otel/config.yaml"]
    command: ["--config=/etc/otel/config.yaml"]
    ports: ["4317:4317"]

  tempo:
    image: grafana/tempo:latest
    command: ["-config.file=/etc/tempo.yaml"]
    volumes: ["./tempo-config.yaml:/etc/tempo.yaml"]
    ports: ["3200:3200"]

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment: { GF_AUTH_ANONYMOUS_ENABLED: "true" }
    volumes:
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
```

### otel-config.yaml
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
exporters:
  otlp:
    endpoint: tempo:4317
    tls:
      insecure: true
service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [otlp]
```

### Add to all services in docker-compose
```yaml
environment:
  JAVA_TOOL_OPTIONS: >-
    -javaagent:/otel/opentelemetry-javaagent.jar
    -Dotel.service.name=${SERVICE_NAME}
    -Dotel.exporter.otlp.endpoint=http://otel-collector:4317
    -Dotel.propagators=tracecontext,baggage
volumes:
  - otel-agent:/otel:ro
```

Add shared volume `otel-agent` containing the downloaded `opentelemetry-javaagent.jar`.

### Bottleneck profile (payment-service application.yml)
```yaml
spring:
  config:
    activate:
      on-profile: bottleneck
  datasource:
    hikari:
      maximum-pool-size: 2
```

Run with `SPRING_PROFILES_ACTIVE=bottleneck` to reproduce the 140ms connection-pool spike. Observe in Grafana traces. Remove profile to show fix.

---

## Phase 8 — Load Testing (k6)

### k6/load-test.js
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 167 },
    { duration: '5m', target: 167 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<320'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post('http://localhost:8080/orders',
    JSON.stringify({
      customerId: `cust-${Math.random().toString(36).slice(2,9)}`,
      productId: ['prod-aaa','prod-bbb','prod-ccc'][Math.floor(Math.random()*3)],
      quantity: Math.ceil(Math.random() * 5),
      totalAmount: (Math.random() * 200 + 10).toFixed(2),
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'accepted': (r) => r.status === 202 });
  sleep(0.006);
}
```

### k6/chaos-test.js
```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 50, duration: '3m', thresholds: { http_req_failed: ['rate<0.15'] } };

export default function () {
  const forceFailure = Math.random() < 0.1;
  const res = http.post('http://localhost:8080/orders',
    JSON.stringify({ customerId: 'cust-chaos', productId: 'prod-aaa', quantity: 1, totalAmount: '50.00' }),
    { headers: { 'Content-Type': 'application/json', ...(forceFailure ? { 'X-Force-Payment-Failure': 'true' } : {}) } }
  );
  check(res, { 'accepted': (r) => r.status === 202 });
}
```

In payment-service: read `X-Force-Payment-Failure` header via a request-scoped filter. Active on `dev` profile only.

---

## Phase 9 — Deployment

### .github/workflows/deploy.yml
```yaml
name: Build and Push
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - run: mvn -B package -DskipTests
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - run: |
          for svc in orchestrator order-service payment-service inventory-service notification-service; do
            docker build -t ghcr.io/${{ github.repository_owner }}/order-orchestration/$svc:latest ./$svc
            docker push ghcr.io/${{ github.repository_owner }}/order-orchestration/$svc:latest
          done
```

### railway.toml (each service directory)
```toml
[build]
builder = "DOCKERFILE"
dockerfilePath = "Dockerfile"

[deploy]
healthcheckPath = "/actuator/health"
healthcheckTimeout = 30
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

### Free Tier Map
- PostgreSQL: Railway built-in Postgres plugin
- RabbitMQ: CloudAMQP Little Lemur (cloudamqp.com) — free, 1M msg/month
- Services: Railway (deploy from ghcr.io images)
- Tracing: Grafana Cloud free tier (50GB traces)

### CloudAMQP free tier — add to each service application.yml
```yaml
spring:
  rabbitmq:
    cache:
      channel:
        size: 1
    connection-timeout: 10000
```

### Railway env vars (set per service in dashboard)
```
DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
DB_SCHEMA=<service schema>
RABBITMQ_URL=<CloudAMQP amqp URL>
HIKARI_POOL_SIZE=5
SERVICE_NAME=<name>
SPRING_PROFILES_ACTIVE=prod
JAVA_TOOL_OPTIONS=-Dotel.exporter.otlp.endpoint=<Grafana Cloud endpoint> -Dotel.exporter.otlp.headers=Authorization=Basic <base64 key> -Dotel.service.name=<name>
```

---

## Constraints (Non-Negotiable)

1. `sagaRepository.save()` MUST precede every `publisher.publish*()` call.
2. Every command handler checks idempotency key before executing.
3. No cross-schema reads. Each service touches only its own schema.
4. Notification failure does NOT trigger compensation. Mark saga COMPLETED anyway.
5. Store enum values as `VARCHAR(50)`. No Postgres native enum types.
6. `X-Force-Payment-Failure` header and random failure simulation: `dev` profile only.

---

## Build Order & Checklist

Build phases sequentially. Verify each before proceeding.

- [ ] Phase 1: All 5 `/actuator/health` return UP, `docker-compose up` clean
- [ ] Phase 2: Flyway migrations applied, tables visible in `\dt`
- [ ] Phase 3: All exchanges and queues visible in RabbitMQ UI (localhost:15672)
- [ ] Phase 4: `POST /orders` → 202 + sagaId; `GET /orders/{sagaId}` → COMPLETED
- [ ] Phase 5: Duplicate command → idempotent (no double-charge in payments table)
- [ ] Phase 6: Stop a service → orchestrator logs circuit OPEN; saga moves to WAITING_FOR_CIRCUIT
- [ ] Phase 7: Traces visible in Grafana with spans from all 5 services
- [ ] Phase 8: `k6 run k6/load-test.js` → p95 < 320ms, error rate < 1%
- [ ] Phase 9: All services live on Railway, traces flowing to Grafana Cloud
