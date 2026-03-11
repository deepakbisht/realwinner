package com.ballys.realwinner.dto.request;

import com.ballys.realwinner.model.BetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record BetRequest(
        @NotNull String eventId,
        @NotNull String userId,
        @NotNull BetType betType,
        String participantId
) {
    @JsonIgnore
    @AssertTrue(message = "Invalid bet: WIN requires a participant, DRAW must have no participant")
    public boolean isValidBetCombination() {
        if (betType == BetType.WIN && participantId == null) return false;
        if (betType == BetType.DRAW && participantId != null) return false;
        return true;
    }
}
