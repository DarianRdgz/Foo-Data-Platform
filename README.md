# Foo Data Platform (FDP)

A grocery price intelligence backend that ingests multi-source retail pricing data, stores immutable time series price snapshots, and supports state and national analytics.

## Architecture

Designed as a modular monolith with microservice extraction in mind each data source is fully isolated in its own package and can be extracted into a standalone Spring Boot service with minimal refactoring.

```
fdp/
├── core/               # Shared platform infrastructure (locks, runs, quota, raw payloads)
├── grocery/            # Domain model (locations, products, prices)
└── sources/
    └── kroger/         # Kroger API adapter (auth, client, ingestion, web)
```

**Key design decisions:**
- Append-only `price_observation` table with `ON CONFLICT DO NOTHING` batch inserts for idempotent ingestion
- Table-based distributed lock (`fdp_core.ingestion_lock`) prevents concurrent ingestion runs per source
- Every API response, success or failure, is archived to `fdp_core.raw_payload` before any exception is raised

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0 |
| Persistence | PostgreSQL 16, Spring Data JPA, JdbcTemplate |
| Schema migrations | Flyway |
| HTTP client | Spring RestClient |
| Serialization | Jackson 3 |
| Containerization | Docker / Docker Compose |

## Prerequisites

- Docker and Docker Compose
- A `.env` file in the project root (see below)

## Environment Setup

Create a `.env` file in the project root:

```env
POSTGRES_DB=fdp
POSTGRES_USER=your_user
POSTGRES_PASSWORD=your_password

KROGER_CLIENT_ID=your_kroger_client_id
KROGER_CLIENT_SECRET=your_kroger_client_secret
KROGER_API_BASE_URL=https://api-ce.kroger.com/v1
```

## Running the Application

```bash
# First run or after schema changes — tears down volumes and rebuilds cleanly
docker compose down -v
docker compose up -d --build
```

Flyway migrations run automatically at startup. The backend is available at `http://localhost:8080`.

```bash
# View backend logs
docker compose logs -f backend
```

## API Endpoints

### Token Management
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/kroger/token/status` | Check current OAuth token state |
| `POST` | `/kroger/token/refresh` | Force token refresh |

### Store Locations
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/kroger/locations` | All ingested Kroger locations |
| `GET` | `/kroger/locations/{locationId}` | Single location by Kroger location ID |

### Ingestion Triggers
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/kroger/ingestion/locations` | Trigger location ingestion by zip code(s) |
| `POST` | `/kroger/ingestion/products` | Trigger product + price ingestion |

**Example — trigger location ingestion:**
```bash
curl -X POST http://localhost:8080/kroger/ingestion/locations \
  -H "Content-Type: application/json" \
  -d '{"zipCodes": ["77001", "77002"]}'
```

**Example — trigger product ingestion:**
```bash
curl -X POST http://localhost:8080/kroger/ingestion/products \
  -H "Content-Type: application/json" \
  -d '{"locationIds": ["70100277"], "searchTerms": ["milk", "bread", "eggs"]}'
```

## Database Schema

Managed exclusively by Flyway. Never modify tables manually.

| Schema | Purpose |
|---|---|
| `fdp_core` | Source systems, ingestion runs, locks, quota tracking, raw payloads |
| `fdp_grocery` | Store locations, products, price observations |
| `fdp_public_safety` | Reserved for future non-grocery datasets |

Migrations live in `backend/src/main/resources/db/migration/`.

## Running Tests

```bash
cd backend
mvn test
```

Tests are unit and web-layer slice tests only — no database or external services required.

## Adding a New Data Source

1. Create `sources/<sourcename>/` following the Kroger package structure
2. Add a seed row to `fdp_core.source_system` via a new Flyway migration
3. Register the new `@ConfigurationProperties` class in `FdpApplication.java`
4. Shared infrastructure (`IngestionRunService`, `IngestionLockService`, `RawPayloadService`) is reused as-is