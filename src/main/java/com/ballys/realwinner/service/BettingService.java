package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.*;
import com.ballys.realwinner.repository.BetStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BettingService {

    private static final Logger log = LoggerFactory.getLogger(BettingService.class);

    private final BetStore betStore;
    private final MatchService matchService;

    public BettingService(BetStore betStore, MatchService matchService) {
        this.betStore = betStore;
        this.matchService = matchService;
    }

    public Bet placeBet(BetRequest req) {
        Match match = matchService.getMatch(req.eventId());
        if (match == null || match.status() == MatchStatus.FINISHED) {
            log.warn("Bet validation failed: User {} attempted to bet on a finished or non-existent match: {}", req.userId(), req.eventId());
            throw new IllegalStateException("Bets only allowed if match is scheduled or live");
        }
        String betId = "B" + UUID.randomUUID().toString().substring(0, 4);
        Bet bet = new Bet(betId, req.eventId(), req.userId(), req.betType(), req.participantId(), BetStatus.PENDING, null);
        betStore.save(bet);
        
        log.info("Bet {} successfully placed by user {} for match {} (Type: {})", betId, req.userId(), req.eventId(), req.betType());
        return bet;
    }

    @EventListener
    public void onMatchFinished(MatchFinishedEvent event) {
        String eventId = event.eventId();
        String winnerId = event.winnerParticipantId();
        
        log.info("MatchFinishedEvent received for match {}. Commencing bet settlement...", eventId);
        int settledCount = 0;

        for (Bet bet : betStore.findAll()) {
            if (bet.eventId().equals(eventId) && bet.status() == BetStatus.PENDING) {
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
                log.debug("Settled bet {} with result: {}", bet.betId(), result);
            }
        }
        log.info("Bet settlement completed for match {}. Total bets settled: {}", eventId, settledCount);
    }

    public List<Bet> getUserBets(String userId, BetStatusRequest status) {
        return betStore.findAll().stream()
                .filter(b -> b.userId().equals(userId))
                .filter(b -> status == BetStatusRequest.PENDING ? b.status() == BetStatus.PENDING : b.result() != null)
                .toList();
    }
}