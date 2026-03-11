package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.dto.request.ScoreRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.Match;
import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.model.Outcome;
import com.ballys.realwinner.model.Participant;
import com.ballys.realwinner.repository.MatchStore;
import com.ballys.realwinner.util.EventLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all business logic related to creating matches, updating their status, and recording scores.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchStore matchStore;
    private final ApplicationEventPublisher eventPublisher;
    private final EventLockManager lockManager;

    public MatchService(MatchStore matchStore, ApplicationEventPublisher eventPublisher, EventLockManager lockManager) {
        this.matchStore = matchStore;
        this.eventPublisher = eventPublisher;
        this.lockManager = lockManager;
    }

    /**
     * A simple helper class to safely pass results out of the locked map update.
     */
    private static class UpdateResult {
        boolean newlyFinished = false;
        String winnerId = null;
    }

    /**
     * Creates a new match or updates the status/details of an existing one.
     * If the match is finishing, it locks the event so no late bets can sneak in.
     *
     * @param req The incoming request containing the match details.
     * @return The newly saved Match object.
     */
    public Match addOrUpdateMatch(MatchRequest req) {
        if (req.status() == MatchStatus.FINISHED) {
            var lock = lockManager.getLockForEvent(req.eventId()).writeLock();
            lock.lock();
            try {
                return executeMatchUpsert(req);
            } finally {
                lock.unlock();
            }
        } else {
            return executeMatchUpsert(req);
        }
    }

    /**
     * The internal core logic that safely validates and saves the match to the store.
     * Automatically triggers settlement if the match transitions to FINISHED.
     *
     * @param req The incoming request containing the match details.
     * @return The successfully updated Match.
     * @throws IllegalArgumentException If the transition is invalid (e.g., trying to change teams mid-game).
     */
    private Match executeMatchUpsert(MatchRequest req) {
        final UpdateResult result = new UpdateResult();

        Match updatedMatch = matchStore.compute(req.eventId(), (eventId, existing) -> {
            if (sameMatch(req, existing)) return existing;

            if (existing == null && req.status() != MatchStatus.SCHEDULED) {
                throw new IllegalArgumentException("The first entry for the match should have scheduled status");
            } 
            else if (existing != null && !existing.status().canTransitionTo(req.status())) {
                throw new IllegalArgumentException("Invalid transition: " + existing.status() + " -> " + req.status());
            } 
            else if (existing != null && existing.status() == MatchStatus.LIVE && !areParticipantAndNameSame(req, existing)) {
                throw new IllegalArgumentException("Cannot update participants or name in a LIVE game.");
            }

            Map<String, Integer> scores = existing != null ? existing.scores() : null;

            if (req.status() == MatchStatus.FINISHED && (existing == null || existing.status() != MatchStatus.FINISHED)) {
                result.winnerId = calculateWinner(scores);
                result.newlyFinished = true;
                
                return new Match(
                        req.eventId(), req.name(), req.participants(), req.status(),
                        scores, matchStore.getNextSequence(), new Outcome(result.winnerId)
                );
            } else {
                return new Match(
                        req.eventId(), req.name(), req.participants(), req.status(),
                        scores, matchStore.getNextSequence(), existing != null ? existing.outcome() : null
                );
            }
        });

        if (result.newlyFinished) {
            log.info("Match {} finished. Publishing settlement event.", req.eventId());
            eventPublisher.publishEvent(new MatchFinishedEvent(req.eventId(), result.winnerId));
        }

        return updatedMatch;
    }

    /**
     * Updates the current scores for a live match.
     * Prevents out-of-order network messages from accidentally lowering a team's score.
     *
     * @param req The request containing the new score mapping.
     * @return The updated Match object.
     * @throws IllegalArgumentException If the match is not LIVE or the teams do not match.
     */
    public Match updateScores(ScoreRequest req) {
        return matchStore.compute(req.eventId(), (eventId, existing) -> {
            if (existing == null) {  log.warn("Failed to update scores. Match not found for eventId: {}", req.eventId());
                throw new IllegalArgumentException("Match not found with id:" + req.eventId());
            }
            if (existing.status() != MatchStatus.LIVE) throw new IllegalArgumentException("Scores can only be updated for a LIVE match");

            Set<String> participantIds = existing.participants().stream().map(Participant::id).collect(Collectors.toSet());
            if (!participantIds.equals(req.scores().keySet())) {
                log.warn("Participants must be same, match participants:{}, request participants:{}", participantIds, req.scores().keySet());
                throw new IllegalArgumentException("Participant must be same, existing:" + participantIds + " request:" + req.scores().keySet());
            }

            for (Map.Entry<String, Integer> incomingScore : req.scores().entrySet()) {
                Integer currentScore = existing.scores() != null ? existing.scores().getOrDefault(incomingScore.getKey(), 0) : 0;
                if (incomingScore.getValue() < currentScore) {
                    log.warn("Discarding out-of-order score update.");
                    return existing;
                }
            }

            if (req.scores().equals(existing.scores())) return existing;

            return new Match(
                    existing.eventId(), existing.name(), existing.participants(),
                    existing.status(), req.scores(), matchStore.getNextSequence(), existing.outcome()
            );
        });
    }

    /**
     * Retrieves a single match by its event ID.
     *
     * @param eventId The unique identifier of the match.
     * @return The Match object, or null if not found.
     */
    public Match getMatch(String eventId) {
        return matchStore.findById(eventId);
    }

    /**
     * Retrieves all matches currently in memory, sorted strictly by their arrival sequence.
     *
     * @return A sorted list of all matches.
     */
    public List<Match> getMatchesSorted() {
        return matchStore.findAll().stream().sorted(Comparator.comparingLong(Match::sequence)).toList();
    }

    /**
     * Helper method to determine the winning team based on the final scores.
     *
     * @param scores A map of participant IDs to their respective scores.
     * @return The ID of the winning team, or null if the match ended in a draw.
     */
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
        return isDraw ? null : winnerId;
    }

    /**
     * Helper method to verify that a match update hasn't illegally changed the playing teams or the match name.
     *
     * @param request The incoming update request.
     * @param existing The match currently stored in the database.
     * @return True if the names and participants are identical.
     */
    private static boolean areParticipantAndNameSame(MatchRequest request, Match existing) {
        return request.participants().equals(existing.participants()) && Objects.equals(request.name(), existing.name());
    }

    /**
     * Helper method to check if an incoming update is perfectly identical to the current state,
     * allowing us to skip unnecessary database writes and sequence increments.
     *
     * @param request The incoming update request.
     * @param existing The match currently stored in the database.
     * @return True if the incoming request changes absolutely nothing.
     */
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