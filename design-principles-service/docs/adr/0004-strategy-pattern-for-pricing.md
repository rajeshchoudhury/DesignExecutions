# ADR-0004: Strategy Pattern for Pricing

## Status
**Accepted** — 2024-04-15

## Context
Product pricing may vary by category, promotional period, or business rule. We need an extensible pricing mechanism without modifying the core service for each new rule.

Options considered:
1. **If/else chain in service** — simple initially, but becomes a maintenance nightmare as rules accumulate.
2. **Rule engine (Drools, Easy Rules)** — powerful but heavy dependency for our current needs (YAGNI).
3. **Strategy pattern** — simple interface, easy to extend, no external dependencies.

## Decision
Define a `PricingStrategy` interface and inject it into `ProductServiceImpl`. Start with a single `StandardPricingStrategy` that returns the base price unchanged.

## Consequences
### Positive
- New pricing strategies are added by implementing the interface — no existing code changes
- Strategies are independently testable
- Service logic remains clean (delegates to strategy)
- Demonstrates KISS (simple) + OCP (open for extension, closed for modification)

### Negative
- One extra layer of indirection for a currently-simple operation
- Justified as a teaching example; in production, evaluate whether the abstraction is premature (YAGNI)

### YAGNI Decision Log
- **NOT implementing now**: PromotionalPricingStrategy, TieredPricingStrategy, BulkDiscountStrategy
- **Reason**: No business requirement exists. The Strategy interface makes them trivial to add later.

## References
- [Strategy Pattern — Refactoring.Guru](https://refactoring.guru/design-patterns/strategy)
