package com.microservices.strangler.modern.repository;

import com.microservices.strangler.modern.domain.ModernOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModernOrderRepository extends JpaRepository<ModernOrder, Long> {

    Optional<ModernOrder> findByOrderId(String orderId);

    List<ModernOrder> findByCustomerId(String customerId);

    List<ModernOrder> findByStatus(String status);

    @Query("SELECT o FROM ModernOrder o WHERE o.customerId = ?1 ORDER BY o.createdAt DESC")
    List<ModernOrder> findRecentByCustomerId(String customerId);

    boolean existsByOrderId(String orderId);
}
