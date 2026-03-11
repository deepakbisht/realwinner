package com.ballys.realwinner.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;


public enum BetStatusRequest {
    @JsonProperty("pending") PENDING,
    @JsonProperty("settled") SETTLED;

    @JsonCreator
    public static BetStatusRequest fromValue(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "pending" -> PENDING;
            case "settled" -> SETTLED;
            default -> throw new IllegalArgumentException("Invalid BetStatusRequest: " + value + " should be one of:"
                    + Arrays.toString(BetStatusRequest.values()));
        };
    }
}
