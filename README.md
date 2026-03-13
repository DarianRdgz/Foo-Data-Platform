# Foo Data Platform (FDP)

A production-patterned data ingestion and geographic intelligence platform built in Java 21 and Spring Boot 4. It aggregates seven public US datasets, housing, economic indicators, grocery prices, crime, education, and natural disaster risk, normalizes them into a unified geographic model spanning national down to ZIP code level, and serves them through a REST API and React admin dashboard.

Built as a self-directed engineering project to demonstrate backend architecture, data engineering, and API integration at a realistic production scale.

---

## Why This Project Exists

Most portfolio projects demonstrate that someone can build a CRUD app. This one was designed to demonstrate something harder: architecting a system where multiple independent data pipelines, each with different shapes, authentication schemes, delivery formats, and failure modes, all feed into a single coherent data model without coupling to each other.

The specific design challenges solved here include distributed locking, idempotent ingestion, schema-free flexible payloads alongside a strict geographic hierarchy, and enforced source isolation via architectural tests.

---

## Technology Stack

**Backend**
- Java 21 (records, sealed interfaces, pattern matching, text blocks throughout)
- Spring Boot 4.0 — one of the first projects running the newly released version
- PostgreSQL 16 with 15 versioned Flyway migrations
- Spring Data JPA for entity management + raw `JdbcTemplate` / `NamedParameterJdbcTemplate` for performance-sensitive batch upserts
- Spring `RestClient` for all outbound HTTP (replaces `RestTemplate` in Spring 6+)
- Resilience4j for API rate limiting (FRED adapter)
- Logstash Logback encoder for structured JSON logs with MDC error categorization

**Frontend**
- React 19 + Vite 7
- Tailwind CSS 4
- React Router 7 + Axios

**Testing**
- JUnit 5 + Spring Boot Test
- Testcontainers (real PostgreSQL in every integration test — no in-memory database shortcuts)
- WireMock for HTTP client tests against stubbed external APIs
- ArchUnit for enforcing architectural constraints at the package level
- MockMvc for controller layer tests

**Infrastructure**
- Docker + Docker Compose (single-command local startup)
- Separate `test` service in Compose for running the full test suite in a containerized environment

---

## Architecture

### Geographic Data Model

The central design decision is a 6-level US geographic hierarchy stored in a single `fdp_geo.geo_areas` table:

```
National -> State -> Metro (CBSA) -> County -> City -> ZIP
```

This hierarchy mirrors how federal datasets are actually published. Every piece of ingested data, housing values, crime rates, economic indicators, disaster risk, resolves to a `geo_id` UUID in this table rather than embedding raw geographic identifiers in each source's schema.

### Unified Snapshot Model

All time-series data lands in a single `fdp_geo.area_snapshot` table:

| Column | Type | Purpose |
|---|---|---|
| `geo_id` | UUID FK | The geographic unit |
| `category` | TEXT | Dot-namespaced metric: `housing.home_value`, `economic.unemployment_rate`, `risk.composite` |
| `snapshot_period` | DATE | First day of the observation period |
| `source` | TEXT | The adapter that wrote this row |
| `is_rollup` | BOOLEAN | Computed aggregate vs. directly ingested |
| `payload` | JSONB | All metric fields — flexible schema per category |

The unique constraint `(geo_id, category, snapshot_period, source)` makes every write idempotent. Running any adapter twice produces the same database state.

### Ingestion Pipeline

```
External Source (API / CSV / gzip)
    → HTTP Client       (auth, retry, rate limiting)
    → Source Adapter    (parse, resolve geo IDs, build upsert batch)
    → IngestionService  (acquire distributed lock, open run record)
    → batchUpsert()     (NamedParameterJdbcTemplate batch write)
    → finishSuccess()   (record row count, duration, release lock)
    → REST API / Admin Dashboard
```

Each source lives in its own package under `sources/` and has no compile-time dependency on any other source package, enforced by ArchUnit tests that fail the build if a cross-source import is introduced.

### Distributed Locking

Rather than advisory locks (which are connection-scoped and unsafe with a connection pool), ingestion locks are backed by a dedicated `fdp_core.ingestion_lock` table using a `WHERE expires_at < now()` conditional upsert. This means:
- Multiple concurrent scheduler triggers for the same source silently skip rather than pile up
- Stale locks from a crashed instance expire automatically via the TTL column
- Lock ownership is checked on release, preventing one service instance from releasing another's lock

---

## Data Sources

| Source | Format | Geo Levels | Schedule |
|---|---|---|---|
| [Kroger API](https://developer.kroger.com/) | REST/JSON | City, ZIP | Weekly |
| [Zillow Research](https://www.zillow.com/research/data/) | Monthly CSV | All 6 levels | Monthly |
| [FBI Crime Data Explorer](https://cde.fbi.gov/) | CSV | National, State | Annual |
| [FRED (St. Louis Fed)](https://fred.stlouisfed.org/) | REST/JSON | National, State, Metro, County | Monthly |
| [College Scorecard](https://collegescorecard.ed.gov/data/documentation/) | Paginated REST/JSON | State, City | Annual |
| [FEMA OpenFEMA](https://www.fema.gov/about/openfema/data-sets) | Bulk CSV | State, County | Annual |
| [NOAA Storm Events](https://www.ncdc.noaa.gov/stormevents/) | Gzipped CSV (directory listing + download) | State, County | Annual |

Each adapter handles the realities of its source: FRED uses a YAML-configured series catalog and a Resilience4j rate limiter; NOAA requires discovering the current filename from an HTML directory index before downloading a gzipped CSV; College Scorecard uses paginated JSON where metric keys contain literal dots.

A composite `risk.composite` score is computed from FEMA and NOAA data using a deterministic weighted formula, written only when both sources are present for a given geo.

---

## Key Engineering Decisions

**ArchUnit for architectural governance.** Three test classes (`Epic2ArchitectureTest`, `Sprint3ArchitectureTest`, `Sprint5ArchitectureTest`) assert structural constraints, source packages don't import each other, adapters don't reach into unrelated domains, the `DisasterRiskScoreService` doesn't depend on source adapters directly. These run on every build.

**No fake databases in integration tests.** Every integration test spins up a real PostgreSQL container via Testcontainers and runs the full Flyway migration chain. This catches SQL, constraint, and index issues that H2 would silently pass.

**WireMock for all HTTP clients.** Each adapter's integration test stubs the real upstream API endpoint, including realistic edge cases — paginated responses, malformed rows, gzipped files, dot-notation JSON keys, `.`-valued FRED observations.

**JSONB payloads with typed categories.** Each metric category defines its own payload shape, stored as JSONB. This means adding a new metric never requires a schema migration, but the category namespace (`housing.home_value`, `risk.disaster.fema`) provides enough structure for querying. The `row_number() OVER (PARTITION BY geo_id, category ORDER BY snapshot_period DESC)` pattern serves the "latest value" read path efficiently without a separate materialized view.

**Structured logging with error classification.** Every exception handler tags logs with an `error_category` MDC field (`VALIDATION_ERROR`, `API_ERROR`, `DB_ERROR`, `LOCK_ERROR`, `UNCLASSIFIED`) using an `AutoCloseable` wrapper for try-with-resources usage. This makes log aggregation and alerting targeting straightforward.

---

## Admin Dashboard

A React admin UI at `/admin` provides:

- **Overview** - last ingestion run status, timing, and row counts per source
- **Jobs** - enable/disable scheduled jobs, trigger runs manually, live status polling every 5 seconds
- **Ingestion history** - paginated run log with duration, records written, and error detail
- **Database browser** - live table metadata, column types, index definitions, and row samples
- **FRED series registry** - view the active series catalog with geo mappings

All admin routes require an API key set via `FDP_ADMIN_API_KEY`.

---

## Project Structure

```
Foo-Data-Platform/
├── backend/
│   └── src/
│       ├── main/java/com/fooholdings/fdp/
│       │   ├── api/            # Public REST controllers, query services, DTOs
│       │   ├── admin/          # Admin controllers (jobs, ingestion, db, FRED registry)
│       │   ├── core/           # Ingestion engine: locking, run lifecycle, quota, logging
│       │   ├── geo/            # Geo hierarchy schema, repositories, seeding, support utils
│       │   ├── sources/        # One package per data source, no cross-source imports
│       │   │   ├── kroger/     # OAuth2 client, location + product ingestion
│       │   │   ├── zillow/     # CSV fetcher, 4 adapters (ZHVI, ZORI, Listings, Affordability)
│       │   │   ├── cde/        # FBI crime CSV client and adapter
│       │   │   ├── fred/       # FRED REST client, rate limiter, series catalog
│       │   │   ├── collegescorecard/  # Paginated JSON client, state + city aggregation
│       │   │   ├── fema/       # OpenFEMA CSV client, 10-year rolling window
│       │   │   └── noaa/       # Gzip CSV client with directory discovery
│       │   └── grocery/        # Grocery domain models (products, prices, store locations)
│       ├── main/resources/
│       │   ├── db/migration/   # 15 Flyway migrations (V1–V15)
│       │   └── fred-series-catalog.yml
│       └── test/               # 17 test classes: unit, integration, architecture
├── frontend/                   # React 19 + Vite 7 admin UI
└── docker-compose.yml
```

---

## Running Locally

**Prerequisites:** Docker and Docker Compose

```bash
git clone <repo-url>
cd Foo-Data-Platform
cp .env.example .env
# Add Kroger credentials and desired Postgres password to .env
docker compose up
```

Backend runs at `http://localhost:8080`. Flyway migrations apply automatically on startup.

```bash
# Run the full test suite (Testcontainers + WireMock integration tests included)
docker compose run --rm test
```

---

## Author

**Darian Rodriguez** - Software Engineer
