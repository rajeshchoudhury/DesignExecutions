package com.microservices.principles.dto.mapper;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.valueobject.Money;
import com.microservices.principles.dto.request.CreateProductRequest;
import com.microservices.principles.dto.response.ProductResponse;
import org.mapstruct.*;

/**
 * MapStruct mapper for bidirectional Product ↔ DTO conversion.
 *
 * <h3>DRY Principle</h3>
 * <p>Manual entity-to-DTO mapping code is tedious, error-prone, and duplicated everywhere.
 * MapStruct generates type-safe, compile-time mapping code — eliminating boilerplate while
 * catching mapping errors at build time rather than runtime.</p>
 *
 * <h3>SOC Principle</h3>
 * <p>Mapping logic is isolated in this single file, not scattered across controllers
 * and services. When a field is added to the response DTO, there's exactly one place
 * to update the mapping.</p>
 *
 * @see <a href="https://mapstruct.org/">MapStruct Documentation</a>
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    /**
     * Converts a Product entity to the API response DTO.
     * Flattens the embedded Money value object.
     */
    @Mapping(source = "price.amount", target = "price")
    @Mapping(source = "price.currencyCode", target = "currency")
    ProductResponse toResponse(Product product);

    /**
     * Converts a creation request into a domain entity.
     * Note: the actual Product creation goes through {@link Product#create} factory method
     * in the service layer — this mapping is a helper for non-factory scenarios.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "domainEvents", ignore = true)
    @Mapping(target = "price", ignore = true)
    Product toEntity(CreateProductRequest request);

    /**
     * Maps request fields to an existing entity for update operations.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "domainEvents", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "stockQuantity", ignore = true)
    void updateEntityFromRequest(
            @MappingTarget Product product,
            com.microservices.principles.dto.request.UpdateProductRequest request);
}
