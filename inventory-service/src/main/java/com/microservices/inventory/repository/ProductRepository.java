package com.microservices.inventory.repository;

import com.microservices.inventory.domain.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    List<Product> findByCategory(String category);

    List<Product> findByAvailableQuantityLessThanEqual(int threshold);

    /**
     * Atomically reserves inventory using MongoDB's findAndModify semantics.
     * Only succeeds if availableQuantity >= requested quantity, preventing overselling.
     * Returns the match count so the caller knows if the update was applied.
     */
    @Query("{ '_id': ?0, 'availableQuantity': { $gte: ?1 } }")
    @Update("{ '$inc': { 'availableQuantity': ?#{-[1]}, 'reservedQuantity': ?1 }, '$set': { 'updatedAt': ?2 } }")
    long reserveStock(String productId, int quantity, java.time.Instant updatedAt);

    /**
     * Atomically releases reserved inventory back to available stock.
     */
    @Query("{ '_id': ?0, 'reservedQuantity': { $gte: ?1 } }")
    @Update("{ '$inc': { 'availableQuantity': ?1, 'reservedQuantity': ?#{-[1]} }, '$set': { 'updatedAt': ?2 } }")
    long releaseStock(String productId, int quantity, java.time.Instant updatedAt);

    /**
     * Atomically restocks inventory by increasing available quantity.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'availableQuantity': ?1 }, '$set': { 'updatedAt': ?2 } }")
    long restockProduct(String productId, int quantity, java.time.Instant updatedAt);

    @Query("{ 'availableQuantity': { $lte: '$reorderThreshold' } }")
    List<Product> findLowStockProducts();
}
