package com.possystem.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ProductType {
    @JsonProperty("ingredient")
    INGREDIENT,

    @JsonProperty("drink")
    DRINK,

    @JsonProperty("cake")
    CAKE,

    @JsonProperty("other")
    OTHER;
}
