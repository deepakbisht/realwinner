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

@RestController
@RequestMapping("/matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);
    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PutMapping("/upsert")
    public ResponseEntity<UpsertMatchResponse> upsertMatch(@Valid @RequestBody MatchRequest req) {
        log.info("REST request to upsert match with eventId: {}", req.eventId());
        Match match = matchService.addOrUpdateMatch(req);
        log.debug("Match {} upserted successfully with sequence {}", match.eventId(), match.sequence());
        UpsertMatchResponse upsertMatchResponse = new UpsertMatchResponse("Match event added", match.sequence());
        return new ResponseEntity<>(upsertMatchResponse, HttpStatus.OK);
    }

    @GetMapping("/query")
    public List<Match> getMatches() {
        log.debug("REST request to get all matches sorted by arrival");
        return matchService.getMatchesSorted();
    }

    @PatchMapping("/scores")
    public ResponseEntity<UpsertMatchResponse> updateScores(@Valid @RequestBody ScoreRequest req) {
        log.info("REST request to update scores for match: {}", req.eventId());
        Match match = matchService.updateScores(req);
        log.debug("Scores updated successfully for match {} with new sequence {}", match.eventId(), match.sequence());
        UpsertMatchResponse scoreUpdatedResponse = new UpsertMatchResponse("Score updated", match.sequence());
        return new ResponseEntity<>(scoreUpdatedResponse, HttpStatus.OK);
    }
}