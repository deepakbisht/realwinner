package com.ballys.realwinner.dto.request;

import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.model.Participant;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MatchRequest(
        @NotNull String eventId,
        @NotNull String name,
        @Size(min = 2, max = 2, message = "There are exactly 2 unique participants in a football game")
        @NotNull Set<Participant> participants,
        @NotNull MatchStatus status
) {}
