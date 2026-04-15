# Microservices Design Patterns Platform

A production-grade, multi-module Java 21 / Spring Boot 3.2 platform that demonstrates **12 enterprise microservice design patterns** through a realistic e-commerce order-processing domain. Every pattern is implemented end-to-end with real infrastructure (Kafka, PostgreSQL, MongoDB, Redis, Eureka) — not stubs or toy examples.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Design Patterns Catalog](#design-patterns-catalog)
   - [1. API Gateway](#1-api-gateway)
   - [2. Service Discovery](#2-service-discovery)
   - [3. Saga (Orchestration)](#3-saga-orchestration)
   - [4. Event Sourcing](#4-event-sourcing)
   - [5. CQRS](#5-cqrs-command-query-responsibility-segregation)
   - [6. Circuit Breaker](#6-circuit-breaker)
   - [7. Bulkhead](#7-bulkhead)
   - [8. Retry](#8-retry)
   - [9. Database per Service](#9-database-per-service)
   - [10. Sidecar](#10-sidecar)
   - [11. API Composition](#11-api-composition)
   - [12. Strangler Fig](#12-strangler-fig)
3. [Technology Stack](#technology-stack)
4. [Module Structure](#module-structure)
5. [Getting Started](#getting-started)
6. [API Reference](#api-reference)
7. [Testing the Patterns](#testing-the-patterns)
8. [Observability](#observability)
9. [Production Considerations](#production-considerations)
10. [Further Reading](#further-reading)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│                    (Web / Mobile / Third-party)                              │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY  :8080                                   │
│  ┌──────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────┐ ┌────────────┐  │
│  │   JWT    │ │  Rate Limit  │ │  Circuit   │ │  Request │ │  Fallback  │  │
│  │  Auth    │ │   (Redis)    │ │  Breaker   │ │  Tracing │ │  Handler   │  │
│  └──────────┘ └──────────────┘ └────────────┘ └──────────┘ └────────────┘  │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
        ┌───────────────────┐   ┌─────────────────────┐
        │ SERVICE DISCOVERY │   │    KAFKA CLUSTER     │
        │  (Eureka) :8761   │   │       :9092          │
        └───────────────────┘   └──────────┬──────────┘
                                           │
        ┌──────────────┬───────────────┬───┴──────────┬──────────────┐
        ▼              ▼               ▼              ▼              ▼
┌──────────────┐┌─────────────┐┌──────────────┐┌──────────────┐┌────────────────┐
│    ORDER     ││   PAYMENT   ││  INVENTORY   ││ NOTIFICATION ││  STRANGLER     │
│   SERVICE    ││   SERVICE   ││   SERVICE    ││   SERVICE    ││  FIG SERVICE   │
│    :8081     ││    :8082    ││    :8083     ││    :8084     ││    :8086       │
│              ││             ││              ││              ││                │
│ • Saga       ││ • Circuit   ││ • DB per Svc ││ • Sidecar    ││ • Legacy       │
│ • Event      ││   Breaker   ││ • Saga       ││   Pattern    ││ • Modern       │
│   Sourcing   ││ • Bulkhead  ││   Partici-   ││ • Event      ││ • Facade       │
│ • CQRS       ││ • Retry     ││   pant       ││   Consumer   ││ • Routing      │
│ • Axon Fwk   ││ • Resilience││ • MongoDB    ││              ││   Strategy     │
│              ││   4j        ││              ││              ││                │
├──────────────┤├─────────────┤├──────────────┤├──────────────┤├────────────────┤
│  PostgreSQL  ││ PostgreSQL  ││   MongoDB    ││ PostgreSQL   ││  PostgreSQL    │
│  (order_db)  ││(payment_db) ││(inventory_db)││(notif_db)    ││ (strangler_db) │
└──────────────┘└─────────────┘└──────────────┘└──────────────┘└────────────────┘

                         ┌──────────────────────┐
                         │  API COMPOSITION SVC  │
                         │        :8085          │
                         │  Aggregates data from │
                         │  all services above   │
                         └──────────────────────┘
```

### Domain Context

The platform models an **e-commerce order fulfillment pipeline**:

1. A customer submits an order via the API Gateway
2. The **Order Service** creates the order (Event Sourced) and starts a **Saga**
3. The Saga orchestrates **Inventory** reservation and **Payment** processing
4. On success/failure, the Saga compensates or completes the order
5. **Notifications** are fired for every state change via Kafka events
6. The **API Composition Service** aggregates cross-service data for read-heavy dashboards
7. The **Strangler Fig Service** demonstrates live migration from legacy to modern code

---

## Design Patterns Catalog

### 1. API Gateway

> **Module:** `api-gateway` | **Port:** 8080

#### What It Is

The API Gateway pattern provides a **single entry point** for all client interactions with the microservices ecosystem. Instead of clients knowing about and communicating with each individual service, they interact with one unified API surface.

#### Why It Matters

In production systems, the gateway handles cross-cutting concerns that would otherwise be duplicated across every service:

- **Authentication and authorization** are enforced once, at the edge
- **Rate limiting** prevents abuse and protects downstream services
- **Request routing** decouples clients from service topology changes
- **Protocol translation** allows internal services to evolve independently
- **Load balancing** distributes traffic across service instances

#### Implementation Deep-Dive

**Technology:** Spring Cloud Gateway (reactive, non-blocking on Project Reactor)

**Route Configuration** (`GatewayConfig.java`):
Each route is defined programmatically with multiple filters chained together:

```java
.route("order-service", r -> r
    .path("/api/orders/**")
    .filters(f -> f
        .circuitBreaker(cb -> cb
            .setName("orderServiceCB")
            .setFallbackUri("forward:/fallback/orders"))
        .requestRateLimiter(rl -> rl
            .setRateLimiter(redisRateLimiter()))
        .rewritePath("/api/orders/(?<segment>.*)", "/api/orders/${segment}")
        .addRequestHeader("X-Gateway-Timestamp", Instant.now().toString())
        .retry(retryConfig -> retryConfig
            .setRetries(3)
            .setMethods(HttpMethod.GET)
            .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR)))
    .uri("lb://order-service"))
```

**Key architectural decisions:**
- Routes use `lb://` URIs for client-side load balancing via Eureka
- Each route has its own circuit breaker with dedicated fallback
- Rate limiting uses Redis as a distributed token bucket (10 requests/second burst to 20)
- The gateway is fully reactive — no thread-per-request model

**Authentication Filter** (`AuthenticationFilter.java`):
A custom `GatewayFilterFactory` that:
1. Extracts JWTs from the `Authorization` header
2. Validates signature and expiration using HMAC-SHA256
3. Injects `X-User-Id` and `X-User-Roles` headers into downstream requests
4. Maintains a configurable allowlist of open endpoints (e.g., `/api/auth/**`)

**Request Tracing** (`RequestTracingFilter.java`):
A `GlobalFilter` at `Ordered.HIGHEST_PRECEDENCE` that generates or propagates `X-Correlation-Id` headers across the entire request lifecycle. Every log line in every service includes this correlation ID, enabling distributed trace reconstruction.

**Fallback Handling** (`FallbackController.java`):
When a circuit breaker trips, the gateway returns a structured degradation response rather than a raw error:

```json
{
  "message": "Order service is temporarily unavailable. Please retry.",
  "timestamp": "2026-04-15T10:30:00Z",
  "retryAfterSeconds": 30
}
```

#### How to Observe It

```bash
# All traffic enters through port 8080
curl http://localhost:8080/api/orders

# Rate limiting headers appear in response
# X-RateLimit-Remaining: 9
# X-RateLimit-Burst-Capacity: 20

# Tracing headers propagate
# X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
# X-Response-Time: 45ms
```

---

### 2. Service Discovery

> **Module:** `service-discovery` | **Port:** 8761

#### What It Is

Service Discovery eliminates hardcoded service URLs. Services **register themselves** at startup and **discover peers** at runtime. When a service instance scales up, crashes, or moves, the registry updates automatically.

#### Why It Matters

In a dynamic container/cloud environment:
- Service instances are ephemeral — IPs change on every deployment
- Horizontal scaling adds/removes instances without configuration changes
- Health-aware routing avoids sending traffic to unhealthy instances
- No need for external load balancers for internal service-to-service calls

#### Implementation Deep-Dive

**Technology:** Netflix Eureka Server (Spring Cloud Netflix)

**Server Configuration** (`application.yml`):
```yaml
eureka:
  server:
    enable-self-preservation: true           # Don't evict instances during network partitions
    renewal-percent-threshold: 0.85          # 85% of instances must heartbeat
    eviction-interval-timer-in-ms: 60000     # Check for expired instances every 60s
    response-cache-update-interval-ms: 30000 # Cache freshness interval
```

**Self-preservation mode** is critical in production: during a network partition, Eureka stops evicting instances rather than cascading failures. This is a deliberate trade-off of stale data over no data.

**Client Registration** (every service's `application.yml`):
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka_secret@localhost:8761/eureka/
    registry-fetch-interval-seconds: 15     # How often clients refresh their local cache
  instance:
    prefer-ip-address: true                  # Register IP instead of hostname
    lease-renewal-interval-in-seconds: 10    # Heartbeat interval
    lease-expiration-duration-in-seconds: 30 # Expire if no heartbeat for 30s
```

**Security** (`SecurityConfig.java`):
The Eureka dashboard and registration endpoints are protected with HTTP Basic authentication. CSRF is disabled for the `/eureka/**` REST API (service-to-service heartbeats are not browser-initiated).

#### How to Observe It

```bash
# Eureka Dashboard
open http://localhost:8761

# REST API — list all registered instances
curl -u eureka:eureka_secret http://localhost:8761/eureka/apps

# Watch services register in real-time after starting them
```

---

### 3. Saga (Orchestration)

> **Module:** `order-service` → `OrderSaga.java`

#### What It Is

The Saga pattern manages **distributed transactions** across multiple services without a global two-phase commit. Instead of an atomic transaction spanning databases, a saga is a sequence of local transactions where each step has a defined **compensating action** that undoes its effect if a later step fails.

#### Why It Matters

In microservices, each service owns its database. There is no distributed `BEGIN TRANSACTION` that spans Order DB, Payment DB, and Inventory DB. The saga ensures **eventual consistency** by:

- Executing steps in sequence (or parallel where safe)
- Rolling back completed steps in reverse order on failure
- Handling partial failures, timeouts, and idempotency

#### Implementation Deep-Dive

**Technology:** Axon Framework Saga support

**Saga Lifecycle** (`OrderSaga.java`):

```
OrderCreatedEvent
    │
    ├──► ReserveInventoryCommand ──► inventory-service
    │         │
    │    ┌────┴────┐
    │    ▼         ▼
    │  RESERVED   FAILED ──► RejectOrderCommand ──► END SAGA
    │    │
    │    ▼
    ├──► ProcessPaymentCommand ──► payment-service
    │         │
    │    ┌────┴────┐
    │    ▼         ▼
    │  PROCESSED  FAILED ──► ReleaseInventoryCommand (compensate)
    │    │                        └──► RejectOrderCommand ──► END SAGA
    │    ▼
    └──► ApproveOrderCommand ──► END SAGA (success)

    ⏱ DeadlineHandler (30 min timeout) ──► compensate all completed steps
```

**Key code structure:**

```java
@Saga
public class OrderSaga {

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent event) {
        this.sagaState = SagaState.INVENTORY_RESERVING;
        commandGateway.send(new ReserveInventoryCommand(
            event.getOrderId(),
            extractProductId(event),
            extractQuantity(event)
        ));
        deadlineManager.schedule(Duration.ofMinutes(30),
            "saga-timeout", event.getOrderId());
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(PaymentFailedEvent event) {
        this.sagaState = SagaState.COMPENSATING;
        // Compensate: release the inventory that was already reserved
        commandGateway.send(new ReleaseInventoryCommand(
            this.reservationId, event.getOrderId(), "Payment failed"));
        commandGateway.send(new RejectOrderCommand(
            event.getOrderId(), "Payment failed: " + event.getReason()));
        SagaLifecycle.end();
    }

    @DeadlineHandler(deadlineName = "saga-timeout")
    public void onTimeout(String orderId) {
        // Compensate ALL completed steps based on current state
        if (sagaState.isAfter(SagaState.INVENTORY_RESERVED)) {
            commandGateway.send(new ReleaseInventoryCommand(...));
        }
        commandGateway.send(new CancelOrderCommand(orderId, "Saga timeout"));
        SagaLifecycle.end();
    }
}
```

**Critical production concerns addressed:**
- **Idempotency:** Saga event handlers check current state before acting
- **Timeouts:** A 30-minute deadline ensures sagas don't hang forever
- **Compensation ordering:** Steps are compensated in reverse to maintain consistency
- **State tracking:** `SagaState` enum tracks exactly which steps completed, enabling precise partial rollback

#### How to Test It

```bash
# Happy path — creates order, reserves inventory, processes payment
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "items": [{"productId": "PROD-001", "productName": "Laptop", "quantity": 1, "unitPrice": 999.99}]
  }'

# Failure path — create order for out-of-stock product to trigger compensation
# The saga will: create order → fail inventory → reject order

# Check saga execution via order history (event sourcing replay)
curl http://localhost:8081/api/orders/{orderId}/history
```

---

### 4. Event Sourcing

> **Module:** `order-service` → `OrderAggregate.java`

#### What It Is

Instead of storing the **current state** of an entity, Event Sourcing stores the **complete sequence of state-changing events**. The current state is derived by replaying all events from the beginning (or from a snapshot).

#### Why It Matters

- **Complete audit trail:** Every state change is recorded as an immutable event
- **Temporal queries:** "What was the order status at 3:00 PM yesterday?"
- **Debugging:** Replay events to reproduce any bug
- **Event replay:** Rebuild read models, populate new services, fix data corruption
- **Decoupling:** Events become the integration contract between services

#### Implementation Deep-Dive

**Technology:** Axon Framework with JPA-backed Event Store

**Aggregate** (`OrderAggregate.java`):

The aggregate is the consistency boundary. All commands target an aggregate, and all events are emitted by an aggregate:

```java
@Aggregate(snapshotTriggerDefinition = "orderSnapshotTrigger")
public class OrderAggregate {

    @AggregateIdentifier
    private String orderId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItem> items;

    @CommandHandler
    public OrderAggregate(CreateOrderCommand cmd) {
        // Validation happens here — before any event is produced
        if (cmd.getItems() == null || cmd.getItems().isEmpty()) {
            throw new BusinessRuleViolationException("Order must have at least one item");
        }
        AggregateLifecycle.apply(new OrderCreatedEvent(
            cmd.getOrderId(), cmd.getCustomerId(),
            cmd.getItems(), cmd.getTotalAmount(), Instant.now()
        ));
    }

    @CommandHandler
    public void handle(ApproveOrderCommand cmd) {
        if (status != OrderStatus.PAYMENT_PROCESSED) {
            throw new BusinessRuleViolationException(
                "Cannot approve order in state: " + status);
        }
        AggregateLifecycle.apply(new OrderApprovedEvent(cmd.getOrderId(), Instant.now()));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent event) {
        this.orderId = event.getOrderId();
        this.status = OrderStatus.CREATED;
        this.totalAmount = event.getTotalAmount();
        // Pure state mutation — no side effects allowed here
    }

    @EventSourcingHandler
    public void on(OrderApprovedEvent event) {
        this.status = OrderStatus.APPROVED;
    }
}
```

**Snapshots** (`AxonConfig.java`):
After every 10 events, a snapshot is taken. When loading the aggregate, Axon loads the latest snapshot + events after it, avoiding full replay:

```java
@Bean
public SnapshotTriggerDefinition orderSnapshotTrigger(Snapshotter snapshotter) {
    return new EventCountSnapshotTriggerDefinition(snapshotter, 10);
}
```

**Event Store structure** (JPA tables created automatically):
- `domain_event_entry` — every event with aggregate ID, sequence number, serialized payload, timestamp
- `snapshot_event_entry` — periodic aggregate state snapshots

#### How to Observe It

```bash
# Create an order
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C1","items":[{"productId":"P1","productName":"Widget","quantity":2,"unitPrice":25.00}]}'

# View the complete event history — this IS the event store
curl http://localhost:8081/api/orders/{orderId}/history

# Response shows every event in sequence:
# [
#   { "eventType": "OrderCreatedEvent", "sequenceNumber": 0, "timestamp": "..." },
#   { "eventType": "OrderApprovedEvent", "sequenceNumber": 1, "timestamp": "..." }
# ]
```

---

### 5. CQRS (Command Query Responsibility Segregation)

> **Module:** `order-service` → `command/` + `query/`

#### What It Is

CQRS splits the application into two distinct models:
- **Command side:** Handles writes (create, update, delete) through the aggregate
- **Query side:** Handles reads from a separately optimized projection (read model)

#### Why It Matters

Reads and writes have fundamentally different optimization needs:

| Concern | Command Side | Query Side |
|---------|-------------|------------|
| Model | Domain aggregates (event-sourced) | Flat, denormalized views |
| Storage | Event store (append-only) | Relational/document (read-optimized) |
| Scaling | Scaled for write throughput | Scaled for read throughput (can add replicas) |
| Consistency | Strongly consistent | Eventually consistent |
| Indexing | Minimal (aggregate ID) | Rich (multiple query patterns) |

#### Implementation Deep-Dive

**Command Side** — `OrderCommandController.java` → `CommandGateway` → `OrderAggregate`:

```java
@PostMapping
public ResponseEntity<ApiResponse<String>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    String orderId = UUID.randomUUID().toString();
    commandGateway.sendAndWait(new CreateOrderCommand(orderId, request.getCustomerId(), ...));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Order created", orderId));
}
```

**Query Side** — `OrderQueryController.java` → `QueryGateway` → `OrderProjection.java`:

```java
@GetMapping("/{orderId}")
public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {
    OrderView view = queryGateway.query(
        new FindOrderQuery(orderId), ResponseTypes.instanceOf(OrderView.class)).join();
    return ResponseEntity.ok(ApiResponse.success("Order retrieved", toResponse(view)));
}
```

**Projection** (`OrderProjection.java`):
The projection subscribes to the event stream and maintains a denormalized read model:

```java
@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

    @EventHandler
    public void on(OrderCreatedEvent event) {
        OrderView view = new OrderView();
        view.setOrderId(event.getOrderId());
        view.setCustomerId(event.getCustomerId());
        view.setStatus(OrderStatus.CREATED);
        view.setTotalAmount(event.getTotalAmount());
        orderViewRepository.save(view);
    }

    @QueryHandler
    public OrderView handle(FindOrderQuery query) {
        return orderViewRepository.findByOrderId(query.getOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }
}
```

**Key architectural decisions:**
- Command and query controllers are **separate classes** with separate endpoints
- The read model is a flat JPA entity (`OrderView`) optimized for queries — not the event-sourced aggregate
- Projection runs as a tracking event processor, allowing independent scaling and replay
- Eventual consistency gap between command and query is typically < 100ms

---

### 6. Circuit Breaker

> **Module:** `payment-service` → `PaymentService.java`

#### What It Is

The Circuit Breaker pattern prevents cascading failures by **detecting failures and cutting off traffic** to a failing downstream service. Like an electrical circuit breaker, it "trips" when failure rates exceed a threshold.

#### Why It Matters

Without circuit breakers, a failing downstream service causes:
- **Thread exhaustion:** All threads block waiting for timeouts
- **Cascading failures:** Callers of the blocked service also start failing
- **Recovery delay:** Even after the downstream recovers, the backlog of queued requests causes continued overload

#### Implementation Deep-Dive

**Technology:** Resilience4j (successor to Hystrix)

**State Machine:**

```
         failure rate          permitted calls         failure rate
         > threshold           succeed                 still high
  ┌──────────────┐     ┌────────────────────┐    ┌──────────────────┐
  │              │     │                    │    │                  │
  ▼              │     ▼                    │    ▼                  │
CLOSED ────────► OPEN ────────► HALF_OPEN ──┴──► OPEN
  ▲                                    │
  │       all permitted calls          │
  │       succeed                      │
  └────────────────────────────────────┘
```

**Configuration** (`application.yml`):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentGateway:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10              # Evaluate last 10 calls
        failure-rate-threshold: 50           # Trip at 50% failure rate
        wait-duration-in-open-state: 10s     # Stay open for 10s before trying again
        permitted-number-of-calls-in-half-open-state: 5  # Test with 5 calls
        slow-call-duration-threshold: 2s     # Calls > 2s count as "slow"
        slow-call-rate-threshold: 80         # Trip if 80% of calls are slow
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignore-exceptions:
          - com.microservices.common.exception.BusinessRuleViolationException
```

**Annotated Service Method:**

```java
@CircuitBreaker(name = "paymentGateway", fallbackMethod = "processPaymentFallback")
public PaymentResponse processPayment(ProcessPaymentCommand command) {
    PaymentGatewayResponse gatewayResponse = paymentGatewayService.processPayment(...);
    // ... persist and return
}

private PaymentResponse processPaymentFallback(ProcessPaymentCommand command,
                                                CallNotPermittedException ex) {
    log.warn("Circuit breaker OPEN for payment gateway. Order: {}", command.getOrderId());
    // Return a queued/pending response instead of failing
    return PaymentResponse.builder()
        .status(PaymentStatus.PENDING)
        .message("Payment queued — gateway temporarily unavailable")
        .build();
}
```

**Simulated Failures** (`PaymentGatewayServiceImpl.java`):
The gateway deliberately fails ~30% of requests (20% timeout + 10% exception) to demonstrate the circuit breaker tripping and recovering in real-time.

#### How to Observe It

```bash
# Monitor circuit breaker state
curl http://localhost:8082/api/payments/resilience/status

# Response:
# {
#   "circuitBreakerState": "CLOSED",
#   "failureRate": 30.0,
#   "bufferedCalls": 10,
#   "failedCalls": 3,
#   "slowCalls": 1
# }

# Actuator endpoint for detailed metrics
curl http://localhost:8082/actuator/circuitbreakers

# Actuator events stream
curl http://localhost:8082/actuator/circuitbreakerevents
```

---

### 7. Bulkhead

> **Module:** `payment-service` → `PaymentService.java`

#### What It Is

The Bulkhead pattern **isolates failures by limiting concurrent access** to a component. Named after ship bulkheads that contain flooding to one compartment, this pattern ensures a slow or failing component doesn't consume all available resources.

#### Why It Matters

Without bulkheads, a single slow dependency can:
- Consume all threads in a thread pool, starving other operations
- Cause memory exhaustion from queued requests
- Make the entire service unresponsive, not just the affected operation

#### Implementation Deep-Dive

**Technology:** Resilience4j Bulkhead

**Two Types Implemented:**

| Type | How It Works | Use Case |
|------|-------------|----------|
| **Semaphore** | Limits concurrent calls (no extra threads) | Lightweight isolation |
| **Thread Pool** | Dedicated thread pool per operation | Strong isolation with backpressure via queue |

**Configuration:**

```yaml
resilience4j:
  bulkhead:
    instances:
      paymentGateway:
        max-concurrent-calls: 10        # Max 10 parallel payment calls
        max-wait-duration: 500ms        # Wait max 500ms if at capacity
  thread-pool-bulkhead:
    instances:
      paymentGateway:
        core-thread-pool-size: 5
        max-thread-pool-size: 10
        queue-capacity: 20
        keep-alive-duration: 100ms
```

**Effect:** Even if the payment gateway is slow, only 10 threads are affected. The remaining thread pool serves other endpoints (health checks, status queries, refunds) normally.

```java
@Bulkhead(name = "paymentGateway", fallbackMethod = "processPaymentBulkheadFallback")
public PaymentResponse processPayment(ProcessPaymentCommand command) { ... }

private PaymentResponse processPaymentBulkheadFallback(ProcessPaymentCommand command,
                                                        BulkheadFullException ex) {
    log.error("Bulkhead full — all {} slots occupied", 10);
    throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS,
        "PAYMENT_CAPACITY_EXCEEDED", "Payment processing at capacity");
}
```

#### How to Observe It

```bash
# Check bulkhead utilization
curl http://localhost:8082/api/payments/resilience/status
# Shows: availableConcurrentCalls, maxAllowedConcurrentCalls

curl http://localhost:8082/actuator/bulkheads
```

---

### 8. Retry

> **Module:** `payment-service` → `PaymentService.java`

#### What It Is

The Retry pattern automatically **re-executes failed operations** with configurable backoff strategies. It handles transient failures (network blips, temporary overloads) without burdening the caller.

#### Why It Matters

Many failures in distributed systems are transient:
- Network timeouts due to GC pauses
- Database connection pool exhaustion during traffic spikes
- HTTP 503 from a service that's mid-deployment

A well-configured retry with exponential backoff resolves most transient failures without human intervention.

#### Implementation Deep-Dive

**Configuration:**

```yaml
resilience4j:
  retry:
    instances:
      paymentGateway:
        max-attempts: 3
        wait-duration: 1s                                # Initial wait
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2                 # 1s → 2s → 4s
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - com.microservices.common.exception.BusinessRuleViolationException
```

**Why `ignore-exceptions` matters:** You must NOT retry business rule violations (insufficient funds, invalid card). These are deterministic — retrying won't change the outcome. Only retry infrastructure/transient failures.

**Interaction with Circuit Breaker:**
Resilience4j applies decorators in a specific order: `Retry → CircuitBreaker → Bulkhead → TimeLimiter`. This means:
1. The call is attempted
2. If it fails, Retry retries (up to 3 times with exponential backoff)
3. Each attempt is recorded by the Circuit Breaker
4. If the Circuit Breaker trips, no more retries are attempted

```java
@Retry(name = "paymentGateway", fallbackMethod = "processPaymentRetryFallback")
@CircuitBreaker(name = "paymentGateway", fallbackMethod = "processPaymentFallback")
public PaymentResponse processPayment(ProcessPaymentCommand command) { ... }

private PaymentResponse processPaymentRetryFallback(ProcessPaymentCommand command, Exception ex) {
    log.error("All {} retry attempts exhausted for order: {}", 3, command.getOrderId(), ex);
    // Persist as FAILED with retry context
    return persistFailedPayment(command, "All retries exhausted: " + ex.getMessage());
}
```

#### How to Observe It

```bash
# Retry event stream
curl http://localhost:8082/actuator/retryevents

# Metrics
curl http://localhost:8082/actuator/metrics/resilience4j.retry.calls
# Tags: kind=successful_without_retry, successful_with_retry, failed_with_retry, failed_without_retry
```

---

### 9. Database per Service

> **Module:** `inventory-service` (MongoDB) vs `order-service` (PostgreSQL)

#### What It Is

Each microservice owns a **private database** that only it can access. No other service may read from or write to another service's database. Services communicate exclusively through APIs or events.

#### Why It Matters

Shared databases create **tight coupling** that undermines the entire point of microservices:
- Schema changes in one service break others
- Performance issues in one service's queries impact all services
- You can't scale, deploy, or migrate databases independently
- Domain boundaries become blurred when any service can query any table

#### Implementation Deep-Dive

**Polyglot Persistence in this project:**

| Service | Database | Rationale |
|---------|----------|-----------|
| Order Service | PostgreSQL | Relational data, ACID transactions for event store |
| Payment Service | PostgreSQL | Financial data requires strong consistency |
| Inventory Service | **MongoDB** | Document model fits product catalogs, flexible schema for product attributes |
| Notification Service | PostgreSQL | Simple relational storage for notification records |
| Strangler Fig Service | PostgreSQL | Legacy compatibility |

**Inventory Service with MongoDB** (`Product.java`):

```java
@Document(collection = "products")
@CompoundIndex(name = "sku_warehouse_idx", def = "{'sku': 1, 'warehouse': 1}", unique = true)
public class Product {
    @Id
    private String id;
    @Indexed(unique = true)
    private String sku;
    private String name;
    private int availableQuantity;
    private int reservedQuantity;
    private BigDecimal unitPrice;
    // MongoDB's flexible schema allows adding fields without migrations
}
```

**Atomic stock operations** use MongoDB's `$inc` operator for lock-free concurrency:

```java
@Query("{'_id': ?0, 'availableQuantity': {$gte: ?1}}")
@Update("{'$inc': {'availableQuantity': ?#{-[1]}, 'reservedQuantity': ?1}}")
long reserveStock(String productId, int quantity);
```

This atomically decrements `availableQuantity` and increments `reservedQuantity` in a single operation — no locks, no race conditions.

**Data isolation enforcement:**
- Each service's `application.yml` points to a **different database** (different host/port/db name)
- Docker Compose provisions **separate PostgreSQL containers** per service
- No service has credentials to access another service's database

#### How Cross-Service Data Access Works

Since services can't query each other's databases, they communicate via:
1. **Kafka events** for asynchronous data propagation
2. **API Composition Service** for synchronous read aggregation
3. **WebClient calls** for direct service-to-service queries

---

### 10. Sidecar

> **Module:** `notification-service` → `sidecar/`

#### What It Is

The Sidecar pattern attaches a helper process alongside the main service to handle **cross-cutting concerns** (logging, metrics, configuration, health checking) without polluting the core business logic.

#### Why It Matters

Cross-cutting concerns like structured logging, metrics collection, and dynamic configuration would otherwise:
- Clutter every service with boilerplate
- Create inconsistencies when each team implements them differently
- Make it hard to update observability standards across the fleet
- Couple business code to infrastructure libraries

In production, sidecars are typically separate containers (Envoy, Datadog Agent, Fluentd). This implementation demonstrates the pattern within the JVM using Spring AOP and components.

#### Implementation Deep-Dive

**Four Sidecar Components:**

**1. Logging Sidecar** (`SidecarLoggingAspect.java`):
An `@Aspect` that intercepts all service methods and enriches logs with structured context:

```java
@Around("execution(* com.microservices.notification.service..*(..))")
public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    String correlationId = MDC.get("correlationId");
    MDC.put("component", "notification-service");
    MDC.put("operation", joinPoint.getSignature().getName());

    log.info("Entering {} with args: {}", joinPoint.getSignature(), sanitize(joinPoint.getArgs()));
    long start = System.nanoTime();
    try {
        Object result = joinPoint.proceed();
        log.info("Completed {} in {}ms", joinPoint.getSignature(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return result;
    } catch (Exception ex) {
        log.error("Failed {} after {}ms: {}", joinPoint.getSignature(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), ex.getMessage());
        throw ex;
    }
}
```

**2. Metrics Sidecar** (`SidecarMetricsCollector.java`):
Collects Prometheus-compatible metrics using Micrometer:

```java
// Tracks per-channel, per-type notification metrics
notificationsSent = Counter.builder("sidecar.notifications.sent")
    .tag("channel", channel.name())
    .tag("type", type.name())
    .register(meterRegistry);

notificationLatency = Timer.builder("sidecar.notifications.latency")
    .tag("channel", channel.name())
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry);

queueSize = Gauge.builder("sidecar.notifications.queue.size", this, self -> self.getPendingCount())
    .register(meterRegistry);
```

**3. Config Sidecar** (`SidecarConfigProvider.java`):
Polls an external configuration source (simulated) every 30 seconds:

```java
@Scheduled(fixedRate = 30000)
public void refreshConfiguration() {
    // In production, this polls Consul, etcd, AWS AppConfig, etc.
    Map<String, Object> newConfig = fetchExternalConfig();
    if (!newConfig.equals(currentConfig)) {
        log.info("Configuration changed: {}", diff(currentConfig, newConfig));
        currentConfig = newConfig;
        eventPublisher.publishEvent(new ConfigChangedEvent(newConfig));
    }
}

public boolean isChannelEnabled(NotificationChannel channel) {
    return (boolean) currentConfig.getOrDefault("channel." + channel.name() + ".enabled", true);
}

public int getRateLimit(NotificationChannel channel) {
    return (int) currentConfig.getOrDefault("channel." + channel.name() + ".rateLimit", 100);
}
```

**4. Health Sidecar** (`SidecarHealthIndicator.java`):
Performs deep health checks beyond simple HTTP:

```java
@Override
public Health health() {
    Health.Builder builder = new Health.Builder();
    Map<String, Object> details = new HashMap<>();

    details.put("kafka", checkKafkaConnectivity());     // Verify Kafka broker reachable
    details.put("database", checkDatabaseConnectivity()); // Verify DB connection pool
    details.put("mailServer", checkMailServer());         // Verify SMTP server
    details.put("configFreshness", getConfigAge());       // How old is the cached config

    boolean allHealthy = details.values().stream()
        .allMatch(v -> "UP".equals(v.toString()) || v instanceof Number);

    return (allHealthy ? builder.up() : builder.down()).withDetails(details).build();
}
```

---

### 11. API Composition

> **Module:** `api-composition-service`

#### What It Is

API Composition implements a service that **joins data from multiple microservices** into a single response. Since each service owns its database (Database per Service pattern), cross-service queries require an aggregation layer.

#### Why It Matters

Clients frequently need data that spans multiple services:
- "Show me this order with its payment status, inventory status, and notifications"
- "Build a customer dashboard with recent orders, payment history, and alert count"

Without API Composition, clients would need to make N calls and join data themselves — leading to chatty APIs, complex client logic, and poor mobile performance.

#### Implementation Deep-Dive

**Technology:** WebClient (non-blocking) + CompletableFuture + Caffeine Cache

**Parallel Aggregation** (`OrderCompositionService.java`):

```java
public OrderDetailsComposite getOrderDetails(String orderId) {
    long start = System.nanoTime();
    Map<String, Long> latencies = new ConcurrentHashMap<>();

    CompletableFuture<OrderSummary> orderFuture = CompletableFuture.supplyAsync(() -> {
        long t = System.nanoTime();
        OrderSummary result = orderClient.getOrder(orderId);
        latencies.put("order-service", Duration.ofNanos(System.nanoTime() - t).toMillis());
        return result;
    });

    CompletableFuture<PaymentSummary> paymentFuture = CompletableFuture.supplyAsync(() -> {
        long t = System.nanoTime();
        PaymentSummary result = paymentClient.getPaymentsByOrder(orderId);
        latencies.put("payment-service", Duration.ofNanos(System.nanoTime() - t).toMillis());
        return result;
    });

    // All four calls execute in parallel
    CompletableFuture.allOf(orderFuture, paymentFuture, inventoryFuture, notificationFuture)
        .join();

    return OrderDetailsComposite.builder()
        .order(orderFuture.get())
        .payment(paymentFuture.get())
        .inventory(inventoryFuture.get())
        .notifications(notificationFuture.get())
        .compositionMeta(CompositionMetadata.builder()
            .perServiceLatencyMs(latencies)
            .totalLatencyMs(Duration.ofNanos(System.nanoTime() - start).toMillis())
            .respondedServices(latencies.keySet())
            .build())
        .build();
}
```

**Partial Failure Handling** (`CompositionErrorHandler.java`):
If one downstream service fails, the composition returns partial data with error indicators rather than failing entirely:

```json
{
  "order": { "orderId": "ORD-001", "status": "APPROVED" },
  "payment": { "status": "COMPLETED", "amount": 999.99 },
  "inventory": null,
  "compositionMeta": {
    "respondedServices": ["order-service", "payment-service", "notification-service"],
    "failedServices": {
      "inventory-service": "CircuitBreaker 'inventoryService' is OPEN"
    },
    "totalLatencyMs": 120,
    "perServiceLatencyMs": {
      "order-service": 45,
      "payment-service": 62,
      "notification-service": 38
    }
  }
}
```

**Caching:**
Compositions are cached for 30 seconds using Caffeine, reducing downstream load for frequently accessed orders.

---

### 12. Strangler Fig

> **Module:** `strangler-fig-service`

#### What It Is

The Strangler Fig pattern enables **incremental migration** from a legacy monolith to microservices. A facade layer intercepts all traffic and gradually routes requests from the old implementation to the new one — like a strangler fig tree growing around and eventually replacing its host.

#### Why It Matters

Big-bang rewrites fail. The Strangler Fig pattern:
- Allows **zero-downtime migration** — both old and new code serve traffic simultaneously
- Enables **gradual confidence building** — start with 5% of traffic, increase over weeks
- Provides **instant rollback** — flip routing back to legacy if issues arise
- Supports **shadow mode** — send traffic to both, compare responses, verify correctness before switching

#### Implementation Deep-Dive

**Legacy Code** (`LegacyOrderService.java`):
Deliberately written with anti-patterns to simulate a real legacy monolith — god class, mixed concerns, no separation of layers, `Thread.sleep` for slow operations.

**Modern Code** (`ModernOrderService.java`):
Clean implementation with proper DDD, validation, error handling, and separation of concerns.

**Routing Facade** (`StranglerFacade.java`):

```java
public enum RoutingStrategy {
    LEGACY_ONLY,        // 100% legacy
    MODERN_ONLY,        // 100% modern (migration complete)
    CANARY,             // Percentage-based split (e.g., 80% legacy / 20% modern)
    SHADOW,             // Send to both, return legacy response, compare results
    GRADUAL_MIGRATION   // Auto-incrementing modern percentage over time
}
```

**Shadow Mode** is the safest way to validate the modern implementation:

```java
public Object routeRequest(String endpoint, Object request) {
    RoutingStrategy strategy = routingTable.getOrDefault(endpoint, LEGACY_ONLY);

    if (strategy == SHADOW) {
        CompletableFuture<Object> legacyFuture = executeAsync(() -> legacyService.handle(request));
        CompletableFuture<Object> modernFuture = executeAsync(() -> modernService.handle(request));

        Object legacyResponse = legacyFuture.join();
        Object modernResponse = modernFuture.join();

        compareResponses(endpoint, legacyResponse, modernResponse);  // Log diffs
        metricsCollector.recordShadowExecution(endpoint,
            legacyLatency, modernLatency, responsesMatch);

        return legacyResponse;  // Always return legacy in shadow mode
    }
}
```

**Gradual Migration** automatically increases modern traffic:

```java
@Scheduled(fixedRate = 300000)  // Every 5 minutes
public void incrementMigrationPercentage() {
    routingTable.forEach((endpoint, strategy) -> {
        if (strategy == GRADUAL_MIGRATION) {
            int current = migrationPercentages.getOrDefault(endpoint, 0);
            int next = Math.min(current + 5, 100);  // +5% every 5 minutes
            migrationPercentages.put(endpoint, next);
            log.info("Migration for {}: {}% → {}%", endpoint, current, next);
        }
    });
}
```

**Migration Metrics** (`MigrationMetrics.java`):
Tracks everything needed to make data-driven migration decisions:
- Total requests, split by legacy vs. modern
- Average latency comparison
- Shadow mode discrepancy count
- Error rate comparison

---

## Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.1 |
| Event Sourcing / CQRS | Axon Framework | 4.9.3 |
| Resilience | Resilience4j | 2.2.0 |
| Service Discovery | Netflix Eureka | (Spring Cloud) |
| API Gateway | Spring Cloud Gateway | (Spring Cloud) |
| Messaging | Apache Kafka | 7.6.0 (Confluent) |
| RDBMS | PostgreSQL | 16 |
| Document Store | MongoDB | 7.0 |
| Cache / Rate Limiting | Redis | 7 |
| API Documentation | SpringDoc OpenAPI | 2.3.0 |
| Metrics | Micrometer + Prometheus | (Spring Boot) |
| Build | Maven (multi-module) | 3.9+ |
| Containers | Docker + Docker Compose | v3.9 |

---

## Module Structure

```
DesignExecutions/
├── pom.xml                          # Parent POM (dependency management)
├── docker-compose.yml               # Full infrastructure + services
├── .env                             # Environment variables
├── common-lib/                      # Shared library (events, DTOs, exceptions, saga)
│   └── src/main/java/com/microservices/common/
│       ├── events/{order,payment,inventory}/  # Cross-service event contracts
│       ├── dto/                               # ApiResponse, PagedResponse, OrderItemData
│       ├── exception/                         # ServiceException, GlobalExceptionHandler
│       └── saga/                              # SagaStep, SagaOrchestrator abstractions
├── service-discovery/               # Eureka Server (:8761)
├── api-gateway/                     # Spring Cloud Gateway (:8080)
│   └── src/main/java/.../gateway/
│       ├── config/                  # Routes, rate limiting, CORS
│       ├── filter/                  # Auth, tracing, logging filters
│       └── fallback/               # Graceful degradation handlers
├── order-service/                   # Core domain service (:8081)
│   └── src/main/java/.../order/
│       ├── aggregate/               # Event-sourced OrderAggregate
│       ├── command/api/             # Command objects (write side)
│       ├── query/api/               # Query objects (read side)
│       ├── query/projection/        # Read model projections
│       ├── saga/                    # OrderSaga orchestrator
│       └── event/                   # Domain events
├── payment-service/                 # Resilience patterns (:8082)
│   └── src/main/java/.../payment/
│       ├── service/                 # Circuit Breaker + Bulkhead + Retry
│       ├── config/                  # Resilience4j configuration
│       └── domain/                  # Payment entities
├── inventory-service/               # MongoDB / DB-per-service (:8083)
│   └── src/main/java/.../inventory/
│       ├── domain/                  # MongoDB documents
│       ├── service/                 # Atomic stock operations, Kafka consumer
│       └── repository/             # MongoDB repositories with $inc operations
├── notification-service/            # Sidecar pattern (:8084)
│   └── src/main/java/.../notification/
│       ├── sidecar/                 # Logging, metrics, config, health sidecars
│       ├── consumer/                # Kafka event consumers
│       └── service/                 # Notification sending
├── api-composition-service/         # API Composition (:8085)
│   └── src/main/java/.../composition/
│       ├── client/                  # WebClient-based service clients
│       ├── service/                 # Parallel aggregation logic
│       └── dto/                     # Composite response DTOs
└── strangler-fig-service/           # Strangler Fig migration (:8086)
    └── src/main/java/.../strangler/
        ├── legacy/                  # Legacy monolith simulation
        ├── modern/                  # Clean modern implementation
        ├── facade/                  # Routing facade + migration metrics
        └── interceptor/             # Request interception for routing
```

---

## Getting Started

### Prerequisites

- **Java 21** (verify: `java -version`)
- **Maven 3.9+** (verify: `mvn -version`)
- **Docker & Docker Compose** (verify: `docker compose version`)
- **~8 GB RAM** available for all containers

### Option 1: Docker Compose (Recommended)

```bash
# Clone and enter the project
cd DesignExecutions

# Start all infrastructure (databases, Kafka, Redis)
docker compose up -d postgres-order postgres-payment postgres-notification \
  postgres-strangler mongodb redis zookeeper kafka

# Wait for infrastructure to be healthy (~30 seconds)
docker compose ps  # All should show "healthy"

# Build and start all services
mvn clean package -DskipTests
docker compose up -d --build
```

### Option 2: Local Development

```bash
# Start infrastructure only
docker compose up -d postgres-order postgres-payment postgres-notification \
  postgres-strangler mongodb redis zookeeper kafka kafka-ui

# Build the project
mvn clean install -DskipTests

# Start services in separate terminals (order matters for dependencies)
cd service-discovery && mvn spring-boot:run &
sleep 10  # Wait for Eureka
cd api-gateway && mvn spring-boot:run &
cd order-service && mvn spring-boot:run &
cd payment-service && mvn spring-boot:run &
cd inventory-service && mvn spring-boot:run &
cd notification-service && mvn spring-boot:run &
cd api-composition-service && mvn spring-boot:run &
cd strangler-fig-service && mvn spring-boot:run &
```

### Verify Everything is Running

```bash
# Eureka Dashboard — should show all 7 services registered
open http://localhost:8761

# Kafka UI — should show topics
open http://localhost:8090

# API Gateway health
curl http://localhost:8080/actuator/health

# Each service health
for port in 8081 8082 8083 8084 8085 8086; do
  echo "Port $port: $(curl -s http://localhost:$port/actuator/health | jq -r '.status')"
done
```

---

## API Reference

### Service Ports

| Service | Port | Swagger UI |
|---------|------|-----------|
| API Gateway | 8080 | — |
| Order Service | 8081 | http://localhost:8081/swagger-ui.html |
| Payment Service | 8082 | http://localhost:8082/swagger-ui.html |
| Inventory Service | 8083 | http://localhost:8083/swagger-ui.html |
| Notification Service | 8084 | — |
| API Composition | 8085 | http://localhost:8085/swagger-ui.html |
| Strangler Fig | 8086 | http://localhost:8086/swagger-ui.html |
| Eureka Dashboard | 8761 | — |
| Kafka UI | 8090 | — |

### Key Endpoints

**Through API Gateway (port 8080):**

```bash
# Orders (CQRS + Event Sourcing + Saga)
POST   /api/orders                        # Create order (starts saga)
GET    /api/orders/{orderId}              # Query read model
GET    /api/orders/{orderId}/history      # Event sourcing: full event stream
GET    /api/orders/customer/{customerId}  # Orders by customer (paged)
PUT    /api/orders/{orderId}/approve      # Approve order
PUT    /api/orders/{orderId}/cancel       # Cancel (triggers saga compensation)

# Payments (Circuit Breaker + Bulkhead + Retry)
POST   /api/payments/process              # Process payment
POST   /api/payments/{paymentId}/refund   # Refund
GET    /api/payments/{paymentId}          # Get payment
GET    /api/payments/resilience/status    # Circuit breaker & bulkhead status

# Inventory (Database per Service)
POST   /api/inventory/products            # Create product
POST   /api/inventory/reserve             # Reserve stock
POST   /api/inventory/release             # Release reservation
POST   /api/inventory/restock             # Restock product
GET    /api/inventory/products/{id}       # Product availability
GET    /api/inventory/products/{id}/movements  # Stock audit trail
GET    /api/inventory/products/low-stock  # Low stock alerts

# API Composition
GET    /api/compositions/orders/{orderId}/details      # Full order composite
GET    /api/compositions/customers/{customerId}/dashboard  # Customer dashboard
GET    /api/compositions/orders/{orderId}/fulfillment  # Fulfillment tracking

# Strangler Fig
GET    /api/legacy/orders/{id}            # Legacy implementation
GET    /api/v2/orders/{id}                # Modern implementation
```

---

## Testing the Patterns

### End-to-End Saga Flow

```bash
# Step 1: Create a product in inventory
curl -X POST http://localhost:8083/api/inventory/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "ThinkPad X1 Carbon",
    "description": "Business laptop",
    "category": "Electronics",
    "initialQuantity": 50,
    "reorderThreshold": 10,
    "unitPrice": 1299.99,
    "warehouse": "US-EAST-1"
  }'

# Step 2: Create an order (triggers saga: order → inventory → payment)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "items": [{
      "productId": "LAPTOP-001",
      "productName": "ThinkPad X1 Carbon",
      "quantity": 2,
      "unitPrice": 1299.99
    }]
  }'
# Save the returned orderId

# Step 3: Watch the saga execute by polling order status
curl http://localhost:8081/api/orders/{orderId}

# Step 4: View the complete event history (Event Sourcing)
curl http://localhost:8081/api/orders/{orderId}/history

# Step 5: View the composed order details (API Composition)
curl http://localhost:8085/api/compositions/orders/{orderId}/details

# Step 6: Check inventory was decremented
curl http://localhost:8083/api/inventory/products/LAPTOP-001

# Step 7: View stock movement audit trail
curl http://localhost:8083/api/inventory/products/LAPTOP-001/movements
```

### Testing Circuit Breaker

```bash
# Rapid-fire payment requests to trigger failures
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:8082/api/payments/process \
    -H "Content-Type: application/json" \
    -d "{\"orderId\":\"ORD-$i\",\"customerId\":\"C1\",\"amount\":99.99,\"paymentMethod\":\"CREDIT_CARD\"}" &
done
wait

# Check circuit breaker state — should be OPEN after enough failures
curl http://localhost:8082/api/payments/resilience/status

# Wait 10 seconds for HALF_OPEN transition
sleep 10
curl http://localhost:8082/api/payments/resilience/status
# Should show HALF_OPEN, then CLOSED if next calls succeed
```

### Testing Strangler Fig Migration

```bash
# Call legacy endpoint
curl http://localhost:8086/api/legacy/orders/ORD-001
# Notice: slower response time, simpler response structure

# Call modern endpoint
curl http://localhost:8086/api/v2/orders/ORD-001
# Notice: faster, richer response

# Check migration metrics
curl http://localhost:8086/actuator/metrics/strangler.requests.legacy
curl http://localhost:8086/actuator/metrics/strangler.requests.modern
```

---

## Observability

### Metrics (Prometheus-Compatible)

Every service exposes metrics at `/actuator/prometheus`:

```bash
# Gateway metrics
curl http://localhost:8080/actuator/prometheus | grep gateway

# Circuit breaker metrics
curl http://localhost:8082/actuator/prometheus | grep resilience4j

# Custom sidecar metrics
curl http://localhost:8084/actuator/prometheus | grep sidecar
```

### Health Endpoints

```bash
# Deep health check (includes dependencies)
curl http://localhost:8082/actuator/health | jq

# Shows: db, diskSpace, kafka, circuitBreakers status
```

### Kafka Monitoring

```bash
# Kafka UI for topic inspection, consumer groups, message browsing
open http://localhost:8090

# Topics created automatically:
# - order-events
# - payment-events
# - inventory-events
```

### Distributed Tracing

Every request through the API Gateway receives an `X-Correlation-Id` header that propagates through all downstream services. Search for this ID across service logs to trace a complete request flow:

```bash
# Find a correlation ID from gateway logs
docker logs api-gateway 2>&1 | grep "X-Correlation-Id"

# Trace it through all services
docker logs order-service 2>&1 | grep "{correlation-id}"
docker logs payment-service 2>&1 | grep "{correlation-id}"
docker logs inventory-service 2>&1 | grep "{correlation-id}"
```

---

## Production Considerations

### What This Project Demonstrates vs. Production Requirements

| This Project | Production Addition |
|-------------|-------------------|
| Single Eureka instance | Eureka cluster (peer-to-peer replication) |
| In-memory JWT validation | OAuth2/OIDC with identity provider (Keycloak, Auth0) |
| Simulated payment gateway | Real gateway SDK (Stripe, Adyen) with PCI DSS |
| Axon JPA event store | Axon Server or EventStoreDB for high-throughput |
| `Thread.sleep` for delays | Actual network latency profiles |
| Caffeine local cache | Redis Cluster for distributed caching |
| Docker Compose | Kubernetes with Helm charts |
| Console logging | ELK/EFK Stack or Datadog |
| Simulated sidecar | Envoy/Istio service mesh sidecar |
| Manual Prometheus scraping | Grafana dashboards with alerting |

### Security Hardening Checklist

- [ ] Replace JWT HMAC-SHA256 with RSA-256 key pairs
- [ ] Implement OAuth2 Resource Server on each service
- [ ] Add mTLS between services (or use service mesh)
- [ ] Store secrets in HashiCorp Vault, not environment variables
- [ ] Enable SSL/TLS for all database connections
- [ ] Add audit logging for all state changes
- [ ] Implement API key rotation for external clients
- [ ] Configure network policies to restrict inter-service communication

### Scaling Considerations

- **Order Service**: Scale the query side independently from the command side (CQRS advantage)
- **Payment Service**: Bulkhead prevents one client's traffic from starving others
- **Inventory Service**: MongoDB sharding for horizontal scaling beyond single node
- **API Composition**: Stateless — scale horizontally behind the gateway load balancer
- **Kafka**: Increase partition count for higher throughput; add consumer instances per group

---

## Further Reading

### Design Pattern References

| Pattern | Key Resource |
|---------|-------------|
| API Gateway | [microservices.io/patterns/apigateway](https://microservices.io/patterns/apigateway.html) |
| Saga | [microservices.io/patterns/saga](https://microservices.io/patterns/data/saga.html) |
| Event Sourcing | [Martin Fowler: Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) |
| CQRS | [Martin Fowler: CQRS](https://martinfowler.com/bliki/CQRS.html) |
| Circuit Breaker | [Martin Fowler: Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html) |
| Bulkhead | [Resilience4j Bulkhead](https://resilience4j.readme.io/docs/bulkhead) |
| Strangler Fig | [Martin Fowler: Strangler Fig](https://martinfowler.com/bliki/StranglerFigApplication.html) |
| Database per Service | [microservices.io/patterns/database-per-service](https://microservices.io/patterns/data/database-per-service.html) |
| Sidecar | [Microsoft: Sidecar Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/sidecar) |
| API Composition | [microservices.io/patterns/api-composition](https://microservices.io/patterns/data/api-composition.html) |

### Books

- *Building Microservices* (2nd Edition) — Sam Newman
- *Microservices Patterns* — Chris Richardson
- *Designing Data-Intensive Applications* — Martin Kleppmann
- *Release It!* (2nd Edition) — Michael T. Nygard
- *Domain-Driven Design* — Eric Evans

---

## License

This project is intended for educational and reference purposes. Use it as a foundation for building production microservices, but review and adapt all configurations, security settings, and infrastructure for your specific requirements.
