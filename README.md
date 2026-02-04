# Foo-Data-Platform

A backend first data ingestion platform that authenticates with the Kroger API, pulls grocery location and product data, and stores normalized snapshots in PostgreSQL for analytics, comparison, and future monetization.


# Project-Goals

The Foo Data Platform is designed to:
    -Authenticate securely with the Kroger API using OAuth 2.0
    -Pull small, controlled datasets (locations, products, prices)
    -Store raw data reliably in PostgreSQL
    -Serve as the foundation for:
        -price comparison dashboards
        -historical price tracking
        -data analytics & reporting
        -future API / SaaS offerings


# Architecture-Overview

Backend
    -Java 21
    -Spring Boot 4
    -Spring MVC
    -Spring Data JPA
    -Flyway

Database
    -PostgreSQL 16 (Dockerized)

External APIs
    -Kroger API (Certification environment)
        -OAuth 2.0 client credentials
        -Locations
        -Products

Containerization
    -Docker & Docker Compose (Postgres only; backend runs locally during development)


# Security-&-Credentials

The application requires Kroger API credentials to run.

For Docker based services (Postgres), these are loaded via .env.
For local Spring Boot runs, they must be provided via:
    -IDE run configuration or
    -exported shell environment variables

Credentials are:
    -validated at startup
    -logged only in masked form
    -never returned by any endpoint


# Local-Development-Setup

Step 1 Start PostgreSQL on Docker

From the repo root:
    "docker compose up -d db"

Starts:
    -PostgreSQL on localhost:5432
    -persistent volume storage

Step 2 Run Backend (Spring Boot)

From the backend/ directory:
    "mvnw spring-boot:run"

Ensure Kroger credentials are available in the environment before starting.


# OAuth-Token-Handling

-Tokens are fetched lazily (on first API call)
-Tokens are cached in memory
-Tokens refresh automatically before expiry
-Only one refresh happens at a time (thread-safe)

Debug Endpoint
    "GET /api/kroger/token/status"


# Simplified-Project-Structure

backend/
    -config/              # Config & property binding
    -kroger/
        -auth/             # OAuth token fetch + caching
        -locations/        # Kroger Locations client & DTOs
    -startup/              # Startup validation checks
    -resources/
        -db/migration/     # Flyway migrations


# Testing

-Token caching and refresh behavior validated manually
-API connectivity verified against Kroger Certification environment
-Automated tests will be added incrementally as ingestion stabilizes


# Notes

-This project is intentionally backend-heavy.
-Code favors clarity and correctness over optimization.
-Certification credentials are used during development.