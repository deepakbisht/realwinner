package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BetType {
    @JsonProperty("win") WIN,
    @JsonProperty("draw") DRAW
}
