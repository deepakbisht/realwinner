package com.ballys.realwinner.controller;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.model.Bet;
import com.ballys.realwinner.model.BetType;
import com.ballys.realwinner.service.BettingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bets")
public class BettingController {

    private static final Logger log = LoggerFactory.getLogger(BettingController.class);
    private final BettingService bettingService;

    public BettingController(BettingService bettingService) { this.bettingService = bettingService; }

    @PostMapping("/create")
    public Map<String, Object> placeBet(@Valid @RequestBody BetRequest req) {
        log.info("REST request to place {} bet for user {} on match {}", req.betType(), req.userId(), req.eventId());
        if (req.betType() == BetType.WIN && req.participantId() == null) {
            log.warn("Bet validation failed: User {} submitted a WIN bet without a participant ID for match {}", req.userId(), req.eventId());
            throw new IllegalArgumentException("Win bets must specify a participant ID");
        }
        if (req.betType() == BetType.DRAW && req.participantId() != null) {
            log.warn("Bet validation failed: User {} submitted a DRAW bet but included a participant ID for match {}", req.userId(), req.eventId());
            throw new IllegalArgumentException("Draw bets must have a null participant ID");
        }
        Bet b = bettingService.placeBet(req);
        log.debug("Bet {} created successfully with status pending", b.betId());
        return Map.of("message", "Bet placed", "betId", b.betId(), "status", "pending");
    }

    @GetMapping("/{userId}")
    public List<Bet> getUserBets(@PathVariable String userId, @RequestParam BetStatusRequest status) {
        log.debug("REST request to fetch bets for user {} with status {}", userId, status);
        return bettingService.getUserBets(userId, status);
    }
}