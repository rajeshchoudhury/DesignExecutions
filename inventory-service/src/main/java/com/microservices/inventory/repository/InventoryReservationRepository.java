package com.microservices.inventory.repository;

import com.microservices.inventory.domain.InventoryReservation;
import com.microservices.inventory.domain.ReservationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends MongoRepository<InventoryReservation, String> {

    Optional<InventoryReservation> findByReservationId(String reservationId);

    List<InventoryReservation> findByOrderId(String orderId);

    List<InventoryReservation> findByStatus(ReservationStatus status);

    List<InventoryReservation> findByExpiresAtBeforeAndStatus(Instant cutoff, ReservationStatus status);
}
