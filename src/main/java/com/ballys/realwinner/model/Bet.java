package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public record Bet(
        String betId,
        String eventId,
        String userId,
        BetType betType,
        String participantId,
        @JsonInclude(JsonInclude.Include.NON_NULL) BetStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) BetStatus result
) {}
