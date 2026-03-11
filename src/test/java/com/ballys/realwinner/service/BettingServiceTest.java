package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.BetStatusRequest;
import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.*;
import com.ballys.realwinner.repository.BetStore;
import com.ballys.realwinner.repository.MatchStore;
import com.ballys.realwinner.util.EventLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BettingServiceTest {

    private BetStore betStore;
    private MatchStore matchStore;
    private EventLockManager lockManager;
    private MatchService matchService;
    private BettingService bettingService;

    private Set<Participant> participants;

    @BeforeEach
    void setUp() {
        betStore = new BetStore();
        matchStore = new MatchStore();
        lockManager = new EventLockManager();
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        matchService = new MatchService(matchStore, eventPublisher, lockManager);
        bettingService = new BettingService(betStore, matchService, lockManager);

        // Corrected to Set.of()
        participants = Set.of(
                new Participant("P1", "Team A"),
                new Participant("P2", "Team B")
        );
    }

    // --- PLACEMENT VALIDATIONS ---

    @Test
    void placeBet_MatchNotFound_ThrowsException() {
        BetRequest req = new BetRequest("E1", "U1", BetType.WIN, "P1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bettingService.placeBet(req));
        assertTrue(ex.getMessage().contains("Bets only allowed if match is scheduled or live"));
    }

    @Test
    void placeBet_MatchFinished_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.FINISHED));

        BetRequest req = new BetRequest("E1", "U1", BetType.WIN, "P1");
        assertThrows(IllegalStateException.class, () -> bettingService.placeBet(req));
    }

    @Test
    void placeBet_WinBetInvalidParticipant_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));

        BetRequest req = new BetRequest("E1", "U1", BetType.WIN, "P99");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(req));
        assertTrue(ex.getMessage().contains("Invalid participant for WIN bet"));
    }

    @Test
    void placeBet_DrawBetWithParticipant_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));

        BetRequest req = new BetRequest("E1", "U1", BetType.DRAW, "P1");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(req));
        assertTrue(ex.getMessage().contains("Draw bets must have a null participant ID"));
    }

    @Test
    void placeBet_Success() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));

        BetRequest req = new BetRequest("E1", "U1", BetType.WIN, "P1");
        Bet bet = bettingService.placeBet(req);

        assertNotNull(bet.betId());
        assertEquals(BetStatus.PENDING, bet.status());

        assertEquals(1, bettingService.getUserBets("U1", BetStatusRequest.PENDING).size());
    }

    // --- SETTLEMENT VALIDATIONS ---

    @Test
    void onMatchFinished_SettlesBetsCorrectly() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));

        bettingService.placeBet(new BetRequest("E1", "U1", BetType.WIN, "P1"));
        bettingService.placeBet(new BetRequest("E1", "U2", BetType.WIN, "P2"));
        bettingService.placeBet(new BetRequest("E1", "U3", BetType.DRAW, null));

        MatchFinishedEvent event = new MatchFinishedEvent("E1", "P1");
        bettingService.onMatchFinished(event);

        List<Bet> u1Bets = bettingService.getUserBets("U1", BetStatusRequest.SETTLED);
        List<Bet> u2Bets = bettingService.getUserBets("U2", BetStatusRequest.SETTLED);
        List<Bet> u3Bets = bettingService.getUserBets("U3", BetStatusRequest.SETTLED);

        assertEquals(BetStatus.WIN, u1Bets.get(0).result());
        assertEquals(BetStatus.LOSS, u2Bets.get(0).result());
        assertEquals(BetStatus.LOSS, u3Bets.get(0).result());
    }

    @Test
    void onMatchFinished_DrawSettlesCorrectly() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));

        bettingService.placeBet(new BetRequest("E1", "U1", BetType.DRAW, null));
        bettingService.placeBet(new BetRequest("E1", "U2", BetType.WIN, "P1"));

        MatchFinishedEvent event = new MatchFinishedEvent("E1", null);
        bettingService.onMatchFinished(event);

        assertEquals(BetStatus.WIN, bettingService.getUserBets("U1", BetStatusRequest.SETTLED).get(0).result());
        assertEquals(BetStatus.LOSS, bettingService.getUserBets("U2", BetStatusRequest.SETTLED).get(0).result());
    }
}