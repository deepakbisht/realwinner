package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public enum MatchStatus {
    @JsonProperty("scheduled") SCHEDULED {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return (next == LIVE || next == SCHEDULED);
        }
    },
    @JsonProperty("live") LIVE {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return (next == LIVE || next == FINISHED);
        }
    },
    @JsonProperty("finished") FINISHED {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return false;
        }
    };
    public abstract boolean canTransitionTo(MatchStatus next);

    @JsonCreator
    public static MatchStatus fromValue(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.toLowerCase()) {
            case "scheduled" -> SCHEDULED;
            case "live" -> LIVE;
            case "finished" -> FINISHED;
            default -> throw new IllegalArgumentException("Invalid MatchStatus: " + value + "should be one of:"
                    + Arrays.toString(MatchStatus.values()));
        };
    }
}
