package com.microservices.notification.repository;

import com.microservices.notification.domain.Notification;
import com.microservices.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByNotificationId(String notificationId);

    List<Notification> findByOrderId(String orderId);

    List<Notification> findByCustomerId(String customerId);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByStatusAndAttemptsLessThan(NotificationStatus status, int maxAttempts);
}
