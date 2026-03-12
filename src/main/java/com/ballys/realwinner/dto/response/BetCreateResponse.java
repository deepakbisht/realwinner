package com.ballys.realwinner.dto.response;

public record BetCreateResponse(
        String message,
        String betId,
        String status
) {}