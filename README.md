# Foo Data Platform (FDP)

---

## System Flow

```
External APIs
    --> API Clients
        --> Source Adapters
            --> Canonical Models
                --> PostgreSQL Database
                    --> Query Services
                        --> Applications
```

> This design ensures that new APIs can be added without modifying ingestion infrastructure.

---

## Example Data Model

### Price Observation

| Field       | Description                       |
|-------------|-----------------------------------|
| product_id  | Canonical product identifier      |
| location_id | Store or geographic location      |
| price       | Observed price                    |
| observed_at | Timestamp of observation          |
| source      | API provider                      |

> Each observation is immutable, allowing historical price analysis and trend detection.

---

## Example API Responses

### Product Search

```
GET /api/products/search?q=milk
```

```json
{
  "productId": "12345",
  "name": "Whole Milk",
  "brand": "Example Brand",
  "category": "Dairy"
}
```

### Price History

```
GET /api/prices/{productId}
```

```json
{
  "productId": "12345",
  "observations": [
    { "price": 3.49, "observedAt": "2026-01-01" },
    { "price": 3.29, "observedAt": "2026-02-01" }
  ]
}
```

---

## Engineering Challenges Solved

### Integrating Inconsistent APIs
External APIs return different schemas and authentication mechanisms. The platform uses adapters and canonical DTOs to normalize all sources.

### Preventing Duplicate Data
Idempotent ingestion ensures repeated API pulls do not create duplicate records.

### Handling Rate Limits
Quota tracking and ingestion scheduling prevent API rate limit violations.

### Observability and Debugging
All API responses are archived as raw payloads, enabling replay and debugging of ingestion runs.

---

## Running Tests

docker compose run --rm test

---

## Future Development

Planned improvements include:

- Additional API integrations
- Event-driven architecture using Kafka
- Independent ingestion and query services
- Advanced analytics and visualization
- Mobile and web client applications

---

## Author

**Darian Rodriguez**
Software Engineer | Data Platform Developer