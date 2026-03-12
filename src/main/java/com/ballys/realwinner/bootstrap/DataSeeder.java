package com.ballys.realwinner.bootstrap;

import com.ballys.realwinner.dto.request.BetRequest;
import com.ballys.realwinner.dto.request.MatchRequest;
import com.ballys.realwinner.dto.request.ScoreRequest;
import com.ballys.realwinner.model.BetType;
import com.ballys.realwinner.model.MatchStatus;
import com.ballys.realwinner.model.Participant;
import com.ballys.realwinner.service.BettingService;
import com.ballys.realwinner.service.MatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final MatchService matchService;
    private final BettingService bettingService;

    public DataSeeder(MatchService matchService, BettingService bettingService) {
        this.matchService = matchService;
        this.bettingService = bettingService;
    }

    @Override
    public void run(String... args) {
        log.info("======================================================");
        log.info("🚀 Starting Data Population & Scenario Simulation...");
        log.info("======================================================");

        // ==========================================
        // SCENARIO 1: The Clear Winner (Finished)
        // Tests: Standard lifecycle, WIN settlement, LOSS settlement
        // ==========================================
        log.info("Seeding Scenario 1: Match E001 (Clear Winner)...");
        Set<Participant> match1Teams = Set.of(new Participant("P001", "Arsenal"), new Participant("P002", "Chelsea"));
        matchService.addOrUpdateMatch(new MatchRequest("E001", "Arsenal vs Chelsea", match1Teams, MatchStatus.SCHEDULED));
        
        // Bets placed while scheduled
        bettingService.placeBet(new BetRequest("E001", "U001", BetType.WIN, "P001")); // U001 bets Arsenal (Will WIN)
        bettingService.placeBet(new BetRequest("E001", "U002", BetType.WIN, "P002")); // U002 bets Chelsea (Will LOSE)
        bettingService.placeBet(new BetRequest("E001", "U003", BetType.DRAW, null));  // U003 bets Draw (Will LOSE)

        matchService.addOrUpdateMatch(new MatchRequest("E001", "Arsenal vs Chelsea", match1Teams, MatchStatus.LIVE));
        matchService.updateScores(new ScoreRequest("E001", Map.of("P001", 2, "P002", 0))); 
        
        // Triggers settlement: Arsenal wins
        matchService.addOrUpdateMatch(new MatchRequest("E001", "Arsenal vs Chelsea", match1Teams, MatchStatus.FINISHED));


        // ==========================================
        // SCENARIO 2: The Draw (Finished)
        // Tests: Tied scores, DRAW settlement logic
        // ==========================================
        log.info("Seeding Scenario 2: Match E002 (The Draw)...");
        Set<Participant> match2Teams = Set.of(new Participant("P003", "Real Madrid"), new Participant("P004", "Barcelona"));
        matchService.addOrUpdateMatch(new MatchRequest("E002", "El Clasico", match2Teams, MatchStatus.SCHEDULED));
        
        bettingService.placeBet(new BetRequest("E002", "U004", BetType.WIN, "P003")); // U004 bets Madrid (Will LOSE)
        bettingService.placeBet(new BetRequest("E002", "U005", BetType.DRAW, null));  // U005 bets Draw (Will WIN)

        matchService.addOrUpdateMatch(new MatchRequest("E002", "El Clasico", match2Teams, MatchStatus.LIVE));
        matchService.updateScores(new ScoreRequest("E002", Map.of("P003", 1, "P004", 1))); // Tied game!
        
        // Triggers settlement: Draw logic kicks in
        matchService.addOrUpdateMatch(new MatchRequest("E002", "El Clasico", match2Teams, MatchStatus.FINISHED));


        // ==========================================
        // SCENARIO 3: The Active Game (Live)
        // Tests: Live query state, pending bets, score updates
        // ==========================================
        log.info("Seeding Scenario 3: Match E003 (Currently LIVE)...");
        Set<Participant> match3Teams = Set.of(new Participant("P005", "Bayern Munich"), new Participant("P006", "Dortmund"));
        matchService.addOrUpdateMatch(new MatchRequest("E003", "Der Klassiker", match3Teams, MatchStatus.SCHEDULED));
        
        bettingService.placeBet(new BetRequest("E003", "U001", BetType.WIN, "P005")); // U001 bets again (Pending)
        bettingService.placeBet(new BetRequest("E003", "U006", BetType.DRAW, null));  // U006 bets Draw (Pending)

        matchService.addOrUpdateMatch(new MatchRequest("E003", "Der Klassiker", match3Teams, MatchStatus.LIVE));
        
        // Simulating sequence of live score updates
        matchService.updateScores(new ScoreRequest("E003", Map.of("P005", 1, "P006", 0))); 
        matchService.updateScores(new ScoreRequest("E003", Map.of("P005", 2, "P006", 1))); 
        
        // Game stays LIVE. Bets remain PENDING.


        // ==========================================
        // SCENARIO 4: The Future Game (Scheduled)
        // Tests: Pre-game betting, no scores yet
        // ==========================================
        log.info("Seeding Scenario 4: Match E004 (Future Game)...");
        Set<Participant> match4Teams = Set.of(new Participant("P007", "Juventus"), new Participant("P008", "AC Milan"));
        matchService.addOrUpdateMatch(new MatchRequest("E004", "Serie A Clash", match4Teams, MatchStatus.SCHEDULED));
        
        bettingService.placeBet(new BetRequest("E004", "U007", BetType.WIN, "P008")); // Early bet on AC Milan

        log.info("======================================================");
        log.info("✅ Data Population Complete! API is ready for testing.");
        log.info("   Try: GET /matches/query");
        log.info("   Try: GET /bets/U001?status=SETTLED");
        log.info("======================================================");
    }
}