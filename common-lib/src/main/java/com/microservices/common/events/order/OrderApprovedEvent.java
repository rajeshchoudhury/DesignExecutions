package com.microservices.common.events.order;

import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
public class OrderApprovedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    String orderId;
    Instant approvedAt;
}
