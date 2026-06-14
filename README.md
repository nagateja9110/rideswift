# RideSwift

An Uber-style ride-hailing app I built end to end: a Spring Boot API, a React + TypeScript
frontend, Postgres/PostGIS for the geo work, and Redis for driver matching. Trips are tracked
live over WebSockets. It runs a small simulated fleet around Coimbatore, so you can watch the
whole flow (request → match → drive → pay) without needing two phones.

## Run it locally

```bash
docker compose up --build
```

That starts Postgres+PostGIS, Redis, the API and the web client. Open http://localhost:5173.
Swagger docs are at http://localhost:8080/swagger-ui.html. The first boot seeds a demo dataset
(drivers around Coimbatore and Ettimadai, plus some ride history).

## Demo logins

Password for all of them is `password123`.

| Role | Email |
|---|---|
| Passenger | alice@rideswift.io |
| Driver | mike@rideswift.io |
| Admin | admin@rideswift.io |

To see a full trip, open the passenger app in one window and the driver app in another, put the
driver online, then book a ride. The request pops up on the driver side over WebSocket and the
passenger watches the car move in real time. There's also a server-side fleet that auto-accepts,
so a passenger booking will still complete on its own if nobody's logged in as a driver.

## What's in it

- Map-based booking with address search (Nominatim) and real road routes + ETAs (OSRM, with a
  straight-line fallback when it's unavailable)
- Nearest-driver matching with PostGIS spatial queries backed by a Redis geo cache
- Full ride lifecycle: 4-digit pickup PIN, live tracking, ratings, tips, scheduled rides
- Payments via Razorpay (test mode), with refunds and an Envers audit trail
- Surge pricing per zone from a live demand/supply ratio
- Admin side: driver verification, fare rules, broadcasts, audit history, and an outage toggle
  to exercise the payment circuit breaker
- Auth via email/JWT or phone OTP (Firebase)

## Stack

**Backend** — Java 21, Spring Boot 3.3, Postgres 16 + PostGIS, Redis, Flyway, Spring Security
(JWT), Quartz, Resilience4j.

**Frontend** — React 18 + TypeScript, Vite, Zustand + React Query, Tailwind, Leaflet
(OpenStreetMap), SockJS/STOMP.

A few classic patterns show up where they fit: Strategy + Decorator for fare/surge, Factory for
payment gateways, Observer for ride events, and a small state machine for ride status.

## Deploying

See [DEPLOY.md](DEPLOY.md). The included `render.yaml` deploys the whole stack to Render's free
tier in one shot (Postgres, Redis, API and the static frontend).
