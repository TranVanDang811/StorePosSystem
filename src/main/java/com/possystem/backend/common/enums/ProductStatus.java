package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ProductStatus {
    @JsonProperty("active")
    ACTIVE,

    @JsonProperty("out_of_stock")
    OUT_OF_STOCK,

    @JsonProperty("discontinued")
    DISCONTINUED
}