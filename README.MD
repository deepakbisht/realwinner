# Real Winner: Bally's Football Betting Service

## Overview
RealWinner is a lightweight service for managing football match events and user betting predictions. The concept behind the name is simple: regardless of which team wins or loses a match, the real winner is the person who correctly predicts the outcome.A single-instance Spring Boot REST API that manages football matches, live score updates, and bet settlements based on timed sequence arrivals. It handles "win" and "draw" bets, validating inputs and automatically settling pending bets when a match status changes to "finished".

Because this application is required to run without an external database, it uses advanced Java concurrency features to handle thousands of requests per second safely, ensuring no data is lost or corrupted when multiple updates happen at the exact same millisecond.

## 🚀 Tech Stack
* **Java 21**
* **Spring Boot 3.3.x**
* **In-Memory Storage:** `ConcurrentHashMap` (No external database)
* **Testing:** JUnit 5, Mockito, Multithreaded Concurrency Testing
* **Documentation:** Springdoc OpenAPI (Swagger)

---

## 🧠 Architecture & Thread Safety

Handling live sports data requires strict thread safety. We cannot let two network requests overwrite each other. To solve this without a database, this application uses a **Two-Tier Locking Strategy**:



### 1. Data-Level Locks (For High-Speed Updates)
For fast, frequent updates like live scores, we use `ConcurrentHashMap.compute()`.
* **Why?** It locks only the specific match being updated, not the entire map.
* **Benefit:** If Match A gets two score updates at the same time, they are processed one after the other safely. Meanwhile, users can still freely update Match B without waiting.

### 2. Domain-Level Locks (To Prevent "Ghost Bets")
What happens if a user places a bet at the *exact same millisecond* a match finishes? To prevent saving a "Ghost Bet" on a finished game, we built a custom `EventLockManager` using `ReentrantReadWriteLock`.
* **Placing a Bet (Read Lock):** Thousands of users can place bets concurrently on a LIVE match without slowing each other down.
* **Finishing a Match (Write Lock):** When a match finishes, the system claims a "Write Lock". This temporarily pauses new bets for *that specific match only*, settles all pending bets accurately, and then permanently locks the match.

### 3. O(1) Fast Queries
Instead of scanning every bet in the system to find a user's history, the `BetStore` uses dual partitioned indexes (`betsByUserId` and `betsByEventId`). This means finding a user's bets or settling a finished match takes O(1) time, providing massive performance improvements.

---

## 📌 Business Rules, Test Cases, & Assumptions

### 1. Architectural & System Constraints
* **Ephemeral In-Memory Storage:** All data is stored in memory and will be lost upon application restart. It is assumed that JVM heap memory is sufficient for the expected single-instance load.
* **No Historical Time-Series:** Only the absolute latest state and score are maintained for a match. The system does not store a historical log of previous score updates.
* **Idempotent Updates:** Events are treated as unique only if the data has mutated. If an incoming match update contains the exact same status and scores, the system ignores it to prevent unnecessary database writes and sequence increments.
* **Authentication Out of Scope:** Because a User Service does not exist in this boundary, there is no login or session flow. `userId` is treated as an arbitrary client-provided string, and APIs are publicly callable.

### 2. Upstream Integration (The "Source of Truth")

* **Delegated Validations:** The upstream event source (e.g., Kafka) is the absolute source of truth. This service assumes the upstream system has already validated that a team isn't playing in two places at once, that participant IDs are unique, and that the match name matches the participants.
* **Message Ordering:** It is assumed that upstream match and status events arrive in chronological order.
* **Event Reconciliation:** Handling failed event consumption, message replays, or dead-letter queues is considered out of scope for this specific exercise.

### 3. Match & Score Domain Rules
* **Strict Lifecycle:** Matches must follow a strict status progression: `SCHEDULED` -> `LIVE` -> `FINISHED`. A match cannot transition to `FINISHED` without first being `LIVE`.
* **Live State Lock:** Once a match transitions to `LIVE`, the participant entities and match name become immutable and cannot be changed.
* **Score Prerequisite:** A match must physically exist in the system and be in a `LIVE` state before any score updates are accepted. Scores received for `SCHEDULED` matches are rejected, rather than automatically forcing the match to `LIVE`, to maintain strict state consistency.
* **Participant Validation:** Score updates will only be processed if the participant IDs perfectly match the entities actively playing in that specific event.
* **Terminal State Lock:** Once a match is `FINISHED`, no further score or status updates will be accepted.
* **Draw Fallback:** If a match completes without ever receiving a score update, the system safely calculates the outcome as a DRAW.

### 4. Betting Domain Rules
* **Placement Prerequisites:** For a bet to be placed, the match must exist, and the match status must be either `SCHEDULED` or `LIVE`.
* **Immutability:** Once a bet is successfully placed, it cannot be updated or deleted by the user.
* **WIN vs. DRAW Validations:** * A `WIN` bet requires a participant ID, and that participant *must* be actively playing in the referenced match.
    * A `DRAW` bet strictly requires the participant ID to be null.
* **Independent Execution:** Match creation and score updates will process successfully regardless of whether any bets have been placed on that match.

---

## 🛠️ How to Run

### Prerequisites
* Java 21 installed on your machine.
* Maven (Optional, as the project includes the Maven Wrapper).

### Running Locally
Open your terminal in the project folder and run:

```bash
# 1. Clean and build the project
./mvnw clean install

# 2. Start the application
./mvnw spring-boot:run
Once running, you can view the fully documented Swagger UI at:

👉 http://localhost:8080/swagger-ui.html

Note: The application includes a DataSeeder that automatically creates a few test matches, adds scores, and places some bets on startup so you can immediately test the GET endpoints!
It is commented but will run when uncommented.

📡 API Reference
1. Upsert Match
Creates a new match or updates the status of an existing match.

Endpoint: PUT /matches/upsert

Payload:

JSON
{
  "eventId": "E001",
  "name": "Team A vs Team B",
  "participants": [
    { "id": "P001", "name": "Team A" },
    { "id": "P002", "name": "Team B" }
  ],
  "status": "SCHEDULED"
}
2. Update Scores
Updates the current score for a live match.

Endpoint: PATCH /matches/scores

Payload:

JSON
{
  "eventId": "E001",
  "scores": {
    "P001": 1,
    "P002": 0
  }
}

3. Query Matches
Retrieves all matches currently in memory, sorted chronologically by when they were updated.

Endpoint: GET /matches/query

4. Place a Bet
Places a new WIN or DRAW bet for a user.

Endpoint: POST /bets/create

Payload (WIN Bet):

JSON
{
  "eventId": "E001",
  "userId": "U001",
  "betType": "WIN",
  "participantId": "P001"
}
5. Get User Bets
Retrieves all bets for a specific user, filtered by their status (PENDING or SETTLED).

Endpoint: GET /bets/{userId}?status=PENDING

🧪 Automated Test Suite
This project includes a comprehensive JUnit 5 test suite.

To run the tests:

Bash
./mvnw test
