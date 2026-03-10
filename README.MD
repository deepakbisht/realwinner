# Real Winner: Bally's Football Betting Service

## Overview
RealWinner is a lightweight service for managing football match events and user betting predictions. The concept behind the name is simple: regardless of which team wins or loses a match, the real winner is the person who correctly predicts the outcome.A single-instance Spring Boot REST API that manages football matches, live score updates, and bet settlements based on timed sequence arrivals. It handles "win" and "draw" bets, validating inputs and automatically settling pending bets when a match status changes to "finished".

## How to Run the Service
1. Requires Java 21 and Maven.
2. From the root directory, execute: `mvn spring-boot:run`
3. The API will be available at `http://localhost:8080`.

## Example Input/Output
**Add a Match** (`POST /api/match`)
```json
{
  "eventId": "E001",
  "name": "Team A vs Team B",
  "participants": [{ "id": "P001", "name": "Team A" }],
  "status": "scheduled"
}