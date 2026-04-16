package com.microservices.principles.service.validation;

import com.microservices.principles.domain.entity.Product;
import com.microservices.principles.domain.entity.ProductStatus;
import com.microservices.principles.domain.exception.InvalidProductStateException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain validation service for complex product business rules.
 *
 * <h3>SOC Principle</h3>
 * <p>Simple field-level validation (not-null, size, pattern) is handled by Bean Validation
 * annotations on DTOs. This service handles <em>cross-field</em> and <em>stateful</em>
 * validation rules that require access to the domain model — for example, verifying
 * a product is ready for activation.</p>
 *
 * <h3>KISS Principle</h3>
 * <p>Validations are expressed as a flat list of checks. Each check is a simple boolean
 * condition with a descriptive error message. No validation framework or rule engine.</p>
 *
 * <h3>TDD Principle</h3>
 * <p>These validation rules are pure functions on domain objects — they require no Spring
 * context, no database, no external services. They can be exhaustively unit-tested in
 * milliseconds.</p>
 */
@Service
public class ProductValidationService {

    /**
     * Validates that a product meets all preconditions for activation.
     *
     * @param product the product to validate
     * @throws InvalidProductStateException if any validation fails, with all violations listed
     */
    public void validateForActivation(Product product) {
        List<String> violations = new ArrayList<>();

        if (product.getStatus() != ProductStatus.DRAFT) {
            violations.add("Product must be in DRAFT status to activate (current: %s)"
                    .formatted(product.getStatus()));
        }
        if (product.getName() == null || product.getName().isBlank()) {
            violations.add("Product name is required for activation");
        }
        if (product.getPrice() == null || !product.getPrice().isPositive()) {
            violations.add("Product must have a positive price for activation");
        }
        if (product.getStockQuantity() <= 0) {
            violations.add("Product must have stock > 0 for activation");
        }
        if (product.getCategory() == null || product.getCategory().isBlank()) {
            violations.add("Product must have a category for activation");
        }

        if (!violations.isEmpty()) {
            throw new InvalidProductStateException(
                    "Product %s failed activation validation: %s"
                            .formatted(product.getSku(), String.join("; ", violations)));
        }
    }
}
