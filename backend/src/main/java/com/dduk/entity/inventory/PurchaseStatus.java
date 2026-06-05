package com.dduk.entity.inventory;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum PurchaseStatus {
    DRAFT,
    REQUESTED,
    APPROVED,
    REJECTED,
    SENT_TO_VENDOR,
    ORDERED,
    INBOUND_DELAY,
    RECEIVING,
    RECEIVED,
    COMPLETED,
    CANCELLED,
    UNKNOWN; // Fallback 용도

    @Converter(autoApply = true)
    public static class PurchaseStatusConverter implements AttributeConverter<PurchaseStatus, String> {
        private static final Logger log = LoggerFactory.getLogger(PurchaseStatusConverter.class);

        @Override
        public String convertToDatabaseColumn(PurchaseStatus attribute) {
            return attribute != null ? attribute.name() : null;
        }

        @Override
        public PurchaseStatus convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return null;
            }
            try {
                return PurchaseStatus.valueOf(dbData.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[ENUM_FALLBACK] Unknown PurchaseStatus value in DB: {}. Fallback to UNKNOWN.", dbData);
                return PurchaseStatus.UNKNOWN;
            }
        }
    }
}





