package com.ballys.realwinner.controller;

import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.dto.request.ScoreRequest;
import com.ballys.realwinner.dto.response.UpsertMatchResponse;
import com.ballys.realwinner.model.Match;
import com.ballys.realwinner.service.MatchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing football matches and score updates.
 * Serves as the HTTP ingestion point for match events, ensuring they are correctly routed
 * to the service layer for sequential and thread-safe processing.
 */
@RestController
@RequestMapping("/matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);
    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * Creates a new match or updates the state of an existing match (e.g., transitioning from SCHEDULED to LIVE).
     *
     * @param req The validated payload containing match details and the new status.
     * @return A standard response containing a success message and the new sequence number of the update.
     */
    @PutMapping("/upsert")
    public ResponseEntity<UpsertMatchResponse> upsertMatch(@Valid @RequestBody MatchRequest req) {
        log.info("REST request to upsert match with eventId: {}", req.eventId());
        Match match = matchService.addOrUpdateMatch(req);
        log.debug("Match {} upserted successfully with sequence {}", match.eventId(), match.sequence());
        UpsertMatchResponse upsertMatchResponse = new UpsertMatchResponse("Match event added", match.sequence());
        return new ResponseEntity<>(upsertMatchResponse, HttpStatus.OK);
    }

    /**
     * Retrieves the current state of all matches in the system.
     * The results are strictly ordered by their sequence number to reflect the chronological timeline of events.
     *
     * @return A sequentially sorted list of all active and finished matches.
     */
    @GetMapping("/query")
    public List<Match> getMatches() {
        log.debug("REST request to get all matches sorted by arrival");
        return matchService.getMatchesSorted();
    }

    /**
     * Updates the current scores for a LIVE match.
     *
     * @param req The validated payload containing the event ID and the updated score mapping.
     * @return A standard response containing a success message and the new sequence number of the update.
     */
    @PatchMapping("/scores")
    public ResponseEntity<UpsertMatchResponse> updateScores(@Valid @RequestBody ScoreRequest req) {
        log.info("REST request to update scores for match: {}", req.eventId());
        Match match = matchService.updateScores(req);
        log.debug("Scores updated successfully for match {} with new sequence {}", match.eventId(), match.sequence());
        UpsertMatchResponse scoreUpdatedResponse = new UpsertMatchResponse("Score updated", match.sequence());
        return new ResponseEntity<>(scoreUpdatedResponse, HttpStatus.OK);
    }
}