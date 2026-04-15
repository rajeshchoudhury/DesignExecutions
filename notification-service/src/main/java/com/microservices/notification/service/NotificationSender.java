package com.microservices.notification.service;

import com.microservices.notification.domain.Notification;
import com.microservices.notification.domain.NotificationChannel;

public interface NotificationSender {

    void send(Notification notification) throws Exception;

    NotificationChannel getChannel();
}
