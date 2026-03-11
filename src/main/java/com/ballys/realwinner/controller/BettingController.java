package com.ballys.realwinner.controller;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.model.Bet;
import com.ballys.realwinner.service.BettingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for handling all user-facing betting operations.
 * Acts as the HTTP entry point for placing bets and querying bet history.
 */
@RestController
@RequestMapping("/bets")
public class BettingController {

    private static final Logger log = LoggerFactory.getLogger(BettingController.class);
    private final BettingService bettingService;

    public BettingController(BettingService bettingService) { 
        this.bettingService = bettingService; 
    }

    /**
     * Places a new bet for a user on a specific match.
     * Validates the incoming JSON payload before passing it to the service layer.
     *
     * @param req The validated payload containing the event ID, user ID, bet type, and chosen participant.
     * @return A map containing a success message, the generated bet ID, and the initial 'pending' status.
     */
    @PostMapping("/create")
    public Map<String, Object> placeBet(@Valid @RequestBody BetRequest req) {
        log.info("REST request to place {} bet for user {} on match {}", req.betType(), req.userId(), req.eventId());
        Bet b = bettingService.placeBet(req);
        log.debug("Bet {} created successfully with status pending", b.betId());
        return Map.of("message", "Bet placed", "betId", b.betId(), "status", "pending");
    }

    /**
     * Retrieves a user's betting history, filtered by the bet's current status.
     *
     * @param userId The unique identifier of the user.
     * @param status The required status to filter by (e.g., PENDING or SETTLED).
     * @return A list of bets matching the user and status criteria.
     */
    @GetMapping("/{userId}")
    public List<Bet> getUserBets(@PathVariable String userId, @RequestParam BetStatusRequest status) {
        log.debug("REST request to fetch bets for user {} with status {}", userId, status);
        return bettingService.getUserBets(userId, status);
    }
}