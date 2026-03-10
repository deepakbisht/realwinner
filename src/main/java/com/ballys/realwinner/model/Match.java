package com.ballys.realwinner.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Set;

public record Match(
        String eventId,
        String name,
        Set<Participant> participants,
        MatchStatus status,
        Map<String, Integer> scores,
        long sequence,
        @JsonInclude(JsonInclude.Include.NON_NULL) Outcome outcome
) {}