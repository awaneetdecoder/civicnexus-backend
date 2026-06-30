# CivicNexus — Backend

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Gemini](https://img.shields.io/badge/AI-Gemini%202.5%20Flash-8E44AD)
![Auth](https://img.shields.io/badge/Auth-JWT-black?logo=jsonwebtokens)
![Status](https://img.shields.io/badge/status-core%20complete-brightgreen)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

**Spring Boot REST API for CivicNexus — an AI-powered civic issue reporting platform with Gemini multimodal analysis, JWT auth, and a layered geospatial fraud-detection pipeline.**

📱 **Companion repo — Flutter client:** [`civicnexus-app`](https://github.com/awaneetdecoder/civicnexus-app)

> **Architecture note.** This repository is the full production design — Spring Boot + Flutter, MySQL, layered fraud detection. The hackathon's mandatory live demo runs a lighter Firebase-hosted web client implementing the same AI pipeline (Gemini categorization, severity scoring, maps, community upvoting, gamified rewards), built to ship reliably inside a hard deadline on a free-tier Google Cloud stack. **Live demo:** [civicnexus-94d0b.web.app](https://civicnexus-94d0b.web.app)

---

## Table of Contents
- [What This Does](#what-this-does)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Key Features](#key-features)
- [Project Structure](#project-structure)
- [Issue Status Flow](#issue-status-flow)
- [Running Locally](#running-locally)
- [API Reference](#api-reference)
- [What's Built](#whats-built)
- [Roadmap](#roadmap)
- [Known Limitations](#known-limitations)
- [Engineering Decisions](#engineering-decisions)
- [What This Project Demonstrates](#what-this-project-demonstrates)
- [Author](#author)

---

## What This Does

Citizens photograph a civic problem — a pothole, broken streetlight, water leakage, garbage dump. The backend sends the image to Gemini 2.5 Flash, which returns a structured JSON analysis: issue type, severity score, responsible municipal department, estimated resolution time, and a citizen advisory. The report is saved to MySQL with GPS coordinates and is immediately visible to nearby citizens. Municipal workers who claim to resolve an issue must pass GPS proximity validation, timestamp verification, and a Gemini before/after image comparison before the system accepts the resolution.

## Architecture

```
Flutter App
    │
    ├── POST /api/auth/signup|login     → JWT token returned
    ├── POST /api/issues                → image + GPS → Gemini analysis → MySQL
    ├── GET  /api/issues/all            → all issues for map display (public)
    ├── GET  /api/issues/mine           → current user's reports
    ├── POST /api/issues/{id}/upvote    → community verification
    ├── POST /api/issues/{id}/resolve   → fraud check + Gemini comparison
    └── GET  /api/issues/{id}/displacement-check → supervisor anomaly detection
```

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5, Java 17 |
| Security | Spring Security, JWT (jjwt 0.11.5), BCrypt |
| Database | MySQL 8.0, JPA / Hibernate, Spring Data JPA |
| AI | Gemini 2.5 Flash (multimodal — image + text) |
| HTTP Client | OkHttp 4.12 |
| Build | Maven |

## Key Features

**Gemini multimodal analysis.** Every submitted photo is sent to Gemini 2.5 Flash with a structured prompt that returns JSON containing issue type, severity (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), an urgency score (1–10), the responsible department (PWD, Municipal Corporation, Electricity Board, Water Board, Traffic Police), estimated resolution days, and a citizen advisory message.

**Resolution fraud detection.** Three constraints run before any resolution is accepted: GPS proximity (resolver must be within 50 meters of the reported location, computed via the Haversine formula), timestamp validation (the resolution photo must be taken within 30 minutes of submission and after the original report), and a Gemini before/after image comparison that must clear a 60% confidence threshold for automatic resolution.

**Displacement anomaly detection.** After a resolution is accepted, a native SQL query — also Haversine-based — checks whether a new issue of the same type appears within 1km within 2 hours. This flags the pattern of a worker relocating waste rather than disposing of it, surfacing it to a supervisor instead of silently closing the loop.

**Community verification.** Issues auto-upgrade from `REPORTED` to `VERIFIED` once they receive 3 or more citizen upvotes, and verified issues are prioritized in the municipal queue.

**Gamification.** Citizens earn Swachh-Coins per report, scaled to AI-assessed severity: `CRITICAL=50, HIGH=30, MEDIUM=20, LOW=10`. Balances are stored per user and surfaced in the Flutter profile screen.

## Project Structure

```
src/main/java/com/swachhdrishti/swachh_drishti/
├── controller/
│   ├── AuthController.java       # signup, login, /me
│   ├── IssueController.java      # submit, all, mine, upvote, resolve
│   └── ReportController.java     # legacy report endpoints
├── service/
│   ├── AuthService.java          # user registration and login logic
│   ├── IssueService.java         # core business logic
│   ├── GeminiService.java        # Gemini API integration
│   ├── FraudDetectionService.java # GPS + timestamp validation
│   ├── JwtService.java           # token generation and validation
│   └── ReportService.java        # legacy report service
├── entity/
│   ├── Issue.java                # main entity with all AI fields
│   ├── Report.java               # legacy entity
│   └── User.java                 # user with coins and report count
├── repository/
│   ├── IssueRepository.java      # includes Haversine native query
│   ├── ReportRepository.java
│   └── UserRepository.java
├── dto/
│   ├── IssueResponse.java
│   ├── AuthRequest/Response.java
│   ├── SignupRequest.java
│   └── ReportResponse/StatsResponse.java
└── security/
    ├── SecurityConfig.java       # filter chain, CORS, session policy
    └── JwtAuthFilter.java        # per-request JWT validation
```

## Issue Status Flow

```
REPORTED → VERIFIED (3+ upvotes) → ASSIGNED → IN_PROGRESS → RESOLVED
                                                           → REJECTED (fraud detected)
```

## Running Locally

**Prerequisites:** Java 17+, MySQL 8.0, Maven

```bash
git clone https://github.com/awaneetdecoder/civicnexus-backend
cd civicnexus-backend/swachh-drishti
```

Copy the example properties file and fill in your values:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/civicnexus?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
jwt.secret=any_random_string_minimum_64_characters_long
gemini.api.key=YOUR_GEMINI_API_KEY
```

```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`.

## API Reference

**Authentication**

```
POST /api/auth/signup
Body: { "name": "string", "email": "string", "password": "string" }
Returns: { "token": "jwt", "name", "email", "coins", "totalReports" }

POST /api/auth/login
Body: { "email": "string", "password": "string" }
Returns: { "token": "jwt", ... }

GET /api/auth/me
Header: Authorization: Bearer <token>
Returns: user profile
```

**Issues**

```
POST /api/issues (multipart/form-data)
Header: Authorization: Bearer <token>
Fields: image (file), latitude (double), longitude (double), address (string)
Returns: IssueResponse with full Gemini analysis

GET /api/issues/all
Public — no auth required
Returns: list of all issues for map display

POST /api/issues/{id}/upvote
Header: Authorization: Bearer <token>
Returns: updated issue (auto-verifies at 3 upvotes)

POST /api/issues/{id}/resolve (multipart/form-data)
Header: Authorization: Bearer <token>
Fields: image (file), resolverLatitude, resolverLongitude
Runs: GPS check → timestamp check → Gemini comparison
Returns: updated issue with resolution confidence score
```

**Environment variables (production)**

```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWT_SECRET
JWT_EXPIRATIONMS
GEMINI_API_KEY
APP_BASE_URL
```

## What's Built

- [x] JWT authentication — signup, login, BCrypt password hashing, stateless filter chain
- [x] Gemini 2.5 Flash multimodal integration for issue analysis
- [x] Full issue lifecycle — submit, list, upvote, resolve
- [x] GPS proximity + timestamp validation as pre-AI fraud filters
- [x] Gemini before/after resolution comparison with a confidence threshold
- [x] Displacement anomaly detection via time-windowed Haversine query
- [x] Community auto-verification at 3+ upvotes
- [x] Gamified coin rewards tied to AI-assessed severity

## Roadmap

- [ ] Role-based access control — `/resolve` is currently callable by any authenticated user; it needs a distinct municipal-worker role rather than relying on UI convention
- [ ] Containerized deployment to Google Cloud Run (Dockerfile exists; production deployment is in progress — see [Architecture note](#civicnexus--backend))
- [ ] Migrate Gemini key usage so the Flutter client never calls Gemini directly (see [Known Limitations](#known-limitations))
- [ ] Automated test suite — `FraudDetectionService`'s Haversine and timestamp logic is the highest priority, since it's the security-critical path
- [ ] CI pipeline (build + test on push)
- [ ] Spatial indexing or a dedicated geospatial extension if issue volume grows beyond what a full-table Haversine scan can serve efficiently

## Known Limitations

**No role separation between citizens and municipal workers.** The `/api/issues/{id}/resolve` endpoint trusts any authenticated user. In production this needs a `role` field on `User` and an authorization check, not just a UI that happens to only show the resolve flow to workers.

**Gemini API key currently also lives in the Flutter client.** The mobile app calls Gemini directly for a fast pre-submission preview (see the companion repo), which means the key ships inside the compiled app. The backend's own key, used for the authoritative analysis and fraud-detection comparison, is server-side only and read from an environment variable — that one is fine. The client-side key is the one that needs to move behind a backend proxy endpoint before this goes further than a hackathon demo.

**Haversine runs as a full-table scan.** Fine at hackathon scale; would need a spatial index (or PostGIS-equivalent) before it works well at city scale.

## Engineering Decisions

**Why three fraud-detection layers instead of trusting the AI comparison alone?** AI confidence is probabilistic and can be wrong or gamed with a convincing fake. GPS proximity and timestamp validation are deterministic, cheap, and essentially impossible to satisfy without physical presence at the right place and time. Running them first means the expensive, uncertain Gemini call only happens once the cheap checks already agree something legitimate is being submitted.

**Why native SQL Haversine instead of a spatial extension?** Enabling MySQL spatial functions or migrating to PostGIS would have added infrastructure complexity with a hard deadline in front of it. Plain Haversine in a native query gets correct-enough results on a small dataset with zero extra setup, and the Roadmap item above is the explicit, intentional upgrade path once scale demands it.

**Why does Flutter also call Gemini directly, not just the backend?** It's a deliberate UX tradeoff: the client-side call gives the user an AI read on their photo in 3–5 seconds, before they've even hit submit, with no network round trip to the backend in between. The backend's own Gemini call on `POST /api/issues` is the system of record — if the two ever disagree, the server's analysis wins. The cost of that tradeoff is the exposed key noted above.

**Why a 60% confidence threshold instead of binary accept/reject on resolution?** A hard binary either auto-closes issues that aren't really fixed, or rejects legitimate fixes the model is just unsure about. Routing anything under 60% to `IN_PROGRESS` for human review, instead of `RESOLVED` or `REJECTED`, avoids both failure modes at the cost of some manual follow-up.

## What This Project Demonstrates

- End-to-end multimodal AI integration — structured prompting, JSON parsing, and using a model's output as an input to further business logic, not just displaying it
- Designing a layered fraud-detection system that combines deterministic constraints with probabilistic AI verification, in the right order
- Geospatial backend work — Haversine distance in native SQL, time-windowed anomaly correlation
- Stateless authentication architecture with Spring Security's filter chain
- The ability to name a project's own security and scaling gaps precisely, rather than presenting it as finished

## Author

**Awaneet Mishra**
[@awaneetdecoder](https://github.com/awaneetdecoder) · awaneet03991@gmail.com

## License

MIT — free to use for educational purposes.
