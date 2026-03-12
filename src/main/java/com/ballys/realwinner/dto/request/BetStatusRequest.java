package com.ballys.realwinner.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;


public enum BetStatusRequest {
    LIVE("live"),
    SETTLED("settled"),
    ALL("all");

    private final String value;

    BetStatusRequest(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }


    @Override
    public String toString() {
        return value;
    }

    @JsonCreator
    public static BetStatusRequest fromValue(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "live" -> LIVE;
            case "settled" -> SETTLED;
            case "all" -> ALL;
            default -> throw new IllegalArgumentException("Invalid BetStatusRequest: " + value + " should be one of:"
                    + Arrays.toString(BetStatusRequest.values()));
        };
    }
}
