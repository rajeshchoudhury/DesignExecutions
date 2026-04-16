# ADR-0003: MapStruct for DTO-Entity Mapping

## Status
**Accepted** — 2024-04-15

## Context
The layered architecture (ADR-0002) requires DTO-to-entity mapping. Options considered:
1. **Manual mapping methods** — full control, but repetitive and error-prone when fields change.
2. **ModelMapper / Dozer** — reflection-based, runtime errors, slower.
3. **MapStruct** — compile-time code generation, type-safe, fast.

## Decision
Use **MapStruct** with `componentModel = "spring"` for all DTO-entity conversions.

## Consequences
### Positive
- Compile-time type safety: mismatched field types are caught during build
- Zero runtime reflection overhead
- Generated code is readable and debuggable
- Integrates with Lombok seamlessly via annotation processor ordering

### Negative
- Additional annotation processor in the build pipeline
- Learning curve for custom mappings and qualifiers (mitigated by thorough Javadoc)

## References
- [MapStruct Reference Guide](https://mapstruct.org/documentation/stable/reference/html/)
