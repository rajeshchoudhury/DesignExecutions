package com.microservices.notification.service;

import com.microservices.notification.domain.Notification;
import com.microservices.notification.domain.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Override
    public void send(Notification notification) throws Exception {
        log.info("[EMAIL] Sending email to {} | subject='{}' | notificationId={}",
                notification.getRecipient(), notification.getSubject(), notification.getNotificationId());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(notification.getRecipient());
            message.setSubject(notification.getSubject());
            message.setText(notification.getBody());
            message.setFrom("noreply@microservices-platform.com");

            mailSender.send(message);
            log.info("[EMAIL] Successfully sent email to {} | notificationId={}",
                    notification.getRecipient(), notification.getNotificationId());
        } catch (Exception e) {
            log.warn("[EMAIL] Mail server unavailable, simulating send for notificationId={}: {}",
                    notification.getNotificationId(), e.getMessage());
            simulateSend(notification);
        }
    }

    private void simulateSend(Notification notification) {
        log.info("[EMAIL-SIMULATED] To: {} | Subject: {} | Body length: {} chars",
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody() != null ? notification.getBody().length() : 0);
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }
}
