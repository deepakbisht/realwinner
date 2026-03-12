package com.ballys.realwinner.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserBetResponse(
        String betId,
        String eventId,
        String betType,
        String participantId,
        String status, // Will be hidden if null
        String result  // Will be hidden if null
) {}