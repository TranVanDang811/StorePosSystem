package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ImportStatus {
    DRAFT,
    RECEIVING,
    PARTIALLY_IMPORTED,
    IMPORTED,
    CANCELLED;

    @JsonCreator
    public static ImportStatus fromString(String value) {
        if (value == null) return null;
        for (ImportStatus status : ImportStatus.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
