package com.microservices.principles.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base entity providing identity and audit fields for all JPA entities.
 *
 * <h3>DRY Principle</h3>
 * <p>Rather than duplicating {@code id}, {@code createdAt}, {@code updatedAt}, and
 * {@code version} across every entity, this base class centralizes them. Any new entity
 * in the system inherits consistent identity generation and optimistic locking for free.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>UUID primary keys avoid sequential-ID enumeration attacks and simplify
 *       distributed ID generation (no coordination required).</li>
 *   <li>{@link Version} enables optimistic locking to detect concurrent modification
 *       without database-level locks.</li>
 *   <li>Audit timestamps use {@link Instant} (UTC) to avoid timezone ambiguity.</li>
 * </ul>
 *
 * @param <ID> the type of the entity identifier
 * @see <a href="../../docs/adr/0001-uuid-primary-keys.md">ADR-0001: UUID Primary Keys</a>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity<ID extends Serializable> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
