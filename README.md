# FD Shield — Setup Guide

## Prerequisites
- Docker Desktop running
- Git (optional)

---

## Step 1 — Run the database schema on Neon

Open your Neon console → SQL Editor, paste and run `schema.sql`.

Or run from terminal:
```bash
psql 'postgresql://neondb_owner:npg_cEadmPN9OT4W@ep-blue-lab-aom0sc9s-pooler.c-2.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require' -f schema.sql
```

---

## Step 2 — Build and start all services

```bash
cd fd-shield
docker compose up --build
```

This starts:
| Service         | Port |
|----------------|------|
| Spring Boot     | 8080 |
| Go Risk Engine  | 8081 |
| FastAPI         | 8082 |

---

## Step 3 — Verify all services are healthy

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health
```

All three should return OK responses.

---

## Step 4 — Open the frontend

Open `frontend/index.html` in your browser directly (no server needed).

---

## Quick API test via curl

### Create a user
```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Arjun Kumar",
    "email": "arjun@example.com",
    "monthlyIncome": 80000,
    "monthlyExpenses": 40000
  }'
```

### Create an FD
```bash
curl -X POST http://localhost:8080/fds \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "principal": 200000,
    "interestRate": 7.5,
    "durationMonths": 24,
    "startDate": "2025-01-01"
  }'
```

### View portfolio (triggers Go + FastAPI)
```bash
curl http://localhost:8080/users/1/portfolio
```

### Simulate withdrawal
```bash
curl -X POST http://localhost:8080/withdraw \
  -H "Content-Type: application/json" \
  -d '{"fdId": 1}'
```

---

## Architecture

```
HTML Frontend
     |
     v
Spring Boot :8080  (source of truth, owns DB)
     |         |
     v         v
Go :8081    FastAPI :8082
(risk)      (analytics)
     |         |
     v         v
  Spring Boot collects results
  and stores snapshot in Neon PostgreSQL
```

---

## Folder structure

```
fd-shield/
├── docker-compose.yml
├── schema.sql
├── README.md
├── spring-service/        ← Java Spring Boot
├── go-risk-engine/        ← Go + Gin
├── fastapi-analytics/     ← Python FastAPI
└── frontend/              ← Plain HTML
```
