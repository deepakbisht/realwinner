package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MatchStatus {
    @JsonProperty("scheduled") SCHEDULED {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return next == LIVE;
        }
    },
    @JsonProperty("live") LIVE {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return next == FINISHED;
        }
    },
    @JsonProperty("finished") FINISHED {
        @Override
        public boolean canTransitionTo(MatchStatus next) {
            return false;
        }
    };
    public abstract boolean canTransitionTo(MatchStatus next);
}
