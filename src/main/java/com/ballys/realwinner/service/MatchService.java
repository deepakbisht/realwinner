package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.dto.request.ScoreRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.Match;
import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.model.Outcome;
import com.ballys.realwinner.repository.MatchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchStore matchStore;
    private final ApplicationEventPublisher eventPublisher;

    public MatchService(MatchStore matchStore, ApplicationEventPublisher eventPublisher) {
        this.matchStore = matchStore;
        this.eventPublisher = eventPublisher;
    }

    public Match addOrUpdateMatch(MatchRequest req) {
        Match existing = matchStore.findById(req.eventId());
        // If it is same update, return the older one.
        if (sameMatch(req, existing)) {
            return existing;
        }
        // It is a must that first event of the match has scheduled status
        if (existing == null && req.status() != MatchStatus.SCHEDULED) {
            throw new IllegalArgumentException("The first entry for the match should have scheduled status");
            // Match Status can only move in one direction, not back and forth. Like After Schedule, you can only to live.
        } else if (existing != null && existing.status().canTransitionTo(req.status())) {
            throw new IllegalArgumentException("Invalid transition: " + existing.status() + " -> " + req.status());
        } else {
            Map<String, Integer> scores = existing != null ? existing.scores() : null;
            Match updated;
            // If the Match is finished update the database and publish event for eventPublisher.
            if (req.status() == MatchStatus.FINISHED) {
                String winnerId = calculateWinner(scores);
                updated = new Match(
                        req.eventId(), req.name(), req.participants(), req.status(),
                        scores, matchStore.getNextSequence(), new Outcome(winnerId)
                );
                matchStore.save(updated);
                log.info("Match {} finished. Winner calculated as: {}. Publishing settlement event.", req.eventId(), winnerId);
                eventPublisher.publishEvent(new MatchFinishedEvent(req.eventId(), winnerId));
            } else {
                // Update the Event.
                updated = new Match(
                        req.eventId(), req.name(), req.participants(), req.status(),
                        scores, matchStore.getNextSequence(), null
                );
                matchStore.save(updated);
                log.info("Match {} upserted with status: {}", req.eventId(), req.status());
            }
            return updated;
        }
    }

    public Match updateScores(ScoreRequest req) {
        Match existing = matchStore.findById(req.eventId());
        if (existing == null) {
            log.warn("Failed to update scores. Match not found for eventId: {}", req.eventId());
            throw new IllegalArgumentException("Match not found");
        }

        Match updated = new Match(
                existing.eventId(), existing.name(), existing.participants(),
                existing.status(), req.scores(), matchStore.getNextSequence(), existing.outcome()
        );
        matchStore.save(updated);
        log.info("Scores updated for match {}: {}", req.eventId(), req.scores());
        return updated;
    }

    public Match getMatch(String eventId) {
        return matchStore.findById(eventId);
    }

    public List<Match> getMatchesSorted() {
        return matchStore.findAll().stream()
                .sorted(Comparator.comparingLong(Match::sequence))
                .toList();
    }

    private String calculateWinner(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) return null;
        int maxScore = -1;
        String winnerId = null;
        boolean isDraw = false;

        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winnerId = entry.getKey();
                isDraw = false;
            } else if (entry.getValue() == maxScore) {
                isDraw = true;
            }
        }
        log.debug("Winner calculated. Top score: {}, Winner ID: {}, Is Draw: {}", maxScore, winnerId, isDraw);
        return isDraw ? null : winnerId;
    }

    private static boolean sameMatch(MatchRequest request, Match existing) {
        if (existing == null || request == null) {
            return false;
        }
        return Objects.equals(request.eventId(), existing.eventId())
                && Objects.equals(request.name(), existing.name())
                && Objects.equals(request.participants(), existing.participants())
                && Objects.equals(request.status(), existing.status());
    }
}