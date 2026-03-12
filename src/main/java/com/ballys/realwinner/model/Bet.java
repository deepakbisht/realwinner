package com.ballys.realwinner.model;

public record Bet(
        String betId,
        String eventId,
        String userId,
        BetType betType,
        String participantId,
        BetStatus status
) {}