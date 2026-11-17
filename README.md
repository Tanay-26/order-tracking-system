# Real-Time Order Tracking System

Java 17 · Spring Boot 3.2 · Redis (Streams, Pub/Sub, TTL) · PostgreSQL · WebSocket/STOMP

Event-driven order tracking across 6 stages, with live rider GPS pushed to the browser
every 3 seconds. Status events go through a durable Redis Stream into PostgreSQL;
GPS pings never touch the database at all.

## Quick start

```bash
docker compose up -d                 # PostgreSQL + Redis
mvn clean package -DskipTests
mvn spring-boot:run                  # http://localhost:8080
open http://localhost:8080/index.html

cd simulator && pip3 install requests
python3 rider_simulator.py           # creates an order and pings GPS every 3s
```

Paste the printed `orderRef` into the web page to watch the progress bar and the
coordinates update live.

## Architecture in one line

REST → PostgreSQL (durable) → Redis Stream (XADD) → consumer group → audit row →
Redis Pub/Sub → every app instance → WebSocket → browser.
GPS: REST → Redis SET with 30s TTL + Pub/Sub → WebSocket. No database.

## API

| Method | Endpoint |
|---|---|
| POST | `/api/v1/orders` |
| GET | `/api/v1/orders/{ref}` |
| PATCH | `/api/v1/orders/{ref}/status` |
| PATCH | `/api/v1/orders/{ref}/rider` |
| GET | `/api/v1/orders/{ref}/events` |
| POST | `/api/v1/riders/{id}/location` |
| GET | `/api/v1/riders/{id}/location` |
| GET | `/api/v1/riders/{id}/trail` |
| WS | `/ws` → `/topic/orders/{ref}` and `/topic/orders/{ref}/location` |

See the full guide (`Real-Time-Order-Tracking-System.pdf`) for the architecture
walkthrough, technology justifications and interview prep.
