package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BetStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("won") WON,
    @JsonProperty("lost") LOST
}
