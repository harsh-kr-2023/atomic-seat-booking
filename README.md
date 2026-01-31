# 🎫 Eventure | Atomic Seat Reservation Engine

A production-grade, high-concurrency seat booking system built with **Spring Boot**, **PostgreSQL**, and **Redis**. This experimental engine is designed to solve the "Thundering Herd" problem during major ticket releases, ensuring **zero double-bookings** and maximum system resilience.

## 🚀 Core Philosophy
> **"The Frontend only shows intent; the Backend decides the truth."**

This project focuses on **Atomic Correctness**. Using a combination of optimistic and pessimistic strategies, we ensure that even under massive parallel load, every seat is assigned to exactly one user.

---

## 🛠 Tech Stack
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL (Final Authority)
- **Cache/Locking**: Redis (Stampede Prevention)
- **Rate Limiting**: Bucket4j (Protection from Bots/Spam)
- **Concurrency**: Pessimistic Write Locking (`SELECT FOR UPDATE`)
- **Reliability**: Request Correlation IDs & MDC Logging

---

## 🏗 Key Features

### 1. Atomic State Machine
Seats follow a strict lifecycle: `AVAILABLE` -> `HELD` -> `BOOKED`.
- **Pessimistic Locking**: When a user attempts to hold a seat, the DB acquires a `FOR UPDATE` lock with a 2-second timeout.
- **Auto-Healing**: Expired holds (15 mins) are automatically reset during lookup to ensure seats aren't "stuck" in a held state.

### 2. Double-Layer Stampede Prevention (Soft Hold)
- **Tier 1 (Redis)**: A lightweight "Soft Hold" claim (15 sec) prevents multiple users from hitting the database for the same seat concurrently.
- **Tier 2 (Postgres)**: A durable "Hard Hold" that represents the formal reservation.
- **Benefit**: Redis absorbs ~95% of contention noise, keeping the primary DB fast and responsive.

### 3. Identity-Aware Security
- **Strict Ownership**: Every hold is cryptographically tied to a `userId` via `X-User-Id` headers.
- **Zero Cross-Leakage**: Users can only confirm or modify holds that belong specifically to them.
- **ThreadLocal Storage**: Centralized `UserContext` prevents "UserID parameter pollution" across the service layer.

### 4. Bulletproof Idempotency
- **Conflict Management**: All booking confirmations require an `X-Idempotency-Key`.
- **Safe Retries**: If a payment times out but succeeds in the background, retrying with the same key returns the original success response instead of creating a duplicate booking.
- **User Scoping**: Keys are scoped per user to prevent cross-account collisions.

### 5. Traffic Shaping
- **Rate Limits**: Configurable per-user, per-seat, and per-event limits using the Token Bucket algorithm.
- **Status 429**: Clean handling of bot-like behavior to preserve resources for legitimate fans.

---

## 🚦 Getting Started

### Prerequisites
- Java 17+
- PostgreSQL
- Redis

### Running Locally
1. Configure credentials in `src/main/resources/application.properties`.
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Open `http://localhost:8080` in your browser.

### Using the Frontend
The system includes a premium **Vanilla JS** frontend that allows you to:
- **Seed Events**: Initialize a fresh seat grid.
- **Simulate Actors**: Switch between Alice, Bob, and Charlie to test concurrency.
- **Real-time Sync**: Watch the grid update as other "users" interact with it.

---

## 🧪 Testing
The project includes a suite of reliability tests:
- **Crash Simulation**: Verifies transaction rollback on payment failure.
- **Concurrency Test**: Proves that only 1 out of 5 simultaneous requests succeeds.
- **Fast Failure**: Verifies that lock timeouts prevent request pile-ups.

Run tests:
```bash
./mvnw test
```

---

## 📜 One-Line Philosophy
**Authentication identifies the user; Atomic Correctness decides the outcome.**
