package com.ballys.realwinner.service;

import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.event.MatchFinishedEvent;
import com.ballys.realwinner.model.Match;
import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.repository.MatchStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchStore matchStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MatchService matchService;

    @Test
    void addOrUpdateMatch_WhenScheduled_ShouldSaveAndNotPublishEvent() {
        MatchRequest req = new MatchRequest("E1", "Team A vs B", Set.of(), MatchStatus.SCHEDULED);
        when(matchStore.getNextSequence()).thenReturn(1L);

        Match result = matchService.addOrUpdateMatch(req);

        assertEquals(MatchStatus.SCHEDULED, result.status());
        verify(matchStore).save(any(Match.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void addOrUpdateMatch_WhenFinished_ShouldCalculateWinnerAndPublishEvent()  {
        MatchRequest req = new MatchRequest("E1", "Team A vs B", Set.of(), MatchStatus.FINISHED);
        
        // Simulate existing match with scores
        Match existingMatch = new Match("E1", "Team A vs B", Set.of(), MatchStatus.LIVE,
                                        Map.of("P1", 2, "P2", 1), 1L, null);
        when(matchStore.findById("E1")).thenReturn(existingMatch);
        when(matchStore.getNextSequence()).thenReturn(2L);

        Match result = matchService.addOrUpdateMatch(req);

        // Verify winner calculation
        assertEquals("P1", result.outcome().winnerParticipantId());

        // Verify Event was published
        ArgumentCaptor<MatchFinishedEvent> eventCaptor = ArgumentCaptor.forClass(MatchFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("E1", eventCaptor.getValue().eventId());
        assertEquals("P1", eventCaptor.getValue().winnerParticipantId());
    }
}