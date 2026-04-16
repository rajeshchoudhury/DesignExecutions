package com.microservices.principles.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Generic pagination wrapper for list endpoints.
 *
 * <h3>DRY Principle</h3>
 * <p>Every paginated endpoint in the system returns this same envelope structure.
 * Without this generic wrapper, each entity would need its own
 * {@code PagedProductResponse}, {@code PagedOrderResponse}, etc.</p>
 *
 * @param <T> the type of items in the page
 * @param content       the items on this page
 * @param pageNumber    zero-based page number
 * @param pageSize      requested page size
 * @param totalElements total matching items across all pages
 * @param totalPages    total number of pages
 * @param last          true if this is the last page
 */
@Schema(description = "Paginated response wrapper")
public record PagedResponse<T>(

        @Schema(description = "Items on this page")
        List<T> content,

        @Schema(description = "Current page number (0-based)", example = "0")
        int pageNumber,

        @Schema(description = "Page size", example = "20")
        int pageSize,

        @Schema(description = "Total elements across all pages", example = "142")
        long totalElements,

        @Schema(description = "Total pages", example = "8")
        int totalPages,

        @Schema(description = "True if this is the last page")
        boolean last
) {
    /**
     * Factory method to create from a Spring Data {@code Page}.
     *
     * @param page   the Spring Data page
     * @param mapper function to convert entity → DTO
     * @param <E>    entity type
     * @param <D>    DTO type
     * @return a PagedResponse containing mapped DTOs
     */
    public static <E, D> PagedResponse<D> from(
            org.springframework.data.domain.Page<E> page,
            java.util.function.Function<E, D> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
