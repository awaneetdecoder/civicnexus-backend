# SwachhDrishti — Spring Boot Backend

Spring Boot REST API for **SwachhDrishti** — a citizen garbage-reporting app. Handles JWT auth, report storage, and the AI severity/reward logic built on top of Google Cloud Vision API.

**Flutter app repo:** [swachh-drishti](https://github.com/awaneetdecoder/swachh-drishti)

---

## What this does

Citizens upload a photo + GPS location of garbage. This API sends the photo to Google Cloud Vision API for label detection, then runs its own classification logic on those labels to determine garbage type and severity, stores the report, and awards coins — but only if the photo is confirmed to contain garbage.

Google Cloud Vision API only returns raw labels and confidence scores (e.g. "waste", "plastic", "debris") — it does not classify garbage type or severity natively. That logic is custom, in `VisionService`.

---

## How severity scoring works

1. Image saved to `uploads/` with a UUID filename
2. Sent to Vision API Label Detection → returns labels + confidence
3. Labels mapped to garbage type:
   - plastic / bottle / bag → Plastic
   - organic / food → Organic
   - electronic / circuit → E-Waste
   - else → Mixed
4. Labels mapped to severity (1–5):
   - litter → 1, debris → 2, garbage/pollution → 3, dump → 4, landfill → 5
   - adjusted by label count and confidence
5. Coins awarded by severity: 5 / 10 / 20 / 35 / 50 for levels 1–5
6. **Reward gate:** coins are only awarded if `isGarbage` is true — meaning a garbage-related label was actually detected. This is what blocks fake or empty submissions from earning rewards.

---

## Tech stack

| Technology | Purpose |
|---|---|
| Java 17, Spring Boot 3.5 | Core framework |
| Spring Security + JJWT | JWT authentication |
| Spring Data JPA / Hibernate | ORM |
| MySQL 8 | Database |
| Google Cloud Vision API | Label detection (severity logic is custom, built on top) |
| BCrypt | Password hashing |
| Local filesystem | Image storage (`uploads/` folder) |

---

## Architecture

Strict 4-layer structure: **Controller → Service → Repository → Entity**

- Controllers only read input, call one service method, and return — no logic
- All business logic lives in the Service layer
- Controllers never return Entity objects directly — always DTOs
- `@Transactional` on service methods with multiple DB writes

```
src/main/java/com/swachhdrishti/swachh_drishti/
├── SwachhDrishtiApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── CorsConfig.java
│   └── JwtAuthFilter.java
├── controller/
│   ├── AuthController.java
│   ├── ReportController.java
│   └── UserController.java
├── service/
│   ├── AuthService.java
│   ├── ReportService.java
│   ├── VisionService.java       # Vision API call + severity/type logic
│   └── UserDetailsServiceImpl.java
├── repository/
│   ├── UserRepository.java
│   └── ReportRepository.java
├── entity/
│   ├── User.java
│   └── Report.java
├── dto/
│   ├── AuthRequest.java
│   ├── SignupRequest.java
│   ├── AuthResponse.java
│   ├── ReportResponse.java
│   └── StatsResponse.java
└── util/
    └── JwtUtil.java
```

---

## API Endpoints

### Public

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/signup` | Register |
| POST | `/api/auth/login` | Login → JWT |

### Protected (JWT required)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/auth/me` | Current user details |
| POST | `/api/reports` | Submit report (multipart: image + fields) |
| GET | `/api/reports/myreports` | User's report history |
| GET | `/api/reports/hotspots` | GPS-clustered report density (backend query only — no Flutter screen yet) |
| GET | `/api/reports/stats` | User stats |
| GET | `/api/users/leaderboard` | Leaderboard (endpoint exists, no real ranking logic finalized yet) |

8 endpoints total. Single role — no admin or worker endpoints.

---

## Database schema

**users**
```
id, name, email (unique), password (BCrypt),
coins (default 0), total_reports (default 0),
resolved_reports (default 0), created_at, updated_at
```
No role column — single-role app, every user is a citizen.

**reports**
```
id, user_id (FK), address, description, image_url,
latitude, longitude, severity_score (1-5), garbage_type,
ai_confidence, ai_labels, status (default 'Pending'),
coins_awarded, created_at, updated_at
```

---

## Hotspot query

```sql
SELECT ROUND(latitude, 3) AS lat, ROUND(longitude, 3) AS lng, COUNT(*) AS report_count
FROM reports
GROUP BY ROUND(latitude, 3), ROUND(longitude, 3)
HAVING COUNT(*) >= 2
ORDER BY report_count DESC
```

This surfaces zones with repeated reports — it reflects historical report density, not a prediction of future garbage accumulation. There's no Flutter screen rendering this yet (see Roadmap).

---

## Getting started

### Prerequisites
- Java 17, Maven
- MySQL 8.0
- A Google Cloud Vision API key

### Setup

```sh
git clone https://github.com/awaneetdecoder/swachh-drishti-backend.git
cd swachh-drishti-backend
```

Create the database:
```sql
CREATE DATABASE swachhdrishti;
```

Configure `application.properties` (copy from `.example` if present) with your MySQL credentials, JWT secret, and Google Cloud Vision API key — never commit real credentials.

Run:
```sh
./mvnw spring-boot:run
```

---

## Honest current limitations

- Solo personal project — not yet used or tested by anyone outside development.
- Hotspot endpoint works, but no Flutter screen displays it yet.
- Leaderboard endpoint exists but ranking logic isn't finalized.
- No automated test suite yet.
- Not deployed — runs locally only.

---

## Roadmap

- [x] JWT auth, BCrypt
- [x] Report submission + Vision API integration
- [x] Custom severity/type mapping + reward gate
- [ ] Finalize leaderboard ranking logic
- [ ] Hotspot map screen in Flutter app
- [ ] Get a small group of real users to test it
- [ ] Automated test suite

---

## Author

**Awaneet Mishra** — [@awaneetdecoder](https://github.com/awaneetdecoder)

---

## License

MIT — free to use for educational purposes.
