# RideSwift

Full-stack ride-sharing platform (Uber-style) — **Spring Boot** backend + **React + TypeScript**
SPA. It implements **all six phases** of the project design: **1 — Foundation**,
**2 — Core Ride Flow**, **3 — Payments & Transactions**, **4 — Real-Time Features**,
**5 — Resilience & Admin**, and **6 — Advanced & ML (surge pricing)**, with a polished,
role-aware web client for passengers, drivers, and admins.

## Quick start (full stack)

```bash
docker compose up --build
```

That builds and starts Postgres+PostGIS, Redis, the Spring Boot API, and the web client.
Open the app at **http://localhost:5173** — the API is at `http://localhost:8080`
(Swagger UI at `/swagger-ui.html`). On first boot the `dev` profile seeds a complete
demo dataset (18 users, 11 drivers, 32 historical rides with payments).

### Demo accounts (password: `password123`)

| Role | Email | What to try |
|---|---|---|
| Passenger | `alice@rideswift.io` | Book a ride (tap the map or a sample trip), see live fare estimates, track + pay |
| Driver | `mike@rideswift.io` | Go online, accept an incoming request, drive pickup → start → complete |
| Admin | `admin@rideswift.io` | Live dashboard, verify the pending driver, edit fare rules, audit a ride, simulate a payment outage |

> **Full lifecycle demo:** open the passenger app in one window and the driver app in another.
> Put the driver online, then request a ride as the passenger — the driver receives the
> request over WebSocket, and the passenger sees the driver's location update live.

The login screen also has one-tap demo buttons for each role.

## Frontend

A Vite + React 18 + TypeScript SPA in [`frontend/`](frontend/):

- **State:** Zustand (auth/ride stores) + TanStack React Query (server state)
- **Real-time:** SockJS + STOMP client ([useRideTracking](frontend/src/hooks/useRideTracking.ts),
  [useDriverRequests](frontend/src/hooks/useDriverRequests.ts))
- **Maps:** Leaflet + OpenStreetMap (no API key required) with pickup/drop-off pins and live driver marker
- **UI:** Tailwind CSS, shadcn-style primitives, dark mode, Recharts dashboards
- **Forms:** React Hook Form + Zod validation

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api + /ws to the backend (BACKEND_URL, default :8080)
```

## Phase 1 scope

- Spring Boot 3.3 / Java 21 project scaffolding
- Docker Compose with **PostGIS** (PostgreSQL + geospatial) and Redis
- Flyway-managed schema with PostGIS enabled (`users`, `drivers`, `vehicles`, `refresh_tokens`)
- JWT authentication: register, login, refresh (with rotation), logout
- BCrypt password hashing (strength 12), stateless security, role-based access
- Basic CRUD for users, drivers, and vehicles

## Phase 2 scope

- Ride request endpoint with tentative driver assignment
- Nearest-driver search via **PostGIS** spatial query (`ST_DWithin` + KNN, GIST index)
- **Redis** driver-location cache (GEO set + per-driver key, 10s TTL); matching reads
  Redis first and falls back to PostGIS when the cache is empty/unavailable
- Driver accept / decline (with automatic re-match) flow
- Ride lifecycle state machine (`SCHEDULED → REQUESTED → MATCHED → IN_PROGRESS → COMPLETED`, plus `CANCELLED`)
- Standard fare calculation via a pluggable **Strategy** (`fare_rules` table, fare estimate API)

## Phase 3 scope

- Payment gateways behind a **Factory** (`STRIPE`/`PAYPAL`/`RAZORPAY`; simulated stubs ready for real SDKs)
- Charge + refund run `@Transactional(SERIALIZABLE)` with a pessimistic lock on the ride —
  lock → create PENDING → charge gateway → mark SUCCESS, rolling back entirely on any failure
- **Hibernate Envers** audit trails on `Ride` and `Payment` (`rides_aud`/`payments_aud`/`revinfo`),
  with a custom revision entity recording which user made each change
- Refund flow (admin-only)

## Phase 4 scope

- **STOMP over WebSocket** (`/ws` SockJS for browsers, `/ws-native` plain): live ride-status,
  driver-location, and incoming-request pushes ([RideTrackingHandler](src/main/java/com/rideswift/websocket/RideTrackingHandler.java))
- **Observer pattern** for ride lifecycle events ([RideEventPublisher](src/main/java/com/rideswift/event/RideEventPublisher.java)
  → `NotificationObserver`, `AuditObserver`); failing observers are isolated
- Persisted in-app **notifications** (`notifications` table) with an inbox API
- **Quartz** maintenance-notification job ([MaintenanceNotificationJob](src/main/java/com/rideswift/scheduler/MaintenanceNotificationJob.java))

## Phase 5 scope

- **Resilience4j** circuit breaker + retry + bulkhead + fallback on the payment gateway
  ([ResilientGatewayExecutor](src/main/java/com/rideswift/payment/ResilientGatewayExecutor.java));
  breaker state visible at `/actuator/circuitbreakers`
- **Admin** APIs ([AdminController](src/main/java/com/rideswift/controller/AdminController.java)):
  dashboard metrics, pending-driver list, fare-dispute adjustment, system broadcast, and
  Envers audit-trail viewer ([AuditService](src/main/java/com/rideswift/service/AuditService.java))
- Driver verification workflow (pending list + verify/reject, with notification to the driver)
- **Log4j2** structured logging (key=value + MDC `requestId`/`userId`; [log4j2-spring.xml](src/main/resources/log4j2-spring.xml))
- Per-IP **rate limiting** on auth endpoints (5/min) + security hardening (CSP, frame-options,
  HSTS, nosniff, referrer-policy; actuator restricted to ADMIN except health/info)

## Phase 6 scope

- **Surge pricing** from a live demand/supply ratio within geospatial **surge zones**
  (PostGIS polygons; point-in-zone via `ST_Covers`) — [SurgePricingService](src/main/java/com/rideswift/service/SurgePricingService.java)
- **Decorator** [SurgeFareStrategy](src/main/java/com/rideswift/strategy/SurgeFareStrategy.java)
  layers the dynamic multiplier on top of the standard fare
- **ML demand-prediction stub** ([DemandPredictionService](src/main/java/com/rideswift/service/DemandPredictionService.java))
  feeds predicted demand into the ratio (time-of-day heuristic, ready to swap for a real model)
- Redis **caching** of per-zone surge multipliers (short TTL); admin surge-zone CRUD

## Phase 7+ — Real-world enhancements

Beyond the original six phases, these bring the app closer to a production ride-hailing
product. Every external integration is **configurable with a graceful fallback**, so the
app never hard-breaks if a dependency is unavailable.

- **Address search & autocomplete** — typed pickup/drop-off via a **Nominatim (OSM)** proxy
  ([GeocodingService](src/main/java/com/rideswift/service/GeocodingService.java)), Redis-cached;
  tapping the map reverse-geocodes to a real address. Keyless.
- **Real road routing** — distances/ETAs and the map polyline come from a self-hosted
  **OSRM** engine ([RoutingService](src/main/java/com/rideswift/service/RoutingService.java));
  `FareService` now bills on real road distance (e.g. Times Sq → Brooklyn Bridge is 10.4 km by
  road vs 5.9 km straight-line). Falls back to a haversine estimate if OSRM is down. A NYC-only
  graph (`./osrm/prepare.sh`) is small enough for a free-tier container.
- **Real driver GPS** — driver dashboard toggles between *Demo motion* (simulated, works
  anywhere) and *Live GPS* (`navigator.geolocation`), streamed to the backend.
- **PIN-verified pickup** — a 4-digit PIN is generated on match; the passenger shows it and
  the driver enters it to start the trip (`RideResponse` exposes the PIN to the passenger only).
- **Scheduled (book-for-later) rides** — a future `scheduledAt` parks the ride in a new
  `SCHEDULED` state; a resilient **Quartz sweeper**
  ([ScheduledRideDispatchJob](src/main/java/com/rideswift/scheduler/ScheduledRideDispatchJob.java))
  polls the DB and dispatches due rides to matching (survives restarts, unlike per-ride jobs).
- **Tipping** — optional gratuity added at payment; the charged amount = fare + tip.
- **Auth UX fix** — unauthenticated requests now return **401** (not Spring's default 403) so
  the SPA refreshes an expired access token transparently instead of logging the user out;
  authenticated-but-forbidden still returns **403**.

See [DEPLOY.md](DEPLOY.md) for a free-tier deployment guide (Vercel + Render/Fly + Supabase/Neon
+ Upstash + a NYC-bbox OSRM container).

## Design patterns used

Strategy (fare) · Decorator (surge) · Factory (payment gateways) · Observer (ride lifecycle
events) · State machine (ride status) · plus circuit-breaker/retry/bulkhead resilience around
the payment gateway.

## Tech stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.3.5 |
| Language | Java 21 |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate (+ hibernate-spatial) |
| Auth | JWT (jjwt 0.12) + Spring Security |
| API docs | springdoc-openapi (Swagger UI) |

## Running locally

Start the database (and Redis, reserved for later phases):

```bash
docker compose up -d
```

Run the application:

```bash
mvn spring-boot:run
```

The API is served at `http://localhost:8080`. Interactive docs:
`http://localhost:8080/swagger-ui.html`.

### Configuration

Settings live in `src/main/resources/application.yml` and are overridable via
environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/rideswift` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `rideswift` / `rideswift` | DB credentials |
| `JWT_SECRET` | dev-only key | **Override in production.** Base64 256-bit key |
| `JWT_ACCESS_TTL` | `900` | Access-token lifetime (seconds) |
| `JWT_REFRESH_TTL` | `604800` | Refresh-token lifetime (seconds) |

## API quick reference

### Auth (public)
- `POST /api/v1/auth/register` — `{ name, email, phone, password, role }`
- `POST /api/v1/auth/login` — `{ email, password }`
- `POST /api/v1/auth/refresh` — `{ refreshToken }`
- `POST /api/v1/auth/logout` — `{ refreshToken }`

All other endpoints require an `Authorization: Bearer <accessToken>` header.

### Users
- `GET /api/v1/users/me`
- `GET /api/v1/users` (ADMIN) · `GET /api/v1/users/{id}` (self or ADMIN)
- `PATCH /api/v1/users/{id}` (self or ADMIN) · `DELETE /api/v1/users/{id}` (ADMIN)

### Drivers
- `POST /api/v1/drivers/register` — `{ licenseNumber }` (registers the caller as a driver)
- `GET /api/v1/drivers` (ADMIN) · `GET /api/v1/drivers/{id}`
- `PATCH /api/v1/drivers/{id}/verify` (ADMIN) · `PATCH /api/v1/drivers/{id}/reject` (ADMIN)
- `DELETE /api/v1/drivers/{id}` (ADMIN)

### Vehicles
- `POST /api/v1/drivers/{driverId}/vehicles` (DRIVER/ADMIN)
- `GET /api/v1/drivers/{driverId}/vehicles` · `GET /api/v1/vehicles/{id}`
- `PUT /api/v1/vehicles/{id}` (DRIVER/ADMIN) · `DELETE /api/v1/vehicles/{id}` (DRIVER/ADMIN)

### Drivers — location & matching (Phase 2)
- `GET /api/v1/drivers/me` (DRIVER) — own profile
- `PATCH /api/v1/drivers/location` (DRIVER) — `{ latitude, longitude }`
- `PATCH /api/v1/drivers/availability` (DRIVER) — `{ available }` (only verified drivers may go online)
- `GET /api/v1/drivers/nearby?lat=&lng=&vehicleType=` — nearest available drivers

### Rides (Phase 2)
- `POST /api/v1/rides/request` — `{ pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude, pickupAddress?, dropoffAddress?, vehicleType }`
- `GET /api/v1/rides/history` · `GET /api/v1/rides/{rideId}` (participant or ADMIN)
- `PATCH /api/v1/rides/{rideId}/accept|decline|start|complete` (assigned DRIVER)
- `PATCH /api/v1/rides/{rideId}/cancel` (passenger or assigned driver, before start)

### Fare (Phase 2)
- `POST /api/v1/fare/estimate` — `{ pickup/dropoff lat/lng, vehicleType }`
- `GET /api/v1/fare/rules` · `PUT /api/v1/fare/rules/{id}` (ADMIN)

### Payments (Phase 3)
- `POST /api/v1/payments/initiate` — `{ rideId, gateway, currency? }` (passenger, ride must be COMPLETED)
- `GET /api/v1/payments/{paymentId}` (participant or ADMIN)
- `POST /api/v1/payments/refund` — `{ paymentId }` (ADMIN)

### Notifications & real-time (Phase 4)
- `GET /api/v1/notifications` · `PATCH /api/v1/notifications/{id}/read` · `PATCH /api/v1/notifications/read-all`
- WebSocket: connect to `/ws` (SockJS) or `/ws-native` (plain STOMP), then SUBSCRIBE to:
  - `/topic/ride/{rideId}/status` — ride lifecycle updates
  - `/topic/ride/{rideId}/driver-location` — live driver GPS during the trip
  - `/topic/driver/{driverId}/requests` — incoming ride requests

### Admin & ops (Phase 5)
- `GET /api/v1/admin/dashboard` — active/completed/cancelled rides, revenue, user/driver counts, pending verifications
- `GET /api/v1/admin/drivers/pending` · `PATCH /api/v1/drivers/{id}/verify|reject`
- `PATCH /api/v1/admin/rides/{rideId}/fare` — fare-dispute adjustment (audited)
- `POST /api/v1/admin/notifications/broadcast` — `{ message, role?, type? }`
- `GET /api/v1/admin/audit/rides/{rideId}` — Envers revision history (who changed what, when)
- `POST /api/v1/admin/payments/outage?enabled=` — toggle simulated outage to exercise the breaker
- `GET /actuator/health` (public) · `GET /actuator/circuitbreakers` (ADMIN)

### Surge pricing (Phase 6)
- `GET /api/v1/fare/surge?lat=&lng=` — current zone, multiplier, demand, supply
- `POST /api/v1/admin/surge-zones` — `{ name, coordinates: [[lng,lat],…], minMultiplier?, maxMultiplier? }`
- `GET /api/v1/admin/surge-zones` · `PATCH /api/v1/admin/surge-zones/{id}/active?active=` · `DELETE /api/v1/admin/surge-zones/{id}`
- Surge is applied automatically inside `POST /api/v1/fare/estimate` and `POST /api/v1/rides/request`
  (response shows `strategy: SURGE` and `surgeMultiplier`)

## Notes / deviations from the spec

- **Refresh tokens** are stored in Postgres (`refresh_tokens`, SHA-256 hashed) for Phase 1
  so the service runs with no external dependency. The spec migrates this store to Redis
  in a later phase; Redis is already provisioned in `docker-compose.yml`.
- The spec's circular `drivers.vehicle_id` / `vehicles.driver_id` pair is modelled as a
  clean one-to-many: a driver owns vehicles via `vehicles.driver_id`.
- Driver verification uses a `verification_status` enum (`PENDING`/`VERIFIED`/`REJECTED`)
  rather than a single `is_verified` boolean, to support the Phase 5 onboarding flow.
- `rides.vehicle_type` is captured at request time (not in the spec's `rides` schema) so a
  fare can be estimated and a driver matched before a concrete vehicle is assigned.
- On ride request a nearest eligible driver is *tentatively assigned* while the ride stays
  `REQUESTED`; it only becomes `MATCHED` once that driver accepts. Phase 2 has no live trip
  tracking, so `actual_fare` is finalised to the estimate on completion.
- Auditing uses Hibernate Envers' standard `*_aud` + `revinfo` tables rather than the spec's
  single `audit_log` JSONB table; a custom revision entity records the acting user id. The
  geometry/address columns are `@NotAudited` to keep the audit tables simple.
- One payment per ride (`ux_payments_ride`); payment is taken after the ride is `COMPLETED`
  (the explicit `/payments/initiate` endpoint), not at ride confirmation.
- Gateway integrations are simulated stubs (no live Stripe/PayPal/Razorpay account) with the
  charge/refund seams in place for real SDK wiring.
- WebSocket uses Spring's in-memory simple broker and the handshake is permitted without auth
  (per-topic authorization is a later hardening step); the spec's Redis pub/sub relay for
  multi-instance scaling is noted but not wired in Phase 4.
- The Quartz job uses an in-memory store and fires once at startup then on its interval
  (default daily; override with `MAINTENANCE_INTERVAL` seconds).
- Logging backend swapped from Logback to Log4j2 (`spring-boot-starter-logging` excluded from
  each starter). Structured output is a key=value pattern with MDC correlation, not JSON
  (JSON layout would need an extra `log4j-layout-template-json` dependency).
- The circuit breaker wraps the payment gateway (the genuinely external dependency). The
  spec also lists notification services, but those are local DB writes here, so no breaker
  is applied to them yet. The breaker's bulkhead is semaphore-based, not a separate thread pool.
- Rate limiting is in-memory per-instance (5/min/IP, configurable via
  `rideswift.security.rate-limit.max-requests`); a distributed Redis limiter is a scaling step.
- HSTS is only emitted over HTTPS (Spring default), so it is absent on plain-HTTP local runs.
- Surge multiplier = `clamp((demand + predictedDemand) / max(supply,1), zone.min, zone.max)`,
  rounded to one decimal. `DemandPredictionService` is a deterministic time-of-day stub standing
  in for a real ML model. Surge is captured in the ride's `estimated_fare`; no separate
  surge column is stored on `rides`.
```
