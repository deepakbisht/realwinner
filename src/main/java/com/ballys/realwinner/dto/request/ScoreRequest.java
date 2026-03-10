package com.ballys.realwinner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ScoreRequest(
        @NotNull String eventId,
        @Size(min = 2, max = 2, message = "There should be exactly two scores from different teams")
        @NotNull Map<String, Integer> scores
) {}
