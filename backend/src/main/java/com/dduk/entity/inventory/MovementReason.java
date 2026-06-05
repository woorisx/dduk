package com.dduk.entity.inventory;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum MovementReason {
    PURCHASE_RECEIVED,
    SALES_SHIPPED,
    MANUAL_ADJUST,
    TRANSFER,
    REBUILD_ADJUSTMENT,
    RETURNED_FROM_CUSTOMER,
    RETURNED_TO_VENDOR,
    PRODUCTION_CONSUMED,
    UNKNOWN; // Fallback 용도

    @Converter(autoApply = true)
    public static class MovementReasonConverter implements AttributeConverter<MovementReason, String> {
        private static final Logger log = LoggerFactory.getLogger(MovementReasonConverter.class);

        @Override
        public String convertToDatabaseColumn(MovementReason attribute) {
            return attribute != null ? attribute.name() : null;
        }

        @Override
        public MovementReason convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return null;
            }
            try {
                return MovementReason.valueOf(dbData.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[ENUM_FALLBACK] Unknown MovementReason value in DB: {}. Fallback to UNKNOWN.", dbData);
                return MovementReason.UNKNOWN;
            }
        }
    }
}

