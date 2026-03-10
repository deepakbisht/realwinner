package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.*;
import com.ballys.realwinner.repository.BetStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetServiceTest {

    @Mock
    private BetStore betStore;

    @Mock
    private MatchService matchService;

    @InjectMocks
    private BettingService bettingService;

    @Test
    void placeBet_FailsIfMatchFinished()  {
        BetRequest req = new BetRequest("E1", "U1", BetType.WIN, "P1");
        Match finishedMatch = new Match("E1", "A vs B", Set.of(), MatchStatus.FINISHED, Map.of(), 1L, new Outcome("P1"));
        
        when(matchService.getMatch("E1")).thenReturn(finishedMatch);

        assertThrows(IllegalStateException.class, () -> bettingService.placeBet(req));
        verify(betStore, never()).save(any());
    }

    @Test
    void placeBet_FailsIfDrawHasParticipantId()  {
        BetRequest req = new BetRequest("E1", "U1", BetType.DRAW, "P1");
        Match scheduledMatch = new Match("E1", "A vs B", Set.of(), MatchStatus.SCHEDULED, Map.of(), 1L, null);
        
        when(matchService.getMatch("E1")).thenReturn(scheduledMatch);

        assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(req));
    }

    @Test
    void onMatchFinished_ShouldSettlePendingBets()  {
        // Setup pending bets in the mock store
        Bet pendingWinBet = new Bet("B1", "E1", "U1", BetType.WIN, "P1", BetStatus.PENDING, null);
        Bet pendingDrawBet = new Bet("B2", "E1", "U2", BetType.DRAW, null, BetStatus.PENDING, null);
        when(betStore.findAll()).thenReturn(List.of(pendingWinBet, pendingDrawBet));

        // Simulate the event coming from MatchService
        MatchFinishedEvent event = new MatchFinishedEvent("E1", "P1");
        
        // Trigger the listener
        bettingService.onMatchFinished(event);

        // Verify that save was called with the updated WIN/LOSS statuses
        verify(betStore).save(argThat(bet -> bet.betId().equals("B1") && bet.result() == BetStatus.WIN));
        verify(betStore).save(argThat(bet -> bet.betId().equals("B2") && bet.result() == BetStatus.LOSS));
    }
}