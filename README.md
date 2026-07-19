# ⚡ DBPerfAI — AI Database Performance Engineer

An AI-powered assistant that acts like a senior Database Performance Engineer: it connects to
PostgreSQL, collects performance metrics, analyzes queries and execution plans, explains problems
in plain English, and generates actionable optimization reports.

## Architecture

```
┌────────────┐     ┌──────────────────┐     ┌─────────────┐
│  React SPA │────▶│  Spring Boot API │────▶│  PostgreSQL │  (app database, Cloud SQL)
│  MUI + TS  │     │  Java 21         │     └─────────────┘
│ (Cloud Run)│     │  (Cloud Run)     │────▶ Target PostgreSQL DBs (read-only, monitored)
└────────────┘     │                  │────▶ Claude API (AI analysis)
                   └──────────────────┘────▶ Google Secret Manager (DB credentials)
```

| Layer     | Tech                                                        |
|-----------|-------------------------------------------------------------|
| Frontend  | React 18, TypeScript, Material UI, Vite                     |
| Backend   | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring AI  |
| Database  | PostgreSQL 16, Flyway migrations                            |
| AI        | Claude API                                                  |
| Cloud     | Cloud Run, Cloud SQL, Secret Manager, Artifact Registry     |
| CI/CD     | GitHub Actions, Docker                                      |

## Module roadmap

- [x] **Module 1 — Authentication**: register / login / JWT, protected app shell
- [x] **Module 2 — Database Connections**: add & test PostgreSQL targets, Secret Manager storage, read-only enforcement, demo target DB
- [x] **Module 3 — Performance Collector**: pg_stat_statements / activity / locks / table-stats snapshots, background scheduler, history API
- [x] **Module 4 — Query Analyzer**: AI-powered analysis of SQL + EXPLAIN, live plan/schema grounding, optimized SQL, history
- [x] **Module 5 — Health Dashboard**: explainable health score, trend charts, slow queries, missing-index candidates, AI recommendations
- [x] **Module 6 — AI Copilot**: chat grounded in collected metrics — every turn is re-grounded in the latest snapshot + health score, with persisted sessions and suggested follow-ups
- [x] **Module 7 — Optimization Report**: downloadable PDF — health score, Claude-written executive summary, prioritized SQL-ready action plan, top queries, table access patterns, and snapshot trend
- [ ] **Module 8 — Settings**: allows users to manage their profile, database preferences, AI settings and privacy preferences

## Local development

### Prerequisites
- Java 21 (`brew install openjdk@21`)
- Node 20+
- Docker (for PostgreSQL)

### 1. Start PostgreSQL 
```bash
docker compose up -d postgres
```

### 2. Run the backend (port 8080)
```bash
cd backend
JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home mvn spring-boot:run
```
Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Run the frontend (port 5173)
```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173 — Vite proxies `/api` to the backend.

### Full stack via Docker Compose
```bash
docker compose up --build
```
Frontend: http://localhost:3000 · Backend: http://localhost:8080

### Run tests
```bash
cd backend && mvn test
```

## Environment variables

| Variable                 | Default (local dev)                          | Description                              |
|--------------------------|----------------------------------------------|------------------------------------------|
| `DB_URL`                 | `jdbc:postgresql://localhost:5433/dbperf`    | App database JDBC URL (compose publishes Postgres on 5433 to avoid clashing with a local install) |
| `DB_USERNAME`            | `dbperf`                                     | App database user                        |
| `DB_PASSWORD`            | `dbperf`                                     | App database password                    |
| `JWT_SECRET`             | dev-only value                               | HMAC key, **min 32 chars** — use Secret Manager in prod |
| `JWT_EXPIRATION_MINUTES` | `1440`                                       | Token lifetime                           |
| `CORS_ALLOWED_ORIGINS`   | `http://localhost:5173,http://localhost:3000`| Comma-separated allowed origins          |
| `PORT`                   | `8080`                                       | HTTP port (set by Cloud Run)             |

| `SECRETS_PROVIDER`       | `local`                                      | `local` (AES-GCM dev) or `gcp` (Secret Manager) |
| `LOCAL_ENCRYPTION_KEY`   | dev-only value                               | Passphrase for local provider, min 16 chars |
| `GCP_PROJECT_ID`         | —                                            | Required when `SECRETS_PROVIDER=gcp`     |
| `COLLECTOR_ENABLED`      | `true`                                       | Background metrics collection on/off     |
| `COLLECTOR_INTERVAL_MS`  | `300000`                                     | Delay between collection passes (5 min)  |

| `AI_PROVIDER`            | `auto`                                       | `auto` (prefer Claude, else Gemini), `anthropic`, `gemini`, or `ollama` (local) |
| `ANTHROPIC_API_KEY`      | —                                            | Claude API key                           |
| `GEMINI_API_KEY`         | —                                            | Google Gemini API key ([free tier](https://aistudio.google.com/apikey)) |
| `APP_AI_MODEL`           | `claude-opus-4-8`                            | Claude model                             |
| `APP_GEMINI_MODEL`       | `gemini-3.5-flash`                           | Gemini model                             |
| `OLLAMA_BASE_URL`        | `http://localhost:11434` (compose: `http://host.docker.internal:11434`) | Ollama server |
| `OLLAMA_MODEL`           | `qwen2.5:1.5b`                               | Local model tag (`ollama pull <tag>` first) |
| `APP_AI_MAX_TOKENS`      | `16000`                                      | Per-response output cap                  |

The AI layer is provider-pluggable behind one interface — three implementations:

| Provider | How to enable | Notes |
|----------|---------------|-------|
| **Claude** (recommended) | `export ANTHROPIC_API_KEY=sk-ant-...` | Official SDK, schema-guaranteed structured outputs, adaptive thinking |
| **Gemini** | `export GEMINI_API_KEY=AIza...` ([free tier](https://aistudio.google.com/apikey)) | REST API, JSON-mode + shape-guided prompts |
| **Ollama** (local, free, offline) | `AI_PROVIDER=ollama docker compose up -d backend` | Grammar-constrained JSON via Ollama's `format` schema; quality depends on the local model — small models (1.5b) give shallower analyses |

Then `docker compose up -d backend`. Without any provider, AI endpoints return 503 with
setup instructions; everything else keeps working.

## Demo target database

`docker compose up -d demo-target` starts **shopdemo** on port **5434** — an e-commerce
PostgreSQL (20k customers, 100k orders, 300k order items) with `pg_stat_statements` enabled
and **deliberately missing indexes** so the AI has real problems to find.

Connect it in the UI with database `shopdemo`, user `dbperf_monitor`, password `monitor123`
(a read-only `pg_monitor` user). The host/port depend on where the **backend** runs, because
the connection test executes there:

| Backend running via              | Host           | Port  |
|----------------------------------|----------------|-------|
| `mvn spring-boot:run` (host)     | `localhost`    | 5434  |
| `docker compose up` (container)  | `demo-target`  | 5432  |

(From a container, `localhost` is the container itself. `host.docker.internal:5434` also
works from the compose backend to reach services on the host.)

## Production deployment (Google Cloud Run)

One-time setup:
```bash
gcloud services enable run.googleapis.com sqladmin.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com

gcloud artifacts repositories create dbperf --repository-format=docker \
  --location=us-central1

gcloud sql instances create dbperf-sql --database-version=POSTGRES_16 \
  --tier=db-g1-small --region=us-central1
gcloud sql databases create dbperf --instance=dbperf-sql

echo -n "$(openssl rand -base64 48)" | \
  gcloud secrets create dbperf-jwt-secret --data-file=-
```

Build & deploy the backend:
```bash
cd backend
gcloud builds submit --tag us-central1-docker.pkg.dev/$PROJECT_ID/dbperf/backend

gcloud run deploy dbperf-backend \
  --image us-central1-docker.pkg.dev/$PROJECT_ID/dbperf/backend \
  --region us-central1 --allow-unauthenticated \
  --add-cloudsql-instances $PROJECT_ID:us-central1:dbperf-sql \
  --set-secrets JWT_SECRET=dbperf-jwt-secret:latest \
  --set-env-vars "DB_URL=jdbc:postgresql:///dbperf?cloudSqlInstance=$PROJECT_ID:us-central1:dbperf-sql&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DB_USERNAME=postgres"
```

Build & deploy the frontend:
```bash
cd frontend
gcloud builds submit --tag us-central1-docker.pkg.dev/$PROJECT_ID/dbperf/frontend

gcloud run deploy dbperf-frontend \
  --image us-central1-docker.pkg.dev/$PROJECT_ID/dbperf/frontend \
  --region us-central1 --allow-unauthenticated \
  --set-env-vars "BACKEND_URL=https://<backend-cloud-run-url>,PORT=8080"
```

A GitHub Actions workflow automating this pipeline ships with the final deployment module.

## API (Module 1)

| Method | Endpoint                | Auth   | Description                     |
|--------|-------------------------|--------|---------------------------------|
| POST   | `/api/v1/auth/register` | public | Create account, returns JWT     |
| POST   | `/api/v1/auth/login`    | public | Authenticate, returns JWT       |
| GET    | `/api/v1/auth/me`       | bearer | Current user profile            |

## API (Module 2)

| Method | Endpoint                        | Auth   | Description                                  |
|--------|---------------------------------|--------|----------------------------------------------|
| POST   | `/api/v1/connections`           | bearer | Add target (password → secret store)         |
| GET    | `/api/v1/connections`           | bearer | List own connections                         |
| GET    | `/api/v1/connections/{id}`      | bearer | Get one connection                           |
| POST   | `/api/v1/connections/{id}/test` | bearer | Test stored connection, refresh status       |
| POST   | `/api/v1/connections/test`      | bearer | Ad-hoc test before saving                    |
| DELETE | `/api/v1/connections/{id}`      | bearer | Delete connection + stored secret            |

## API (Module 3)

| Method | Endpoint                                        | Auth   | Description                             |
|--------|-------------------------------------------------|--------|-----------------------------------------|
| POST   | `/api/v1/connections/{id}/snapshots`            | bearer | Collect a performance snapshot now      |
| GET    | `/api/v1/connections/{id}/snapshots?limit=50`   | bearer | Snapshot history (summaries)            |
| GET    | `/api/v1/connections/{id}/snapshots/latest`     | bearer | Latest snapshot with drill-down detail  |

A background scheduler also snapshots every registered connection every 5 minutes.

## API (Module 5)

| Method | Endpoint                                              | Auth   | Description                                             |
|--------|-------------------------------------------------------|--------|---------------------------------------------------------|
| GET    | `/api/v1/connections/{id}/dashboard`                  | bearer | Health score + factors, latest snapshot, history series, rule-based recommendations |
| POST   | `/api/v1/connections/{id}/dashboard/ai-recommendations` | bearer | Claude-generated recommendations from the latest snapshot |

The health score is rule-based and fully explainable: 100 minus documented penalties
(cache hit ratio, blocked sessions, locks, deadlocks, idle-in-transaction, seq-scan
hotspots, slow queries, temp spill), each returned as a visible factor.

## API (Module 4)

| Method | Endpoint                        | Auth   | Description                                          |
|--------|---------------------------------|--------|------------------------------------------------------|
| POST   | `/api/v1/analyzer/analyze`      | bearer | AI analysis of SQL and/or EXPLAIN output. With a `connectionId`, the backend runs EXPLAIN on the target (read-only) and grounds Claude with real index definitions + table statistics |
| GET    | `/api/v1/analyzer/history`      | bearer | Past analyses (kept for the Module 7 report)         |
| GET    | `/api/v1/analyzer/{id}`         | bearer | Full stored analysis                                 |

Full interactive docs at `/swagger-ui.html`.
