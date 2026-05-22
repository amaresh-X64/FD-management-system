<div align="center">

# 🛡️ FD Management

### Intelligent Fixed Deposit Portfolio & Liquidity Management System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Go](https://img.shields.io/badge/Go-1.22-00ADD8?style=flat&logo=go&logoColor=white)](https://golang.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.111-009688?style=flat&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com)
[![Streamlit](https://img.shields.io/badge/Streamlit-1.35-FF4B4B?style=flat&logo=streamlit&logoColor=white)](https://streamlit.io)
[![PostgreSQL](https://img.shields.io/badge/Neon-PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white)](https://neon.tech)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)](https://docs.docker.com/compose)

_A polyglot microservices backend that goes beyond basic FD management — evaluating liquidity safety, emergency readiness, concentration risk, and laddering quality using industry-standard financial algorithms._

</div>

---

## 📋 Table of Contents

- [Problem Statement](#-problem-statement)
- [Architecture](#-architecture)
- [Services](#-services)
- [Scoring Algorithms](#-scoring-algorithms)
- [API Reference](#-api-reference)
- [Database Schema](#-database-schema)
- [Getting Started](#-getting-started)
- [Running Tests](#-running-tests)
- [Project Structure](#-project-structure)
- [Tech Stack](#-tech-stack)

---

## 🎯 Problem Statement

Traditional FD systems show you **principal**, **interest rate**, and **maturity value** — nothing more.

They don't answer:

- 🚨 Can I survive a 6-month financial emergency without breaking an FD?
- 📅 Are my FDs all maturing at the same time (reinvestment risk)?
- 🏦 Am I over-concentrated in one deposit (DICGC insurance gap)?
- 🪜 Is my FD ladder structured for steady cash flow?
- 👤 What kind of investor am I based on my portfolio behaviour?

**FD Shield** answers all of these using a real-time analytics pipeline across three specialised microservices.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│     Streamlit Dashboard :8501        │
│  (Portfolio · Create FD · Withdraw)  │
└──────────────────┬──────────────────┘
                   │ REST
                   ▼
┌─────────────────────────────────────┐
│    Spring Boot Core Banking :8080    │  ◄── Source of Truth
│   Controller · Service · JPA/ORM    │      Owns Neon PostgreSQL
└──────────┬───────────────┬──────────┘
           │ REST          │ REST
           ▼               ▼
┌──────────────┐   ┌──────────────────┐
│  Go Risk     │   │ FastAPI Analytics │
│  Engine:8081 │   │ Service    :8082  │
│              │   │                  │
│  5 goroutines│   │ Pandas · Pydantic │
│  concurrent  │   │ Euclidean Persona │
│  HHI · CV   │   │ Dynamic Weights   │
└──────────────┘   └──────────────────┘
           │               │
           └──────┬────────┘
                  ▼
        Results stored in
        Neon PostgreSQL
```

### Why This Service Split?

| Service               | Language | Reason                                              |
| --------------------- | -------- | --------------------------------------------------- |
| **Spring Boot**       | Java     | Enterprise ORM, transactional DB, orchestration     |
| **Go Risk Engine**    | Go       | 5 concurrent calculations via goroutines + channels |
| **FastAPI Analytics** | Python   | Pandas data analysis, recommendation logic          |
| **Streamlit**         | Python   | Rapid professional dashboard                        |

---

## 🔧 Services

### 1. Spring Boot — Core Banking Service

The orchestrator and single source of truth. Every user action flows through here.

**Key responsibilities:**

- User management and FD lifecycle (create, track, withdraw)
- Compound interest calculation: `A = P(1 + r)^t`
- Orchestrates Go and FastAPI calls after every FD change
- Persists analytics snapshots to PostgreSQL
- Owns all four database tables

**Critical design pattern — transaction boundary split:**

```java
// DB save is @Transactional — commits immediately
public FdResponse createFd(CreateFdRequest req) {
    FdResponse response = saveFd(req);       // ← transaction commits here
    triggerAnalyticsRefresh(req.userId);     // ← runs AFTER commit
    return response;
}
```

Analytics failures (Go/FastAPI down) never roll back the saved FD.

---

### 2. Go Risk Engine — Concurrent Financial Calculator

Stateless computation service. Receives a payload, runs 5 algorithms in parallel, returns scores.

**Goroutine fan-out pattern:**

```go
go func() { liquidityCh     <- calcLiquidityScore(req) }()
go func() { spreadCh        <- calcMaturitySpreadScore(req) }()
go func() { penaltyCh       <- calcPenaltyExposure(req) }()
go func() { concentrationCh <- calcConcentrationRisk(req) }()
go func() { ladderCh        <- calcLadderScore(req) }()
```

All 5 run simultaneously. Total time = slowest calculation, not the sum.

---

### 3. FastAPI Analytics Service — Portfolio Intelligence

Python-powered intelligence layer. Converts raw scores into human-readable insights.

**Three core functions:**

- `_assign_persona()` — Euclidean distance scoring matrix across 5 persona profiles
- `_generate_recommendation()` — Pandas-powered quantified recommendations with specific ₹ amounts
- `_calc_health_score()` — Dynamic weighted average (weights shift based on expense ratio)

**Auto-generated Swagger UI:** `http://localhost:8082/docs`

---

## 📊 Scoring Algorithms

All scores are on a **0–100 scale**.

### Liquidity Score

> _"Can you survive 6 months without breaking an FD?"_

```
Base  = min(short_term_principal / (monthly_expenses × 6) × 100, 100)
Bonus = min(savings_rate × 20, 10)    ← good savings rate = faster rebuild
Score = min(Base + Bonus, 100)
```

### Maturity Spread Score

> _"Are your FD maturities evenly distributed or all clustered?"_

Uses **Coefficient of Variation (CV)** of gaps between sorted maturity dates.
Low CV = evenly spaced = good ladder. High CV = clustered = reinvestment risk.

### Penalty Exposure

> _"How likely are you to break an FD, and how painful would it be?"_

```
Likelihood = expense_burden × 0.6 + liquidity_gap × 0.4
Severity   = weighted_avg_months_remaining / 60
Score      = (Likelihood × 0.5 + Severity × 0.5) × 100
```

### Concentration Risk (HHI-based)

> _"Are you too exposed to a single FD or single bank?"_

Uses the **Herfindahl-Hirschman Index** — the same formula economists use for market monopoly measurement.

```
HHI   = Σ(each FD's share of total principal)²
Score = (1 − HHI) × 100
```

DICGC insurance covers only ₹5 lakh per bank — FDs above this threshold are penalised.

### Ladder Score

> _"Is your FD ladder structured for steady annual cash flow?"_

Weighted across 3 dimensions:
| Dimension | Weight | Ideal |
|-----------|--------|-------|
| Coverage span | 40% | 3–5 year span |
| Gap regularity | 40% | Equal gaps (low CV) |
| Rung count | 20% | 3–5 FDs |

### Financial Personas

Assigned via Euclidean distance to 5 ideal profiles:

| Persona                  | Characteristics                                                    |
| ------------------------ | ------------------------------------------------------------------ |
| 🟢 Conservative Saver    | High liquidity, well-spread, low penalty risk                      |
| 🔴 Liquidity Risk User   | Low liquid reserves, high penalty exposure (penalty > 70 override) |
| 🔵 Long-Term Planner     | 80%+ long-term FDs with good ladder quality                        |
| 🟠 Aggressive Reinvestor | Heavy long-term locking, low diversification                       |
| 🟣 Balanced Investor     | Moderate scores across all dimensions                              |

---

## 📡 API Reference

### Spring Boot (:8080)

| Method | Endpoint                | Description                                   |
| ------ | ----------------------- | --------------------------------------------- |
| `GET`  | `/health`               | Service health check                          |
| `POST` | `/users`                | Create user                                   |
| `GET`  | `/users/{id}`           | Get user by ID                                |
| `POST` | `/fds`                  | Create FD → triggers full analytics pipeline  |
| `GET`  | `/users/{id}/portfolio` | Full portfolio with all scores                |
| `POST` | `/withdraw`             | Premature withdrawal with penalty calculation |

**POST /fds — Request**

```json
{
  "userId": 1,
  "principal": 200000,
  "interestRate": 7.5,
  "durationMonths": 24,
  "startDate": "2025-01-01"
}
```

**GET /users/{id}/portfolio — Response (abridged)**

```json
{
  "user": { "name": "Amaresh", "monthlyIncome": 80000 },
  "activeFds": [...],
  "totalPrincipal": 500000,
  "totalMaturityValue": 578000,
  "financialProfile": {
    "persona": "Conservative Saver",
    "liquidityScore": 82.5,
    "maturitySpreadScore": 74.0,
    "penaltyRisk": 18.3,
    "concentrationRisk": 68.0,
    "ladderScore": 91.0,
    "portfolioHealthScore": 79.4,
    "recommendation": "Your portfolio health score is 79/100..."
  }
}
```

### Go Risk Engine (:8081)

| Method | Endpoint        | Description                      |
| ------ | --------------- | -------------------------------- |
| `GET`  | `/health`       | Service health                   |
| `POST` | `/risk/analyze` | Returns 5 concurrent risk scores |

### FastAPI Analytics (:8082)

| Method | Endpoint              | Description                              |
| ------ | --------------------- | ---------------------------------------- |
| `GET`  | `/health`             | Service health                           |
| `GET`  | `/docs`               | Swagger UI (auto-generated)              |
| `POST` | `/analytics/generate` | Persona + health score + recommendations |

---

## 🗄️ Database Schema

```sql
users                        fixed_deposits
─────────────────────        ──────────────────────────────
id          BIGSERIAL PK     id              BIGSERIAL PK
name        VARCHAR          user_id         FK → users
email       VARCHAR UNIQUE   principal       NUMERIC(15,2)
monthly_income  NUMERIC      interest_rate   NUMERIC(5,2)
monthly_expenses NUMERIC     duration_months INT
created_at  TIMESTAMP        maturity_amount NUMERIC(15,2)
                             start_date      DATE
                             maturity_date   DATE
                             fd_type         SHORT_TERM | LONG_TERM
                             status          ACTIVE | MATURED | WITHDRAWN

withdrawal_logs              user_financial_profile
────────────────────         ──────────────────────────────
id          BIGSERIAL PK     user_id              PK FK → users
fd_id       FK → fds         persona              VARCHAR
withdrawal_type VARCHAR       liquidity_score      NUMERIC
penalty_amount  NUMERIC       maturity_spread_score NUMERIC
created_at  TIMESTAMP         penalty_risk         NUMERIC
                              concentration_risk   NUMERIC
                              ladder_score         NUMERIC
                              portfolio_health_score NUMERIC
                              recommendation       TEXT
                              updated_at           TIMESTAMP
```

---

## 🚀 Getting Started

### Prerequisites

- Docker Desktop
- Git

### 1. Clone the repository

```bash
git clone https://github.com/amaresh-X64/FD-management-system.git
cd FD-management-system
```

### 2. Set up environment variables

Create a `.env` file in the project root (see `.env.example`):

```env
DB_URL=jdbc:postgresql://<your-neon-host>/neondb?sslmode=require&channel_binding=require
DB_USERNAME=your_neon_username
DB_PASSWORD=your_neon_password
GO_RISK_URL=http://go-risk-engine:8081
FASTAPI_URL=http://fastapi-analytics:8082
```

> Get your Neon credentials from [neon.tech](https://neon.tech) → your project → Connection Details.

### 3. Run the database schema

In your Neon console → SQL Editor, paste and run `schema.sql`.

Or via terminal:

```bash
psql 'your-neon-connection-string' -f schema.sql
```

### 4. Start all services

```bash
docker compose up --build
```

First build takes 3–5 minutes (Maven + Go + pip downloads). Subsequent starts are instant due to Docker layer caching.

### 5. Verify all services are up

```bash
curl http://localhost:8080/health   # Spring Boot
curl http://localhost:8081/health   # Go Risk Engine
curl http://localhost:8082/health   # FastAPI Analytics
```

### 6. Open the dashboard

```
http://localhost:8501
```

---

## 🧪 Running Tests

### Go — 20 tests

```bash
cd go-risk-engine
go test ./services/... -v
```

### FastAPI — 21 tests

```bash
cd fastapi-analytics
pip install pytest
pytest -v
```

### Spring Boot — ~60 tests with coverage

```bash
cd spring-service
mvn clean test jacoco:report
# Open: target/site/jacoco/index.html
```

| Package      | Coverage |
| ------------ | -------- |
| `service`    | 84%      |
| `client`     | 77%      |
| `controller` | 48%      |
| **Total**    | **66%**  |

---

## 📁 Project Structure

```
fd-management/
├── .env                          ← credentials (gitignored)
├── .env.example                  ← template for contributors
├── .gitignore
├── .gitattributes
├── docker-compose.yml            ← orchestrates all 4 services
├── schema.sql                    ← Neon PostgreSQL schema + migration
│
├── spring-service/               ← Java 21 + Spring Boot 3.2
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/fdshield/
│       ├── controller/           FdShieldController + GlobalExceptionHandler
│       ├── service/              FdService + UserService
│       ├── client/               GoRiskClient + FastApiClient
│       ├── entity/               User, FixedDeposit, WithdrawalLog, UserFinancialProfile
│       ├── repository/           4 JPA repositories
│       ├── dto/                  8 request/response DTOs
│       └── config/               AppConfig (RestTemplate + CORS)
│
├── go-risk-engine/               ← Go 1.22 + Gin
│   ├── Dockerfile
│   ├── go.mod
│   ├── main.go
│   ├── handlers/                 risk_handler.go
│   ├── models/                   risk.go (RiskRequest, RiskResult)
│   └── services/                 risk_service.go (5 algorithms + goroutines)
│
├── fastapi-analytics/            ← Python 3.12 + FastAPI + Pandas
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── main.py
│   ├── routers/                  analytics_router.py
│   ├── models/                   schemas.py (Pydantic models)
│   ├── analytics/                portfolio_analytics.py (3 core functions)
│   └── tests/                    test_analytics.py (21 pytest tests)
│
├── streamlit-dashboard/          ← Streamlit + Plotly
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app.py                    4 pages + sidebar health monitor
│   └── .streamlit/config.toml   dark theme config
│
└── frontend/
    └── index.html                simple HTML fallback UI
```

---

## 🛠️ Tech Stack

| Layer            | Technology                                     | Version      |
| ---------------- | ---------------------------------------------- | ------------ |
| Core Banking API | Spring Boot                                    | 3.2.5        |
| ORM              | Spring Data JPA + Hibernate                    | 6.4          |
| Risk Engine      | Go + Gin                                       | 1.22 + 1.10  |
| Analytics API    | FastAPI + Uvicorn                              | 0.111 + 0.29 |
| Data Processing  | Pandas                                         | 2.2.2        |
| Dashboard        | Streamlit + Plotly                             | 1.35 + 5.22  |
| Database         | Neon PostgreSQL                                | —            |
| Containerisation | Docker + Compose                               | —            |
| Java utilities   | Lombok                                         | 1.18.32      |
| Validation       | Spring Validation + Pydantic                   | —            |
| Testing          | JUnit 5 + Mockito + MockMvc + pytest + go test | —            |
| Coverage         | JaCoCo                                         | 0.8.12       |

---

## 👤 Author

**Amaresh** — [@amaresh-X64](https://github.com/amaresh-X64)

---

<div align="center">
Built with Spring Boot · Go · FastAPI · Streamlit · Neon PostgreSQL · Docker
</div>
