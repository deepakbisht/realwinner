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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("When match finishes with equal scores, calculates a draw and publishes event")
    void addOrUpdateMatch_ValidFinishWithEqualScores_CalculatesDraw() {
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.SCHEDULED));
        matchService.addOrUpdateMatch(new MatchRequest("E1", "Match", participants, MatchStatus.LIVE));

        // Set an equal score
        matchService.updateScores(new ScoreRequest("E1", Map.of("P1", 1, "P2", 1)));

        MatchRequest req = new MatchRequest("E1", "Match", participants, MatchStatus.FINISHED);
        Match result = matchService.addOrUpdateMatch(req);

        // Assert the outcome winner is null (Draw)
        assertNull(result.outcome().winnerParticipantId());

        ArgumentCaptor<MatchFinishedEvent> captor = ArgumentCaptor.forClass(MatchFinishedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        // Assert the event was published with a null winner
        assertEquals("E1", captor.getValue().eventId());
        assertNull(captor.getValue().winnerParticipantId());
    }

    @Test
    @DisplayName("Queries must return matches sorted by arrival order, updating dynamically after every action")
    void shouldDynamicallyResortMatchesAfterEveryUpdate() {

        // ==========================================
        // STEP 1: Initial Creation (Immutable Records, Sets, Enums)
        // ==========================================
        MatchRequest match1 = new MatchRequest("E001", "Arsenal vs Chelsea",
                Set.of(new Participant("P1", "Arsenal"), new Participant("P2", "Chelsea")), MatchStatus.SCHEDULED);
        matchService.addOrUpdateMatch(match1); // Sequence 1

        MatchRequest match2 = new MatchRequest("E002", "Real Madrid vs Barcelona",
                Set.of(new Participant("P3", "Real Madrid"), new Participant("P4", "Barcelona")), MatchStatus.SCHEDULED);
        matchService.addOrUpdateMatch(match2); // Sequence 2

        MatchRequest match3 = new MatchRequest("E003", "Bayern vs Dortmund",
                Set.of(new Participant("P5", "Bayern"), new Participant("P6", "Dortmund")), MatchStatus.SCHEDULED);
        matchService.addOrUpdateMatch(match3); // Sequence 3

        // ASSERT 1: Natural creation order
        List<Match> matchesAfterCreation = matchService.getMatchesSorted();
        assertThat(matchesAfterCreation)
                .extracting(Match::eventId)
                .containsExactly("E001", "E002", "E003"); // Most recent (E003) is last


        // ==========================================
        // STEP 2: Status Update on the Oldest Match
        // ==========================================
        // Creating a new immutable record to update the status to LIVE
        MatchRequest liveMatch1 = new MatchRequest("E001", "Arsenal vs Chelsea",
                Set.of(new Participant("P1", "Arsenal"), new Participant("P2", "Chelsea")), MatchStatus.LIVE);
        matchService.addOrUpdateMatch(liveMatch1); // Sequence 4

        // ASSERT 2: E001 just received an update, it must jump to the end
        List<Match> matchesAfterFirstStatusUpdate = matchService.getMatchesSorted();
        assertThat(matchesAfterFirstStatusUpdate)
                .extracting(Match::eventId)
                .containsExactly("E002", "E003", "E001");


        // ==========================================
        // STEP 3: Interleaving Status and Score Updates
        // ==========================================
        MatchRequest liveMatch2 = new MatchRequest("E002", "Real Madrid vs Barcelona",
                Set.of(new Participant("P3", "Real Madrid"), new Participant("P4", "Barcelona")), MatchStatus.LIVE);
        matchService.addOrUpdateMatch(liveMatch2); // Sequence 5 (E002 goes to end)

        ScoreRequest scoreUpdateM2 = new ScoreRequest("E002", Map.of("P3", 1, "P4", 0));
        matchService.updateScores(scoreUpdateM2); // Sequence 6 (E002 updates again, stays at end)

        ScoreRequest scoreUpdateM1 = new ScoreRequest("E001", Map.of("P1", 1, "P2", 1));
        matchService.updateScores(scoreUpdateM1); // Sequence 7 (E001 now jumps to the end)

        // ASSERT 3: E003 hasn't been touched since creation, E002 was updated, but E001 was updated LAST
        List<Match> matchesAfterInterleavedUpdates = matchService.getMatchesSorted();
        assertThat(matchesAfterInterleavedUpdates)
                .extracting(Match::eventId)
                .containsExactly("E003", "E002", "E001");


        // ==========================================
        // STEP 4: Status Update on the Ignored Match
        // ==========================================
        MatchRequest liveMatch3 = new MatchRequest("E003", "Bayern vs Dortmund",
                Set.of(new Participant("P5", "Bayern"), new Participant("P6", "Dortmund")), MatchStatus.LIVE);
        matchService.addOrUpdateMatch(liveMatch3); // Sequence 8 (E003 finally gets an update)

        // ASSERT 4: E003 jumps from first to last
        List<Match> matchesAfterM3Update = matchService.getMatchesSorted();
        assertThat(matchesAfterM3Update)
                .extracting(Match::eventId)
                .containsExactly("E002", "E001", "E003");


        // ==========================================
        // STEP 5: Final Score Update
        // ==========================================
        ScoreRequest finalScoreM2 = new ScoreRequest("E002", Map.of("P3", 2, "P4", 0));
        matchService.updateScores(finalScoreM2); // Sequence 9 (E002 jumps from first to last)

        // ASSERT 5: Final strict ordering check
        List<Match> finalMatches = matchService.getMatchesSorted();
        assertThat(finalMatches)
                .extracting(Match::eventId)
                .containsExactly("E001", "E003", "E002");

        // Extra assertion: Validate the sequence numbers themselves are strictly increasing
        assertThat(finalMatches.get(0).sequence())
                .isLessThan(finalMatches.get(1).sequence());

        assertThat(finalMatches.get(1).sequence())
                .isLessThan(finalMatches.get(2).sequence());
    }
}