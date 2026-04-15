package com.microservices.notification.service;

import com.microservices.notification.domain.*;
import com.microservices.notification.repository.NotificationRepository;
import com.microservices.notification.sidecar.SidecarConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Map<NotificationChannel, NotificationSender> senderMap;
    private final SidecarConfigProvider configProvider;

    public NotificationService(NotificationRepository notificationRepository,
                               List<NotificationSender> senders,
                               SidecarConfigProvider configProvider) {
        this.notificationRepository = notificationRepository;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
        this.configProvider = configProvider;
    }

    @Transactional
    public Notification createAndSend(String orderId, String customerId,
                                      NotificationType type, NotificationChannel channel,
                                      String recipient, String subject, String body) {
        Notification notification = Notification.builder()
                .orderId(orderId)
                .customerId(customerId)
                .type(type)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        log.info("Created notification {} for order {} type={} channel={}",
                notification.getNotificationId(), orderId, type, channel);

        sendNotification(notification);
        return notification;
    }

    @Transactional
    public void sendNotification(Notification notification) {
        if (!configProvider.isChannelEnabled(notification.getChannel())) {
            log.warn("Channel {} is disabled by sidecar config, skipping notification {}",
                    notification.getChannel(), notification.getNotificationId());
            return;
        }

        NotificationSender sender = senderMap.get(notification.getChannel());
        if (sender == null) {
            log.warn("No sender configured for channel {}, falling back to EMAIL",
                    notification.getChannel());
            sender = senderMap.get(NotificationChannel.EMAIL);
        }

        if (sender == null) {
            log.error("No notification sender available for notification {}",
                    notification.getNotificationId());
            markFailed(notification, "No sender available for channel " + notification.getChannel());
            return;
        }

        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastAttemptAt(Instant.now());

        try {
            sender.send(notification);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Successfully sent notification {} via {}",
                    notification.getNotificationId(), notification.getChannel());
        } catch (Exception e) {
            log.error("Failed to send notification {} via {} (attempt {}/{}): {}",
                    notification.getNotificationId(), notification.getChannel(),
                    notification.getAttempts(), notification.getMaxAttempts(), e.getMessage());

            if (notification.getAttempts() >= notification.getMaxAttempts()) {
                markFailed(notification, e.getMessage());
            } else {
                notification.setStatus(NotificationStatus.RETRYING);
                notification.setErrorMessage(e.getMessage());
            }
        }

        notificationRepository.save(notification);
    }

    private void markFailed(Notification notification, String errorMessage) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setErrorMessage(errorMessage);
        log.error("Notification {} permanently failed after {} attempts",
                notification.getNotificationId(), notification.getAttempts());
    }

    @Scheduled(fixedDelayString = "${notification.retry.interval-ms:30000}")
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> retryable = notificationRepository
                .findByStatusAndAttemptsLessThan(NotificationStatus.RETRYING, 3);

        if (!retryable.isEmpty()) {
            log.info("Retrying {} failed notifications", retryable.size());
            retryable.forEach(this::sendNotification);
        }
    }

    public List<Notification> getByOrderId(String orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    public List<Notification> getByCustomerId(String customerId) {
        return notificationRepository.findByCustomerId(customerId);
    }

    public List<Notification> getByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status);
    }
}
