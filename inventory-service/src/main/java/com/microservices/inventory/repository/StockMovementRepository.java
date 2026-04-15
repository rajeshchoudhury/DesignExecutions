package com.microservices.inventory.repository;

import com.microservices.inventory.domain.StockMovement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends MongoRepository<StockMovement, String> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(String productId);

    List<StockMovement> findByReferenceId(String referenceId);

    List<StockMovement> findByMovementType(StockMovement.MovementType movementType);
}
