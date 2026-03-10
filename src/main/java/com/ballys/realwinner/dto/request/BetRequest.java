package com.ballys.realwinner.dto.request;

import com.ballys.realwinner.model.BetType;
import jakarta.validation.constraints.NotNull;

public record BetRequest(
        @NotNull String eventId,
        @NotNull String userId,
        @NotNull BetType betType,
        String participantId
) {}
