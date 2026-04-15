package com.microservices.common.dto;

import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;

@Value
public class OrderItemData implements Serializable {

    private static final long serialVersionUID = 1L;

    String productId;
    String productName;
    int quantity;
    BigDecimal unitPrice;
}
