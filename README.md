# Foo Data Platform (FDP)

A full-stack data ingestion and query platform that aggregates public US geographic datasets: housing, grocery prices, cost of living, and more. Exposes them through a REST API and admin dashboard.

Built as a personal learning project to explore backend architecture, data engineering patterns, and API integration at a realistic scale.

---

## What It Does

FDP pulls data from external sources on a schedule, normalizes it into a consistent internal format, stores it in PostgreSQL, and serves it through a queryable REST API. A React admin dashboard lets you monitor ingestion jobs, inspect the database, and manage system state.

The platform is designed around a 5-level US geographic hierarchy **National → State → Metro (CBSA) → County → City/ZIP** matching how public datasets are actually published and how users naturally think about geography.

**Confirmed data sources:**
- [Kroger API](https://developer.kroger.com/) real-time grocery store locations and product prices
- [Zillow Research Data](https://www.zillow.com/research/data/) housing values (ZHVI), rent (ZORI), affordability, and listings

**Planned sources:** FRED (economic indicators), College Scorecard, FEMA/NOAA (disaster risk), FBI crime data

---

## Tech Stack

**Backend**
- Java 21 / Spring Boot 4
- PostgreSQL 16 with Flyway migrations
- Spring Data JPA + JDBC
- Maven

**Frontend**
- React 19 + Vite
- Tailwind CSS
- React Router, Axios

**Infrastructure**
- Docker + Docker Compose (single-command local setup)

---

## Architecture

```
External APIs (Kroger, Zillow, ...)
    → API Clients         (HTTP, auth, error handling)
    → Source Adapters     (normalize to canonical models)
    → Canonical Models    (source-agnostic DTOs)
    → PostgreSQL          (persistent storage via JPA/JDBC)
    → Query Services      (filtered, paginated reads)
    → REST API / Frontend (consumers)
```

The adapter pattern means new data sources can be added without touching ingestion infrastructure — just implement the `GrocerySourceAdapter` interface and register it.

---

## Key Features

**Ingestion pipeline**
- Scheduled ingestion runs with distributed locking to prevent duplicate jobs
- Idempotent writes, repeated runs don't create duplicate records
- Raw payload archiving, every API response is stored for replay and debugging
- API quota tracking to avoid rate limit violations

**Admin dashboard** (`/admin`)
- View ingestion run history with status, duration, and row counts
- Browse database tables with column metadata and sample data
- Trigger and monitor background jobs manually
- API key-protected routes

**Public REST API**
- Products, prices, price history, and trends by location
- Geographic hierarchy endpoints (states, metros, counties, ZIPs)
- Cost-of-living basket comparisons across areas

---

## Getting Started

**Prerequisites:** Docker and Docker Compose

1. Clone the repo and copy the example env file:
   ```bash
   git clone <repo-url>
   cd Foo-Data-Platform
   cp .env.example .env
   ```

2. Add your API credentials to `.env` (Kroger client ID/secret at minimum):
   ```
   KROGER_CLIENT_ID=your_id
   KROGER_CLIENT_SECRET=your_secret
   POSTGRES_DB=fdp
   POSTGRES_USER=fdp
   POSTGRES_PASSWORD=yourpassword
   ```

3. Start the stack:
   ```bash
   docker compose up
   ```

The backend will run at `http://localhost:8080` and apply database migrations automatically on startup.

**To run tests:**
```bash
docker compose run --rm test
```

---

## Project Structure

```
Foo-Data-Platform/
├── backend/                    # Spring Boot application
│   └── src/main/java/com/fooholdings/fdp/
│       ├── api/                # Public REST controllers + query services
│       ├── admin/              # Admin-only controllers (ingestion, db, jobs)
│       ├── core/               # Ingestion engine (locking, scheduling, logging)
│       ├── sources/            # Source adapters (Kroger, Zillow)
│       │   ├── kroger/         # Kroger API client, auth, ingestion
│       │   └── zillow/         # Zillow CSV fetcher and ingestion
│       ├── geo/                # Geographic hierarchy and seeding
│       └── grocery/            # Grocery domain models (products, prices, locations)
├── frontend/                   # React + Vite admin UI
│   └── src/
│       ├── admin/              # Admin pages and layout
│       └── api/                # Axios HTTP client
├── docker/seed/                # US geography seed data (ZIPs, counties, metros)
└── docker-compose.yml
```

---

## What I Learned Building This

- How to design an adapter pattern that makes adding new data sources straightforward
- Managing scheduled jobs safely with distributed locking
- Using Flyway to version and migrate a real PostgreSQL schema
- Structuring a Spring Boot app into meaningful layers (controllers, services, repositories)
- Why raw geographic data (ZIP codes specifically) is messier than it looks, and how to build a proper hierarchy to handle it
- Docker Compose for local full-stack development

---

## Author

**Darian Rodriguez** Software Engineer  
