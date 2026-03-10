package com.ballys.realwinner.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BetStatusRequest {
    @JsonProperty("pending") PENDING,
    @JsonProperty("settled") SETTLED
}
