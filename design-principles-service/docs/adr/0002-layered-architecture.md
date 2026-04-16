# ADR-0002: Layered Architecture with Strict Dependency Rules

## Status
**Accepted** — 2024-04-15

## Context
We need an internal architecture for the design-principles-service that demonstrates Separation of Concerns (SOC) and is enforceable through automated tests.

Options considered:
1. **Hexagonal Architecture (Ports & Adapters)** — powerful but over-engineered for a single bounded context with one adapter type (REST + JPA).
2. **Layered Architecture** — well-understood, easy to teach, sufficient for our domain complexity.
3. **Vertical Slices** — groups by feature instead of layer. Good for large teams, but obscures cross-cutting concerns we want to teach.

## Decision
Adopt a **strict layered architecture** with the following layers (top to bottom):

```
Controller (HTTP) → Service (Business Orchestration) → Repository (Persistence) → Domain (Entities, Value Objects, Events)
```

Cross-cutting concerns (audit logging, validation, caching) are handled via AOP aspects and dedicated service classes.

### Dependency Rules (enforced by ArchUnit tests)
- Controllers may depend on: Services, DTOs
- Services may depend on: Repositories, Domain, DTOs, Mappers
- Repositories may depend on: Domain
- Domain may depend on: nothing external (no Spring, no DTOs)

## Consequences
### Positive
- Clear, teachable boundaries between layers
- Domain logic is testable without Spring context
- Dependency violations are caught at build time via ArchUnit
- Each layer can evolve independently (e.g., swap REST for gRPC without touching domain)

### Negative
- Requires DTO-to-entity mapping (addressed by MapStruct — see ADR-0003)
- More files than a "put everything in one class" approach (but each file is focused and small)

## References
- [ArchUnit](https://www.archunit.org/)
- [Clean Architecture — Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
