package com.microservices.order.query.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.order.domain.OrderItem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * JPA converter that serialises {@link OrderItem} lists to/from JSON for
 * storage in the {@code order_view.items} column. Keeps the read model
 * denormalised so list queries require no joins.
 */
@Converter
public class OrderItemsConverter implements AttributeConverter<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }

    public static String toJson(List<OrderItem> items) {
        try {
            return MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise order items to JSON", e);
        }
    }

    public static List<OrderItem> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise order items from JSON", e);
        }
    }
}
