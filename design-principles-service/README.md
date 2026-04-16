# Design Principles Service

A **production-grade Spring Boot application** that teaches six foundational software engineering principles through a realistic Product Management domain. This is not a hello-world tutorial — it's an industry-ready codebase designed to serve as a reference architecture for engineering teams.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Principle 1: SOC — Separation of Concerns](#principle-1-soc--separation-of-concerns)
4. [Principle 2: DYC — Document Your Code](#principle-2-dyc--document-your-code)
5. [Principle 3: DRY — Don't Repeat Yourself](#principle-3-dry--dont-repeat-yourself)
6. [Principle 4: KISS — Keep It Simple, Stupid](#principle-4-kiss--keep-it-simple-stupid)
7. [Principle 5: TDD — Test-Driven Development](#principle-5-tdd--test-driven-development)
8. [Principle 6: YAGNI — You Ain't Gonna Need It](#principle-6-yagni--you-aint-gonna-need-it)
9. [Project Structure](#project-structure)
10. [Running the Application](#running-the-application)
11. [API Reference](#api-reference)
12. [Architecture Decision Records](#architecture-decision-records)
13. [Anti-Patterns — What NOT to Do](#anti-patterns--what-not-to-do)

---

## Overview

| Attribute | Detail |
|-----------|--------|
| **Domain** | Product Management (catalog, pricing, inventory) |
| **Stack** | Java 21, Spring Boot 3.2.4, JPA/Hibernate, PostgreSQL |
| **Build** | Maven multi-module (child of `design-patterns-platform`) |
| **Testing** | JUnit 5, AssertJ, Mockito, ArchUnit, MockMvc |
| **Documentation** | Javadoc, OpenAPI 3.0 / Swagger UI, ADRs |

### Why Product Management?

A product catalog is a domain every engineer understands intuitively — it has entities (Product), value objects (Money), lifecycle states (Draft → Active → Discontinued), stock management, and pricing rules. This familiarity lets engineers focus on *how* the principles are applied rather than learning a new domain.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                       │
│   ProductController, GlobalExceptionHandler              │
│   Concerns: HTTP binding, status codes, Swagger docs     │
├─────────────────────────────────────────────────────────┤
│                     Service Layer                         │
│   ProductService → ProductServiceImpl                    │
│   Concerns: Use-case orchestration, transactions         │
│   Cross-cutting: AuditAspect, PricingStrategy            │
├─────────────────────────────────────────────────────────┤
│                   Repository Layer                        │
│   ProductRepository, ProductSpecifications               │
│   Concerns: Persistence, dynamic queries                 │
├─────────────────────────────────────────────────────────┤
│                     Domain Layer                          │
│   Product, Money, DateRange, ProductEvent                │
│   ProductStatus, domain exceptions                       │
│   Concerns: Business rules, invariants, domain events    │
└─────────────────────────────────────────────────────────┘
```

**Dependency Rule**: Each layer may only depend on the layer directly below it. The Domain layer depends on nothing (not even Spring). This rule is **enforced by ArchUnit tests** — violations fail the build.

---

## Principle 1: SOC — Separation of Concerns

> *"Every module or class should have responsibility over a single part of the functionality."*

### How It's Implemented

#### 1.1 Strict Layered Architecture

Each layer has a single, well-defined responsibility:

| Layer | Responsibility | Does NOT Do |
|-------|---------------|-------------|
| **Controller** | HTTP binding, validation trigger, status codes, OpenAPI docs | Business logic, database queries, DTO mapping |
| **Service** | Transaction management, use-case orchestration, event publishing | HTTP concerns, SQL queries, domain rule enforcement |
| **Repository** | Data access, query building | Business logic, HTTP concerns |
| **Domain** | Business rules, invariants, state transitions, domain events | Framework concerns, persistence logic |

**File to study**: `ProductController.java` — Notice it has zero `if` statements for business logic. It receives a request, delegates to the service, and returns the response with the correct HTTP status.

#### 1.2 DTOs Decouple API from Domain

The API contract (`CreateProductRequest`, `ProductResponse`) is completely separated from the persistence model (`Product` entity). This means:

- Adding a database column doesn't change the API
- Renaming an API field doesn't require a database migration
- Internal fields (`version`, `domainEvents`) never leak to clients

**File to study**: `ProductResponse.java` — Flattens the embedded `Money` value object into separate `price`/`currency` fields. The API consumer never knows about the `Money` class.

#### 1.3 Domain Logic in the Entity, Not the Service

Business rules live in the `Product` entity:

```java
// Domain enforces its own rules — the service doesn't need to check these
public void reserveStock(int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("...");
    if (this.stockQuantity < quantity) throw new InsufficientStockException("...");
    this.stockQuantity -= quantity;
    registerEvent(ProductEvent.stockReserved(this, quantity));
}
```

The service layer simply calls `product.reserveStock(quantity)` — it doesn't duplicate the validation logic.

#### 1.4 Exception Handling Centralized

`GlobalExceptionHandler` is the *only* place that maps domain exceptions to HTTP status codes. Controllers don't catch exceptions — they throw them freely, knowing the handler will translate them.

```java
// Controller: clean, no try/catch
public ResponseEntity<ProductResponse> activateProduct(@PathVariable UUID id) {
    return ResponseEntity.ok(productService.activateProduct(id));
}

// Handler: single translation point
@ExceptionHandler(InvalidProductStateException.class)
public ResponseEntity<ApiErrorResponse> handleInvalidState(...) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(...);
}
```

#### 1.5 Value Objects Encapsulate Domain Logic

The `Money` value object handles all monetary arithmetic — rounding, currency matching, scale management. No service or controller ever directly manipulates `BigDecimal` for monetary operations.

**Key Insight**: If you find yourself writing `price.multiply(quantity).setScale(2, RoundingMode.HALF_UP)` in a service class, the concern is in the wrong place. It belongs in `Money`.

---

## Principle 2: DYC — Document Your Code

> *"Code tells you how; comments tell you why."*

### How It's Implemented

#### 2.1 Javadoc on Every Public API

Every public class and method has Javadoc that explains:
- **What** the class/method does
- **Why** it exists (which design principle it demonstrates)
- **When** to use it (and when not to)
- **How** it relates to other components (`@see` tags)

**File to study**: `ProductService.java` — Every method documents its parameters, return value, and which exceptions it throws under which conditions.

#### 2.2 OpenAPI / Swagger Annotations (Living Documentation)

```java
@Operation(summary = "Create a new product",
    description = "Creates a product in DRAFT status. The product must be activated "
        + "via PUT /activate before it becomes visible to customers.")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Product created"),
    @ApiResponse(responseCode = "409", description = "Duplicate SKU")
})
```

This produces interactive API documentation at `/swagger-ui.html` that is always in sync with the code. Unlike a Confluence page, this documentation **cannot drift** because it **is** the code.

#### 2.3 Bean Validation as Executable Documentation

```java
@NotBlank(message = "Product name is required")
@Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
@Schema(description = "Product display name", example = "Wireless Bluetooth Headphones")
String name
```

Each field carries three layers of documentation:
1. **Constraint annotations** — enforced at runtime
2. **Message strings** — human-readable validation errors
3. **Schema annotations** — rendered in Swagger UI

#### 2.4 Architecture Decision Records (ADRs)

Located in `docs/adr/`, each ADR documents:
- **Context**: What problem were we solving?
- **Decision**: What did we choose?
- **Consequences**: What are the trade-offs?

| ADR | Decision |
|-----|----------|
| [0001](docs/adr/0001-uuid-primary-keys.md) | UUID primary keys over auto-increment |
| [0002](docs/adr/0002-layered-architecture.md) | Strict layered architecture over hexagonal |
| [0003](docs/adr/0003-mapstruct-over-manual-mapping.md) | MapStruct over manual or reflection-based mapping |
| [0004](docs/adr/0004-strategy-pattern-for-pricing.md) | Strategy pattern for pricing over if/else chains |

#### 2.5 Custom `@Audited` Annotation

```java
@Audited(operation = "CREATE_PRODUCT")
public ProductResponse createProduct(CreateProductRequest request) { ... }
```

This is self-documenting code — any developer reading this method immediately understands that invocations are audit-logged, without needing to read the aspect implementation.

---

## Principle 3: DRY — Don't Repeat Yourself

> *"Every piece of knowledge must have a single, unambiguous, authoritative representation."*

### How It's Implemented

#### 3.1 `BaseEntity` — Shared Audit Fields

```java
@MappedSuperclass
public abstract class BaseEntity<ID extends Serializable> {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;
}
```

**Without DRY**: Every entity repeats `id`, `createdAt`, `updatedAt`, `version` — 4 fields × N entities = N opportunities for inconsistency.

**With DRY**: One base class. Every entity inherits consistent identity, auditing, and optimistic locking.

#### 3.2 `ProductSpecifications` — Composable Query Predicates

Instead of writing N repository methods for every filter combination:

```java
// BAD (DRY violation — combinatorial explosion):
Page<Product> findByNameContaining(String name, Pageable p);
Page<Product> findByCategory(String category, Pageable p);
Page<Product> findByNameContainingAndCategory(String name, String cat, Pageable p);
Page<Product> findByNameContainingAndCategoryAndStatus(String name, String cat, ProductStatus s, Pageable p);
// ... 16+ more combinations
```

We compose atomic specifications:

```java
// GOOD (DRY — compose as needed):
Specification<Product> spec = ProductSpecifications.hasCategory("Electronics")
    .and(ProductSpecifications.hasPriceBetween(min, max))
    .and(ProductSpecifications.hasStatus(ProductStatus.ACTIVE));
productRepository.findAll(spec, pageable);
```

**6 atomic predicates** replace **dozens of finder methods**.

#### 3.3 `ProductMapper` — MapStruct Eliminates Mapping Boilerplate

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source = "price.amount", target = "price")
    @Mapping(source = "price.currencyCode", target = "currency")
    ProductResponse toResponse(Product product);
}
```

MapStruct generates the mapping code at compile time — no manual `new ProductResponse(product.getName(), product.getPrice().getAmount(), ...)`.

#### 3.4 `PagedResponse<T>` — Generic Pagination Wrapper

```java
public record PagedResponse<T>(List<T> content, int pageNumber, int pageSize,
                                long totalElements, int totalPages, boolean last) {
    public static <E, D> PagedResponse<D> from(Page<E> page, Function<E, D> mapper) { ... }
}
```

**Without DRY**: `PagedProductResponse`, `PagedOrderResponse`, `PagedUserResponse` — identical structures for every entity.

**With DRY**: One generic `PagedResponse<T>` for all paginated endpoints.

#### 3.5 `AuditAspect` — AOP for Cross-Cutting Audit Logging

Without AOP, every service method would need:
```java
log.info("AUDIT | operation=CREATE_PRODUCT | started");
Instant start = Instant.now();
try {
    // actual logic
    log.info("AUDIT | operation=CREATE_PRODUCT | success | duration=42ms");
} catch (Exception e) {
    log.error("AUDIT | operation=CREATE_PRODUCT | failure | error=...");
    throw e;
}
```

With AOP, methods just declare `@Audited(operation = "CREATE_PRODUCT")` and the aspect handles all logging in one place.

#### 3.6 `findProductOrThrow` — Reusable Lookup Pattern

```java
private Product findProductOrThrow(UUID id) {
    return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
}
```

Used by **every service method** that needs an existing product. One line per caller instead of three.

---

## Principle 4: KISS — Keep It Simple, Stupid

> *"Simplicity is the ultimate sophistication."*

### How It's Implemented

#### 4.1 Strategy Pattern Instead of If/Else Chains

```java
// BAD (complex, grows with every new rule):
if (category.equals("Electronics")) { price = price.multiply(1.15); }
else if (category.equals("Books")) { price = price.multiply(1.05); }
else if (isPromotionalPeriod()) { price = price.multiply(0.90); }
// ... 20 more rules

// GOOD (simple, extensible):
public interface PricingStrategy {
    Money calculatePrice(Money basePrice, String category);
}
```

Each strategy is a single class with a single `calculatePrice` method. New rules = new classes, not longer if/else chains.

#### 4.2 Explicit State Machine Without a Framework

```java
// Product lifecycle is 3 states with clear transitions:
//   DRAFT → ACTIVE → DISCONTINUED
//         └────────→ DISCONTINUED

public void activate() {
    assertStatus(ProductStatus.DRAFT, "activate");
    this.status = ProductStatus.ACTIVE;
    registerEvent(ProductEvent.activated(this));
}
```

**Why not a state-machine framework?** Because the transitions fit in 20 lines of code. A framework would add configuration files, dependency injection for transitions, and a learning curve — all for 3 states. That's the opposite of KISS.

#### 4.3 Sealed Interface for Domain Events

```java
public sealed interface ProductEvent {
    UUID productId();
    Instant occurredAt();

    record Created(UUID productId, String sku, String name, Instant occurredAt) implements ProductEvent {}
    record Activated(UUID productId, String sku, Instant occurredAt) implements ProductEvent {}
    // ...
}
```

Java 21 sealed interfaces + records = immutable, pattern-matchable event types with zero boilerplate. No event base class, no serialization framework, no event bus library.

#### 4.4 Value Objects Are Self-Validating

```java
public static Money of(BigDecimal amount, String currencyCode) {
    Currency.getInstance(currencyCode); // validates ISO 4217 — throws if invalid
    return new Money(amount, currencyCode);
}
```

You cannot create an invalid `Money` instance. The constructor enforces scale, the factory enforces currency validity. No external validation service needed.

#### 4.5 Flat, Predictable Service Methods

Every service method follows the same pattern:
1. **Validate** (input-level: Bean Validation; business-level: domain methods)
2. **Load** (from repository)
3. **Mutate** (via domain methods)
4. **Save** (to repository)
5. **Map** (entity → DTO)
6. **Return**

No deep nesting, no complex branching, no callback chains.

---

## Principle 5: TDD — Test-Driven Development

> *"Tests are not about finding bugs. They're about designing software."*

### How It's Implemented

#### 5.1 Test Pyramid

```
           ╱╲
          ╱  ╲         ArchUnit Architecture Tests
         ╱────╲        (enforce layer dependencies)
        ╱      ╲
       ╱ WebMvc ╲      Controller Slice Tests
      ╱  Tests   ╲     (HTTP behavior, validation, error mapping)
     ╱────────────╲
    ╱              ╲
   ╱  Service Unit  ╲  Service Unit Tests
  ╱    Tests         ╲ (mocked dependencies, business orchestration)
 ╱────────────────────╲
╱                      ╲
╱   Domain Unit Tests   ╲  Domain Entity & Value Object Tests
╱  (no Spring, no DB)    ╲ (pure logic, sub-millisecond execution)
╱──────────────────────────╲
```

#### 5.2 Domain Tests — Zero Infrastructure

```java
@DisplayName("should reserve stock when sufficient quantity available")
void shouldReserveStock_whenSufficient() {
    Product product = ProductFixture.aProduct().withStockQuantity(50).build();
    product.clearDomainEvents();

    product.reserveStock(30);

    assertThat(product.getStockQuantity()).isEqualTo(20);
    assertThat(product.getDomainEvents()).hasSize(1)
            .first().isInstanceOf(ProductEvent.StockReserved.class);
}
```

**No Spring context. No database. No mocks.** Pure domain logic tested in isolation. This is what proper SOC enables — when your domain has no framework dependencies, your domain tests are blazingly fast.

**File to study**: `ProductTest.java` — 20+ tests covering creation, lifecycle transitions, stock management, price updates, and event collection.

#### 5.3 Service Tests — Mocked Dependencies, BDD Style

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private PricingStrategy pricingStrategy;

    @InjectMocks private ProductServiceImpl productService;

    @Test
    void shouldThrow_whenSkuAlreadyExists() {
        CreateProductRequest request = ProductFixture.aCreateRequest().build();
        given(productRepository.existsBySku(request.sku())).willReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }
}
```

Uses Mockito's BDD API (`given/willReturn`) for readability. Each test verifies a single behavior.

#### 5.4 Controller Tests — Web Layer Slice

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ProductService productService;

    @Test
    void shouldReturn400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\", \"sku\":\"SKU-001\", ...}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
```

`@WebMvcTest` loads only the web layer — no database, no service implementations. Tests focus on HTTP behavior: status codes, validation, JSON structure, error mapping.

#### 5.5 ArchUnit Tests — Architecture as Code

```java
@Test
void shouldEnforceLayeredArchitecture() {
    layeredArchitecture()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Repository").definedBy("..repository..")
        .layer("Domain").definedBy("..domain..")
        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
        .check(importedClasses);
}

@Test
void domainShouldNotDependOnSpring() {
    noClasses().that().resideInAPackage("..domain.entity..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..")
        .check(importedClasses);
}
```

Architecture rules are **executable**. A developer who adds `@Autowired` to a domain entity gets a failing test, not a code review comment three days later.

#### 5.6 Test Fixtures — Reusable, Fluent Builders

```java
Product product = ProductFixture.aProduct()
        .withSku("CUSTOM-SKU")
        .withPrice(49.99)
        .withStockQuantity(10)
        .build();
```

- **DRY**: One fixture class, reused across all test classes
- **KISS**: Fluent API — customize only what the test cares about
- **Maintenance**: When `Product.create()` signature changes, fix one builder, not 50 tests

---

## Principle 6: YAGNI — You Ain't Gonna Need It

> *"Always implement things when you actually need them, never when you just foresee that you need them."*

### How It's Implemented

#### 6.1 Lean Repository Interface

```java
// What we HAVE (needed today):
Optional<Product> findBySku(String sku);
boolean existsBySku(String sku);
Page<Product> findByStatus(ProductStatus status, Pageable pageable);

// What we DON'T have (not needed today):
// ❌ findByDescriptionContaining(...)
// ❌ countByCategory(...)
// ❌ findByCreatedAtBetween(...)
// ❌ deleteByStatus(...)
```

#### 6.2 Lean API Surface

The API exposes only operations the current consumers need:

| Endpoint | Why It Exists |
|----------|---------------|
| `POST /products` | UI create form |
| `GET /products/{id}` | Product detail page |
| `GET /products` | Product listing with filters |
| `PUT /products/{id}` | Product edit form |
| `PUT /products/{id}/activate` | Workflow transition |
| `PUT /products/{id}/discontinue` | Workflow transition |
| `POST /products/{id}/reserve-stock` | Order processing |
| `POST /products/{id}/release-stock` | Order cancellation |

**What we explicitly decided NOT to build:**
- ❌ `DELETE /products/{id}` — soft-delete via discontinue is sufficient
- ❌ `POST /products/bulk-import` — no bulk import requirement
- ❌ `GET /products/{id}/history` — event sourcing is in the order-service module
- ❌ HATEOAS links — no consumer has requested hypermedia navigation
- ❌ GraphQL endpoint — REST satisfies all current needs

#### 6.3 Single Pricing Strategy

We defined the `PricingStrategy` interface for extensibility, but we only implemented `StandardPricingStrategy` (pass-through). No `PromotionalPricingStrategy`, no `TieredPricingStrategy` — those are speculative features.

**Key Insight**: The *interface* is the extension point. The *implementation* is YAGNI until proven needed.

#### 6.4 No Generic CRUD Framework

We intentionally avoided building a `GenericCrudService<T, ID>` base class because:
- We have one entity (Product) — a framework for one user is waste
- Each entity has unique business rules that a generic framework would either skip or awkwardly accommodate
- If a second entity is added and shares patterns, *then* we extract the common base (refactor, don't speculate)

#### 6.5 YAGNI Anti-Pattern Examples

Here's what over-engineering looks like — things we intentionally avoided:

```java
// ❌ YAGNI VIOLATION: Building for imaginary requirements
public interface CrudService<T, ID> {
    T create(T entity);
    T findById(ID id);
    Page<T> findAll(Pageable pageable);
    T update(ID id, T entity);
    void delete(ID id);
    void bulkDelete(List<ID> ids);
    T clone(ID id);
    List<T> importFromCsv(MultipartFile file);
    byte[] exportToCsv(Specification<T> spec);
    void schedule(ID id, ZonedDateTime publishAt);
    T createVersion(ID id);
    void rollback(ID id, int version);
}
// 12 methods. The app needs 8 product-specific operations.
```

```java
// ❌ YAGNI VIOLATION: Abstract factory for one implementation
public interface PricingStrategyFactory {
    PricingStrategy create(PricingContext context);
}
public class PricingStrategyFactoryImpl implements PricingStrategyFactory { ... }
public class PricingContext { ... }
public class PricingContextBuilder { ... }
// 4 classes to avoid one `new StandardPricingStrategy()`.
```

---

## Project Structure

```
design-principles-service/
├── pom.xml                                          # Module POM
├── README.md                                        # This file
├── docs/
│   └── adr/                                         # Architecture Decision Records
│       ├── 0001-uuid-primary-keys.md
│       ├── 0002-layered-architecture.md
│       ├── 0003-mapstruct-over-manual-mapping.md
│       └── 0004-strategy-pattern-for-pricing.md
└── src/
    ├── main/
    │   ├── java/com/microservices/principles/
    │   │   ├── DesignPrinciplesApplication.java      # Entry point
    │   │   ├── annotation/
    │   │   │   └── Audited.java                      # Custom audit annotation [DYC + DRY]
    │   │   ├── aspect/
    │   │   │   └── AuditAspect.java                  # AOP audit logging [DRY]
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java                # Swagger/OpenAPI setup [DYC]
    │   │   ├── controller/
    │   │   │   ├── ProductController.java            # REST endpoints [SOC + DYC]
    │   │   │   └── GlobalExceptionHandler.java       # Centralized error handling [SOC + DRY]
    │   │   ├── domain/
    │   │   │   ├── entity/
    │   │   │   │   ├── BaseEntity.java               # Shared audit fields [DRY]
    │   │   │   │   ├── Product.java                  # Aggregate root [SOC + KISS]
    │   │   │   │   └── ProductStatus.java            # Lifecycle enum [KISS]
    │   │   │   ├── event/
    │   │   │   │   └── ProductEvent.java             # Sealed event hierarchy [SOC + KISS]
    │   │   │   ├── exception/
    │   │   │   │   ├── DuplicateSkuException.java
    │   │   │   │   ├── InsufficientStockException.java
    │   │   │   │   ├── InvalidProductStateException.java
    │   │   │   │   └── ProductNotFoundException.java
    │   │   │   └── valueobject/
    │   │   │       ├── Money.java                    # Monetary value object [SOC + KISS]
    │   │   │       └── DateRange.java                # Date range value object [KISS]
    │   │   ├── dto/
    │   │   │   ├── mapper/
    │   │   │   │   └── ProductMapper.java            # MapStruct mapper [DRY + SOC]
    │   │   │   ├── request/
    │   │   │   │   ├── CreateProductRequest.java     # Inbound DTO [SOC + DYC]
    │   │   │   │   ├── UpdateProductRequest.java     # Inbound DTO [YAGNI]
    │   │   │   │   └── StockAdjustmentRequest.java   # Inbound DTO [KISS]
    │   │   │   └── response/
    │   │   │       ├── ProductResponse.java          # Outbound DTO [SOC + YAGNI]
    │   │   │       ├── PagedResponse.java            # Generic paging [DRY]
    │   │   │       └── ApiErrorResponse.java         # Error envelope [DRY + DYC]
    │   │   ├── repository/
    │   │   │   ├── ProductRepository.java            # JPA repository [SOC + YAGNI]
    │   │   │   └── ProductSpecifications.java        # Dynamic query predicates [DRY + KISS]
    │   │   └── service/
    │   │       ├── ProductService.java               # Service interface [SOC + YAGNI]
    │   │       ├── impl/
    │   │       │   └── ProductServiceImpl.java       # Service implementation [SOC + DRY + KISS]
    │   │       ├── strategy/
    │   │       │   ├── PricingStrategy.java          # Strategy interface [KISS]
    │   │       │   └── StandardPricingStrategy.java  # Default strategy [YAGNI]
    │   │       └── validation/
    │   │           └── ProductValidationService.java # Cross-field validation [SOC + TDD]
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/com/microservices/principles/
        │   ├── ArchitectureTest.java                 # ArchUnit rules [TDD + SOC]
        │   ├── controller/
        │   │   └── ProductControllerTest.java        # WebMvc slice tests [TDD]
        │   ├── domain/
        │   │   ├── MoneyTest.java                    # Value object tests [TDD]
        │   │   └── ProductTest.java                  # Entity tests [TDD]
        │   ├── fixture/
        │   │   └── ProductFixture.java               # Test data builders [TDD + DRY]
        │   └── service/
        │       └── ProductServiceTest.java           # Service unit tests [TDD]
        └── resources/
            └── application-test.yml
```

---

## Running the Application

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 15+ (or use the Docker Compose from the parent project)

### Build & Test
```bash
# From the repository root
mvn clean test -pl design-principles-service

# Run only domain tests (fastest feedback loop)
mvn test -pl design-principles-service -Dtest="com.microservices.principles.domain.*"

# Run with coverage report
mvn clean verify -pl design-principles-service
# Report at: design-principles-service/target/site/jacoco/index.html
```

### Run Locally
```bash
# Start dependencies (PostgreSQL) via Docker Compose
docker compose up -d postgres

# Run the service
mvn spring-boot:run -pl design-principles-service

# Or with test profile (H2 in-memory, no external dependencies)
mvn spring-boot:run -pl design-principles-service -Dspring.profiles.active=test
```

---

## API Reference

Once running, interactive API documentation is available at:
- **Swagger UI**: [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8085/api-docs](http://localhost:8085/api-docs)

### Quick Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/products` | Create product (DRAFT) |
| `GET` | `/api/v1/products/{id}` | Get product by ID |
| `GET` | `/api/v1/products/sku/{sku}` | Get product by SKU |
| `GET` | `/api/v1/products?name=&category=&status=` | Search with filters |
| `PUT` | `/api/v1/products/{id}` | Update product details |
| `PUT` | `/api/v1/products/{id}/activate` | Activate (DRAFT → ACTIVE) |
| `PUT` | `/api/v1/products/{id}/discontinue` | Discontinue product |
| `POST` | `/api/v1/products/{id}/reserve-stock` | Reserve stock |
| `POST` | `/api/v1/products/{id}/release-stock` | Release stock |

---

## Architecture Decision Records

| ADR | Title | Status |
|-----|-------|--------|
| [0001](docs/adr/0001-uuid-primary-keys.md) | UUID Primary Keys | Accepted |
| [0002](docs/adr/0002-layered-architecture.md) | Layered Architecture with Strict Dependencies | Accepted |
| [0003](docs/adr/0003-mapstruct-over-manual-mapping.md) | MapStruct for DTO-Entity Mapping | Accepted |
| [0004](docs/adr/0004-strategy-pattern-for-pricing.md) | Strategy Pattern for Pricing | Accepted |

---

## Anti-Patterns — What NOT to Do

This section contrasts each principle with its violation, helping engineers recognize anti-patterns in their own codebases.

### SOC Violation: Business Logic in Controller
```java
// ❌ BAD — Controller does validation, business logic, AND persistence
@PostMapping
public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    if (name == null || name.isBlank()) {
        return ResponseEntity.badRequest().body("Name is required");
    }
    BigDecimal price = new BigDecimal(body.get("price").toString());
    if (price.compareTo(BigDecimal.ZERO) <= 0) {
        return ResponseEntity.badRequest().body("Price must be positive");
    }
    Product product = new Product();
    product.setName(name);
    product.setPrice(price);
    entityManager.persist(product);
    return ResponseEntity.ok(product); // leaks JPA entity to API!
}
```

### DRY Violation: Copy-Pasted Validation
```java
// ❌ BAD — Same null-check duplicated in 5 service methods
public void createProduct(Request r) {
    Product p = repo.findById(r.id()).orElseThrow(() -> new NotFoundException(r.id()));
    // ...
}
public void updateProduct(Request r) {
    Product p = repo.findById(r.id()).orElseThrow(() -> new NotFoundException(r.id()));
    // ...
}
```

### KISS Violation: Over-Engineered Configuration
```java
// ❌ BAD — Factory of factories for 3 states
public interface StateTransitionHandlerFactory {
    StateTransitionHandler create(TransitionContext ctx);
}
public class StateTransitionHandlerFactoryImpl implements StateTransitionHandlerFactory {
    private final Map<ProductStatus, Map<ProductStatus, TransitionValidator>> validators;
    private final TransitionAuditLogger auditLogger;
    private final TransitionEventPublisherFactory eventPublisherFactory;
    // 200 lines for what 3 if-statements would do
}
```

### YAGNI Violation: Speculative Abstraction
```java
// ❌ BAD — Generic framework for one entity
public abstract class AbstractCrudService<E extends BaseEntity, D, R> {
    protected abstract JpaRepository<E, UUID> getRepository();
    protected abstract Function<E, D> getMapper();
    protected abstract Function<R, E> getCreator();
    protected abstract Consumer<E> getPreCreateHook();
    protected abstract Consumer<E> getPostCreateHook();
    protected abstract BiConsumer<E, R> getUpdateMerger();
    // 15 abstract methods for "flexibility" nobody asked for
}
```

---

## Teaching Guide

### For Tech Leads: How to Use This Repo

1. **Code Reviews**: Point team members to specific files when reviewing PRs that violate a principle
2. **Onboarding**: New engineers read the README, then trace a single request from controller → service → repository → domain
3. **Architecture Katas**: Have the team extend the service (e.g., add a Category entity) and review whether the principles are maintained
4. **Brown Bags**: Each principle section above is structured as a standalone presentation topic

### Suggested Learning Path

| Session | Focus | Key Files |
|---------|-------|-----------|
| 1 | SOC — Trace a request end-to-end | `ProductController`, `ProductServiceImpl`, `Product` |
| 2 | DRY — Find the repeated patterns | `BaseEntity`, `ProductSpecifications`, `AuditAspect` |
| 3 | KISS — Simplicity in design | `Money`, `ProductStatus`, `PricingStrategy` |
| 4 | TDD — Run tests, read test names as specs | `ProductTest`, `ProductServiceTest`, `ArchitectureTest` |
| 5 | YAGNI — Review what we chose NOT to build | Repository interface, API surface, ADR-0004 |
| 6 | DYC — Navigate using docs alone | Swagger UI, Javadoc, ADRs |
