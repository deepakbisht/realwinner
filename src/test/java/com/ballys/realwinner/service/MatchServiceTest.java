package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.dto.request.ScoreRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.Match;
import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.model.Participant;
import com.ballys.realwinner.repository.MatchStore;
import com.ballys.realwinner.util.EventLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private MatchStore matchStore;
    private EventLockManager lockManager;
    private MatchService matchService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Set<Participant> participants;

    @BeforeEach
    void setUp() {
        matchStore = new MatchStore();
        lockManager = new EventLockManager();
        matchService = new MatchService(matchStore, eventPublisher, lockManager);

        // Corrected to Set.of()
        participants = Set.of(
                new Participant("P1", "Team A"),
                new Participant("P2", "Team B")
        );
    }

    // --- UPSERT VALIDATIONS ---

    @Test
    void addOrUpdateMatch_FirstEventNotScheduled_ThrowsException() {
        MatchRequest req = new MatchRequest("E1", "Match", participants, MatchStatus.LIVE);
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matchService.addOrUpdateMatch(req));
        assertTrue(ex.getMessage().contains("scheduled status"));
    }

    @Test
    void addOrUpdateMatch_InvalidTransition_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        
        MatchRequest req = new MatchRequest("E1", "Match", participants, MatchStatus.FINISHED);
        
        assertThrows(IllegalArgumentException.class, () -> matchService.addOrUpdateMatch(req));
    }

    @Test
    void addOrUpdateMatch_LiveMatchParticipantChange_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));

        // Corrected to Set.of() with a new participant
        Set<Participant> newParticipants = Set.of(
                new Participant("P3", "Team C"), 
                new Participant("P2", "Team B")
        );
        MatchRequest req = new MatchRequest("E1", "Match", newParticipants, MatchStatus.LIVE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matchService.addOrUpdateMatch(req));
        assertTrue(ex.getMessage().contains("Cannot update participants or name in a LIVE game"));
    }

    @Test
    void addOrUpdateMatch_ValidFinish_PublishesEventAndCalculatesWinner() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));
        matchService.updateScores(new ScoreRequest("E1", Map.of("P1", 2, "P2", 1)));

        MatchRequest req = new MatchRequest("E1", "Match", participants, MatchStatus.FINISHED);
        Match result = matchService.addOrUpdateMatch(req);

        assertEquals("P1", result.outcome().winnerParticipantId());

        ArgumentCaptor<MatchFinishedEvent> captor = ArgumentCaptor.forClass(MatchFinishedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("E1", captor.getValue().eventId());
        assertEquals("P1", captor.getValue().winnerParticipantId());
    }

    // --- SCORE VALIDATIONS ---

    @Test
    void updateScores_MatchNotLive_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        
        ScoreRequest req = new ScoreRequest("E1", Map.of("P1", 1, "P2", 0));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matchService.updateScores(req));
        assertTrue(ex.getMessage().contains("Scores can only be updated for a LIVE match"));
    }

    @Test
    void updateScores_ParticipantMismatch_ThrowsException() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));
        
        ScoreRequest req = new ScoreRequest("E1", Map.of("P1", 1, "P99", 0)); 
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matchService.updateScores(req));
        assertTrue(ex.getMessage().contains("Participant must be same"));
    }

    @Test
    void updateScores_OutofOrder_DiscardsLowerScore() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));
        
        matchService.updateScores(new ScoreRequest("E1", Map.of("P1", 2, "P2", 1)));
        
        Match result = matchService.updateScores(new ScoreRequest("E1", Map.of("P1", 1, "P2", 1)));
        
        assertEquals(2, result.scores().get("P1")); 
    }
}