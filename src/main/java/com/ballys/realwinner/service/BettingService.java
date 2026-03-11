package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.*;
import com.ballys.realwinner.repository.BetStore;
import com.ballys.realwinner.util.EventLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles all business logic related to placing and settling user bets.
 */
@Service
public class BettingService {

    private static final Logger log = LoggerFactory.getLogger(BettingService.class);

    private final BetStore betStore;
    private final MatchService matchService;
    private final EventLockManager lockManager;

    public BettingService(BetStore betStore, MatchService matchService, EventLockManager lockManager) {
        this.betStore = betStore;
        this.matchService = matchService;
        this.lockManager = lockManager;
    }

    /**
     * Places a new bet for a user on a specific match.
     * Grabs a "Read Lock" to ensure the match doesn't finish while the bet is being saved.
     *
     * @param req The details of the bet being placed (user, match, type, team).
     * @return The newly created Bet object with a PENDING status.
     * @throws IllegalStateException If the match is already finished or doesn't exist.
     * @throws IllegalArgumentException If the bet details (like the team chosen) are invalid.
     */
    public Bet placeBet(BetRequest req) {
        var lock = lockManager.getLockForEvent(req.eventId()).readLock();
        lock.lock();
        try {
            Match match = matchService.getMatch(req.eventId());

            if (match == null || match.status() == MatchStatus.FINISHED) {
                log.warn("Bet validation failed: Match {} is finished or missing", req.eventId());
                throw new IllegalStateException("Bets only allowed if match is scheduled or live");
            }

            Set<String> participantIds = match.participants().stream()
                    .map(Participant::id)
                    .collect(Collectors.toSet());

            if (req.betType().equals(BetType.WIN) && !participantIds.contains(req.participantId())) {
                throw new IllegalArgumentException("Invalid participant for WIN bet. Match participants: " + participantIds);
            }

            if (req.betType() == BetType.DRAW && req.participantId() != null) {
                throw new IllegalArgumentException("Draw bets must have a null participant ID");
            }

            String betId = "B" + UUID.randomUUID().toString().substring(0, 8);
            Bet bet = new Bet(betId, req.eventId(), req.userId(), req.betType(), req.participantId(), BetStatus.PENDING, null);

            betStore.save(bet);

            log.info("Bet {} successfully placed by user {} for match {}", betId, req.userId(), req.eventId());
            return bet;
        } finally {
            lock.unlock(); 
        }
    }

    /**
     * Automatically listens for a match to finish and settles all bets for that match.
     * Converts PENDING bets into WIN or LOSS based on the final outcome.
     *
     * @param event The system event containing the match ID and the winning team's ID.
     */
    @EventListener
    public void onMatchFinished(MatchFinishedEvent event) {
        String eventId = event.eventId();
        String winnerId = event.winnerParticipantId();

        log.info("MatchFinishedEvent received for match {}. Commencing bet settlement...", eventId);
        int settledCount = 0;

        for (Bet bet : betStore.findByEventId(eventId)) {
            if (bet.status() == BetStatus.PENDING) {
                BetStatus result = BetStatus.LOSS;

                if (bet.betType() == BetType.WIN && bet.participantId().equals(winnerId)) {
                    result = BetStatus.WIN;
                } else if (bet.betType() == BetType.DRAW && winnerId == null) {
                    result = BetStatus.WIN;
                }

                betStore.save(new Bet(
                        bet.betId(), bet.eventId(), bet.userId(), bet.betType(), bet.participantId(), null, result
                ));
                settledCount++;
            }
        }
        log.info("Settlement completed for match {}. Total bets settled: {}", eventId, settledCount);
    }

    /**
     * Retrieves all bets placed by a specific user, filtered by their current status.
     *
     * @param userId The ID of the user requesting their bets.
     * @param status The status to filter by (e.g., PENDING or SETTLED).
     * @return A list of the user's bets matching the requested status.
     */
    public List<Bet> getUserBets(String userId, BetStatusRequest status) {
        return betStore.findByUserId(userId).stream()
                .filter(b -> status == BetStatusRequest.PENDING ? b.status() == BetStatus.PENDING : b.result() != null)
                .toList();
    }
}