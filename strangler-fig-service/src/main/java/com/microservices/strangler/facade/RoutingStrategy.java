package com.microservices.strangler.facade;

/**
 * Routing strategies for the Strangler Fig pattern migration.
 *
 * Each strategy represents a different phase of the migration from
 * legacy to modern implementation:
 *
 * LEGACY_ONLY    -> All traffic goes to legacy (pre-migration)
 * CANARY         -> Percentage-based split between legacy and modern
 * SHADOW         -> Traffic goes to both; legacy response returned, responses compared
 * GRADUAL_MIGRATION -> Like CANARY but percentage auto-increases over time
 * MODERN_ONLY    -> All traffic goes to modern (migration complete)
 */
public enum RoutingStrategy {
    LEGACY_ONLY,
    MODERN_ONLY,
    CANARY,
    SHADOW,
    GRADUAL_MIGRATION
}
