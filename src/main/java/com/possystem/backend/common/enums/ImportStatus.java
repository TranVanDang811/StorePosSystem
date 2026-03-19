package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ImportStatus {
    @JsonProperty("draft")
    DRAFT,

    @JsonProperty("receiving")
    RECEIVING,

    @JsonProperty("partially_imported")
    PARTIALLY_IMPORTED,

    @JsonProperty("imported")
    IMPORTED,

    @JsonProperty("cancelled")
    CANCELLED,

}
