# ADR-0001: UUID Primary Keys

## Status
**Accepted** — 2024-04-15

## Context
We need a primary key strategy for all JPA entities. Options considered:
1. **Auto-increment BIGINT** — simple, but leaks ordering information, creates ID collision risk across distributed services, and requires database coordination.
2. **UUID v4** — universally unique without coordination, hides creation order from API consumers, and works across service boundaries.
3. **ULID / Snowflake** — sortable IDs, but add library dependencies for marginal benefit in this domain.

## Decision
Use **UUID v4** via JPA's `@GeneratedValue(strategy = GenerationType.UUID)` for all entity primary keys.

## Consequences
### Positive
- No cross-service ID coordination required
- IDs can be generated client-side or server-side
- Prevents enumeration attacks (IDs are not guessable)
- Works naturally with event-sourced systems and distributed architectures

### Negative
- Larger storage (16 bytes vs. 8 bytes for BIGINT)
- Not naturally sortable (use `createdAt` for ordering)
- Slightly worse index locality than sequential IDs (mitigated by PostgreSQL's UUID index optimizations)

### Trade-offs Accepted
The storage and index trade-offs are acceptable for a service with moderate data volume. If performance profiling reveals UUID indexes as a bottleneck, we can evaluate ULID as a migration path (YAGNI until proven).

## References
- [PostgreSQL UUID Performance](https://www.postgresql.org/docs/current/datatype-uuid.html)
- [Designing Distributed Systems — UUID vs Sequential](https://martinfowler.com/articles/patterns-of-distributed-systems/generation.html)
