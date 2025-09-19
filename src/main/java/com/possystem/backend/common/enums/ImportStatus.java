package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ImportStatus {
    @JsonProperty("not confirmed")
    NOT_CONFIRMED,

    @JsonProperty("imported")
    IMPORTED,

    @JsonProperty("cancelled")
    CANCELLED
}
