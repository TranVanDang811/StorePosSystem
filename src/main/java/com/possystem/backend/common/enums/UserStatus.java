package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum UserStatus {
    @JsonProperty("active")
    ACTIVE,

    @JsonProperty("inactive")
    INACTIVE;

    @JsonCreator
    public static UserStatus fromValue(String value) {
        if (value == null || value.isBlank()) return ACTIVE;
        for (UserStatus s : values()) {
            if (s.name().equalsIgnoreCase(value) || s.toString().equalsIgnoreCase(value)) {
                return s;
            }
        }
        return ACTIVE;
    }
}
