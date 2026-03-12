package com.ballys.realwinner.controller;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.dto.response.BetCreateResponse;
import com.ballys.realwinner.dto.response.UserBetResponse;
import com.ballys.realwinner.model.Bet;
import com.ballys.realwinner.model.BetStatus;
import com.ballys.realwinner.service.BettingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
     * Places a new bet on a specific match for a user.
     * <p>
     * Validates that the match exists, is open for betting, and that the user
     * has not already placed a bet on this event.
     *
     * @param request The payload containing the event, user, and bet details.
     * @return A {@link ResponseEntity} containing the {@link BetCreateResponse} with HTTP 200 OK.
     */
    @PostMapping("/create")
    public ResponseEntity<BetCreateResponse> placeBet(@RequestBody BetRequest request) {
        Bet savedBet = bettingService.placeBet(request);

        BetCreateResponse response = new BetCreateResponse(
                "Bet placed",
                savedBet.betId(),
                savedBet.status().name().toLowerCase()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all bets placed by a specific user, optionally filtered by their status.
     * <p>
     * Dynamically maps internal bet entities to API-compliant DTOs, ensuring that
     * pending bets expose a "status" field, while settled bets expose a "result" field.
     *
     * @param userId The unique identifier of the user.
     * @param status The status to filter by (e.g., "pending", "settled", or "all"). Defaults to "all".
     * @return A {@link ResponseEntity} containing a list of {@link UserBetResponse} with HTTP 200 OK.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<UserBetResponse>> getUserBets(
            @PathVariable String userId,
            @RequestParam(defaultValue = "all") String status) {
        BetStatusRequest betStatusRequest = BetStatusRequest.fromValue(status);
        List<Bet> bets = bettingService.getUserBets(userId, betStatusRequest);

        List<UserBetResponse> responseList = bets.stream()
                .map(bet -> {
                    // Check if the internal bet status indicates it has been settled
                    boolean isSettled = BetStatus.WON.equals(bet.status()) || BetStatus.LOST.equals(bet.status());

                    return new UserBetResponse(
                            bet.betId(),
                            bet.eventId(),
                            bet.betType().name().toLowerCase(),
                            bet.participantId(),
                            isSettled ? null : "pending",                  // Show 'status' only if pending
                            isSettled ? bet.status().name().toLowerCase() : null  // Show 'result' only if settled
                    );
                })
                .toList();

        return ResponseEntity.ok(responseList);
    }
}