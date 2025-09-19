package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OrderStatus {
    @JsonProperty("pending")
    PENDING,

    @JsonProperty("paid")
    PAID,

    @JsonProperty("canceled")
    CANCELED,
}
