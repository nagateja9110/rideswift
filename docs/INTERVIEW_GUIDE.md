# RideSwift — Engineering Deep Dive & Interview Guide

> A complete, honest walkthrough of how RideSwift works end to end: architecture, every
> subsystem, the design decisions and trade-offs behind them, the challenges hit while
> building it, and how to answer the interview questions that follow from each. Read this
> top to bottom and you can defend any part of the project.

- **Live:** https://rideswift-web.onrender.com/
- **Repo:** https://github.com/nagateja9110/rideswift
- **Stack:** Java 21 · Spring Boot 3.3 · React 18 + TypeScript · PostgreSQL 16 + PostGIS · Redis · WebSockets (STOMP/SockJS) · Docker · Render

---

## Table of contents

1. [The 60-second pitch](#1-the-60-second-pitch)
2. [High-level architecture](#2-high-level-architecture)
3. [Data model](#3-data-model)
4. [The ride lifecycle (state machine)](#4-the-ride-lifecycle-state-machine)
5. [Driver matching & dispatch](#5-driver-matching--dispatch)
6. [Geospatial: PostGIS + Redis GEO](#6-geospatial-postgis--redis-geo)
7. [Live tracking over WebSockets](#7-live-tracking-over-websockets)
8. [Fares & surge pricing](#8-fares--surge-pricing)
9. [Payments](#9-payments)
10. [Authentication & security](#10-authentication--security)
11. [Background jobs (Quartz)](#11-background-jobs-quartz)
12. [The simulated fleet](#12-the-simulated-fleet)
13. [Resilience & failure handling](#13-resilience--failure-handling)
14. [Design patterns used](#14-design-patterns-used)
15. [Frontend architecture](#15-frontend-architecture)
16. [Deployment](#16-deployment)
17. [Challenges & how I solved them](#17-challenges--how-i-solved-them)
18. [Known limitations & feasibility](#18-known-limitations--feasibility)
19. [Improvements & what I'd do next](#19-improvements--what-id-do-next)
20. [Rapid-fire interview Q&A](#20-rapid-fire-interview-qa)

---

## 1. The 60-second pitch

RideSwift is a full-stack Uber-style ride-hailing platform with three roles — **passenger**,
**driver**, and **admin**. A passenger books a ride; the backend finds the nearest eligible
driver using geospatial queries; the driver accepts; the passenger watches the car move on a
live map in real time; and at drop-off the passenger pays through Razorpay.

The interesting engineering is in four places:
1. **Geospatial matching** — PostGIS spatial queries with a Redis geo-index cache in front.
2. **Real-time tracking** — driver GPS streamed to the passenger's map over STOMP WebSockets.
3. **Dynamic pricing** — a surge multiplier computed from live demand/supply per zone.
4. **Correctness under failure** — a strict ride state machine, serializable payment
   transactions, and circuit breakers around the payment gateway.

To make the demo feel alive without a second phone, there's a **server-side simulated fleet**
that auto-accepts and drives rides — but it yields the moment a real driver app comes online.

---

## 2. High-level architecture

```
┌──────────────────────────── Browser (React SPA) ────────────────────────────┐
│  Passenger UI   │   Driver UI   │   Admin UI                                  │
│  React 18 + TS · Vite · Zustand + React Query · Tailwind · Leaflet · SockJS  │
└───────┬───────────────────────────────────────────────┬──────────────────────┘
        │ REST  (JWT bearer, /api/v1/**)                 │ WebSocket (STOMP/SockJS, /ws)
        ▼                                                ▼
┌──────────────────────────── Spring Boot API (Java 21) ───────────────────────┐
│  Controllers ── Services ── Repositories                                      │
│  Security: JWT auth filter, rate limiter, BCrypt, CORS                        │
│  Domain services: Matching · Fare+Surge · Routing · Payment · Location        │
│  Messaging: STOMP broker (SimpMessagingTemplate) → /topic/**                  │
│  Events: RideEventPublisher → Observers (Notification push, Audit)            │
│  Scheduled (Quartz): Dispatch sweep · Fleet simulation · Scheduled rides ·    │
│                       Maintenance                                             │
└───────┬───────────────────────┬───────────────────────┬──────────────────────┘
        ▼                        ▼                        ▼
┌───────────────┐      ┌──────────────────┐     ┌─────────────────────────────┐
│ PostgreSQL 16 │      │      Redis        │     │  External HTTP services     │
│  + PostGIS    │      │  geo-index, OTP,  │     │  OSRM (routing/ETA)         │
│  Flyway-      │      │  surge cache,     │     │  Nominatim (geocoding)      │
│  migrated     │      │  rate limit,      │     │  Razorpay (payments)        │
│  + Envers     │      │  driver presence  │     │  Firebase (phone OTP)       │
│  audit        │      │                   │     │  each has a fallback        │
└───────────────┘      └──────────────────┘     └─────────────────────────────┘
```

**Why this shape?**
- **Layered backend** (Controller → Service → Repository) keeps HTTP concerns, business logic,
  and persistence separate and testable.
- **PostGIS is the source of truth** for driver locations; **Redis is a cache** in front of it.
  This gives Redis-speed reads without sacrificing durability or correctness — and the system
  keeps working if Redis is down.
- **Every external dependency has a fallback** (OSRM → straight-line, Redis → PostGIS, Firebase
  → server-side OTP) so a third-party outage degrades quality rather than breaking the app.

---

## 3. Data model

Core entities (all extend `BaseEntity` → UUID primary key + `created_at`/`updated_at`):

| Entity | Key fields | Notes |
|---|---|---|
| **User** | name, email, phone, hashedPassword, role | role ∈ PASSENGER/DRIVER/ADMIN |
| **Driver** | user (1:1), licenseNumber, rating, verificationStatus, available, **currentLocation** (`geography(Point,4326)`) | location is PostGIS |
| **Vehicle** | driver, make, model, year, licensePlate, vehicleType | a driver can own several |
| **Ride** | passenger, driver, vehicleType, pickup/dropoff (PostGIS points + addresses), **status**, fares, distance, duration, **pickupPin**, scheduledAt, offerExpiresAt | the central aggregate |
| **RideOffer** | ride, driver, status, offeredAt, expiresAt, respondedAt | one row per dispatch offer — the audit trail of who was asked |
| **Payment** | ride (1:1), amount, tipAmount, currency, gateway, gatewayOrderId, gatewayTransactionId, **status** | INR; Envers-audited |
| **FareRule** | vehicleType, baseFare, perKmRate, perMinuteRate, surgeMultiplier, effectiveFrom/To | time-versioned pricing |
| **SurgeZone** | name, **area** (`geography(Polygon)`), minMultiplier, maxMultiplier | PostGIS polygons |
| **Notification** | user, message, type, read | in-app feed |
| **RefreshToken** | user, tokenHash (SHA-256), expiresAt, revoked | only the hash is stored |
| **RideMessage** | ride, sender, content, sentAt | in-ride chat |

**Schema management:** Flyway, 14 versioned migrations (`V1__init_schema.sql` … `V14__…`).
PostGIS extension is enabled in V1, and a **GIST index** backs the driver-location column:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE INDEX ix_drivers_location ON drivers USING GIST (current_location);
```

**Auditing:** Hibernate Envers audits `Ride` and `Payment` (the `@Audited` annotation), giving a
full revision history of every status/fare change — surfaced in the admin "audit log" page.
Transient secrets like the pickup PIN are marked `@NotAudited`.

---

## 4. The ride lifecycle (state machine)

A `Ride` moves through an explicit enum-backed state machine. **Every transition is validated**
in `RideStatus.canTransitionTo()` before it's applied — illegal jumps throw a `409 Conflict`.

```
 SCHEDULED ──► REQUESTED ──► MATCHED ──► IN_PROGRESS ──► COMPLETED
     │             │            │
     └──► CANCELLED └► CANCELLED └► CANCELLED
                   └► EXPIRED   (no driver accepted within the dispatch window)

 COMPLETED / CANCELLED / EXPIRED are terminal.
```

```java
public boolean canTransitionTo(RideStatus target) {
    return switch (this) {
        case SCHEDULED   -> Set.of(REQUESTED, CANCELLED).contains(target);
        case REQUESTED   -> Set.of(MATCHED, CANCELLED, EXPIRED).contains(target);
        case MATCHED     -> Set.of(IN_PROGRESS, CANCELLED).contains(target);
        case IN_PROGRESS -> target == COMPLETED;
        case COMPLETED, CANCELLED, EXPIRED -> false;
    };
}
```

Lifecycle events drive everything else: each successful transition in `RideService` calls
`rideEventPublisher.publish(RideEvent.of(TYPE, ride))`, and **Observers** react — pushing live
status to the passenger, notifying the driver, and writing audit entries.

**Why a state machine?** It centralizes the legal-transition rules in one place instead of
scattering `if (status == X)` checks across the codebase, and it makes illegal states
unrepresentable. This is the single most important correctness mechanism in the app.

---

## 5. Driver matching & dispatch

### Matching (who is the best driver?)

`MatchingService.findCandidates(lat, lng, vehicleType)` returns the nearest eligible drivers,
nearest first. **Eligible** = available + `VERIFIED` + has a vehicle of the requested type +
has a known location.

It's a **two-tier read**:
1. **Fast path — Redis GEO.** `GEORADIUS` on the `drivers:geo` set returns candidate IDs sorted
   by distance. Those drivers are loaded from Postgres and filtered for eligibility.
2. **Fallback — PostGIS.** If the Redis set is empty or Redis is unreachable, run the
   authoritative spatial query directly (see §6). The result is identical, just slower.

```java
public List<Candidate> findCandidates(double lat, double lng, VehicleType vt) {
    List<Candidate> viaCache = fromRedis(lat, lng, vt);
    return viaCache.isEmpty() ? fromPostgis(lat, lng, vt) : viaCache;
}
```

Config: search radius **15 km** (covers Coimbatore ↔ Ettimadai ~14 km), up to **5 candidates**
per attempt.

### Dispatch (offer → accept, with rollover)

Matching ranks drivers; **dispatch** offers the ride to them one at a time:

1. On booking, `offerToNextDriver()` picks the nearest candidate **not already offered this
   ride**, writes a `RideOffer` (status OFFERED, `expiresAt = now + 35s`), and points the ride
   at that driver.
2. The driver gets a `/topic/driver/{id}/requests` push and has ~30s to accept.
3. **If they accept** (`RideService.accept`): the offer must still be live (not lapsed/rolled).
   Ride → MATCHED, a 4-digit **pickup PIN** is generated, the driver is marked unavailable and
   evicted from the geo-cache.
4. **If they decline or time out:** a Quartz **dispatch sweeper** (runs every 5s) marks the
   offer EXPIRED and rolls to the next nearest driver.
5. **If nobody accepts within 120s** total, the ride goes **EXPIRED**.

**Why offers as their own table?** It gives an auditable record of who was asked and when, makes
"don't re-offer to someone who already declined" trivial (`findOfferedDriverIds`), and decouples
the slow human-response loop from the request thread — the sweeper handles rollovers
asynchronously.

**Concurrency note:** two passengers could be offered the same driver. The driver accepting one
ride sets `available=false`; the other ride's accept then fails the eligibility/offer-still-live
check and rolls onward. The DB is the arbiter.

---

## 6. Geospatial: PostGIS + Redis GEO

This is the part most worth understanding deeply.

### PostGIS (authoritative)

Driver location is a `geography(Point, 4326)` column (4326 = WGS-84 lat/lng). The nearest-driver
query uses two PostGIS features together:

```sql
SELECT d.* FROM drivers d
WHERE d.is_available = true
  AND d.verification_status = 'VERIFIED'
  AND d.current_location IS NOT NULL
  AND EXISTS (SELECT 1 FROM vehicles v
              WHERE v.driver_id = d.id AND v.vehicle_type = :vehicleType)
  AND ST_DWithin(d.current_location,
                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                 :radiusMeters)
ORDER BY d.current_location <-> ST_SetSRID(ST_MakePoint(:lng,:lat),4326)::geography
LIMIT :limit;
```

- **`ST_DWithin(...)`** — a *radius filter*. On the `geography` type it returns true/false for
  "within N **metres**" using true spherical distance, and it's **index-assisted** by the GIST
  index, so it prunes the table fast instead of scanning every driver.
- **`<->` (KNN operator)** — *distance ordering*. Combined with `ORDER BY ... LIMIT`, PostGIS
  walks the GIST index in nearest-first order (a K-nearest-neighbour index scan), so you get the
  closest N drivers without sorting the whole result set.

Using both means: filter to a sane radius, then order what survives by true distance — both
backed by one spatial index.

### Redis GEO (cache)

Redis natively stores geo data (`GEOADD`/`GEORADIUS`, internally a sorted set keyed by a geohash
score). `LocationService` mirrors every location fix into:
- a `drivers:geo` GEO set (for radius search), and
- a per-driver key `driver:location:{id}` with a short TTL.

Reads (`nearbyCandidateIds`) do `GEORADIUS ... WITHDIST ASC LIMIT n`. **All Redis writes are
best-effort**: wrapped in try/catch, failures are logged and swallowed, and reads return `null`
to signal "fall back to PostGIS." A cache outage never fails a location update or a match.

**Why cache at all if PostGIS is fast?** Location updates are extremely **write-heavy** (every
moving driver, every couple of seconds) and matching is **read-heavy**. Redis absorbs both at
in-memory speed and keeps that load off the primary database. PostGIS stays the durable source
of truth and the correctness backstop.

---

## 7. Live tracking over WebSockets

The passenger's "car moving on the map" is powered by **STOMP over WebSocket (SockJS fallback)**.

- **Endpoint:** clients connect to `/ws` (browser, SockJS) or `/ws-native` (raw WS for mobile).
- **Broker:** Spring's simple in-memory STOMP broker; server pushes via `SimpMessagingTemplate`.
- **Topics (server → client):**
  - `/topic/ride/{rideId}/driver-location` — live GPS fixes
  - `/topic/ride/{rideId}/status` — lifecycle updates (MATCHED → IN_PROGRESS → …)
  - `/topic/driver/{driverId}/requests` — incoming ride-request popups
  - `/topic/ride/{rideId}/messages` — in-ride chat

**Flow:** the driver app (or the simulated fleet) reports a new position → backend persists it →
`RideTrackingHandler.sendDriverLocation()` publishes to the ride's topic → the passenger's
`useRideTracking` hook (subscribed to that topic) updates a Zustand store → Leaflet re-renders
the car marker. Status changes ride the `/status` topic the same way.

```java
public void sendDriverLocation(UUID rideId, DriverLocationMessage msg) {
    messaging.convertAndSend("/topic/ride/" + rideId + "/driver-location", msg);
}
```

**Why STOMP, not raw WebSocket or polling?** STOMP gives a clean pub/sub model
(SUBSCRIBE/topic semantics) on top of WebSocket, and SockJS provides automatic fallback (xhr-
streaming, long-polling) for networks that block raw WS. Polling at the cadence needed for smooth
car movement would hammer the API; a push channel is the right tool.

---

## 8. Fares & surge pricing

### Base fare — Strategy pattern

`StandardFareStrategy`:
```
fare = (baseFare + perKmRate · distanceKm + perMinuteRate · durationMinutes) · surgeMultiplier
```
`distanceKm`/`durationMinutes` come from **real road routing** (OSRM, §13 fallback). Pricing is
read from the **active `FareRule`** for that vehicle type — fare rules are time-versioned
(`effectiveFrom`/`effectiveTo`), so price changes are auditable and never destroy history.

### Surge — Decorator pattern

`SurgePricingService.currentMultiplier(lat, lng)`:
1. Find the pickup's `SurgeZone` via PostGIS (`ST_Covers(zone.area, point)`).
2. `demand` = ride requests in that zone in the last 15 min; `supply` = available verified
   drivers currently inside the zone.
3. `ratio = (demand + predictedDemand) / max(1, supply)`, then
   `multiplier = clamp(round(ratio, 1dp), zone.min, zone.max)`.
4. Cache the result in Redis per zone for ~60s (best-effort).

When the multiplier > 1, `FareService` wraps the base strategy in a `SurgeFareStrategy`
**decorator** that multiplies the computed fare:
```java
FareStrategy strategy = surge.compareTo(BigDecimal.ONE) > 0
        ? new SurgeFareStrategy(base, surge)   // decorate
        : base;                                // pass through
```

**Why Strategy + Decorator?** Strategy lets the pricing algorithm be swapped/extended without
touching callers. Decorator lets surge be layered on *any* base strategy at runtime with the
per-request multiplier, without subclass explosion. The two compose cleanly — that's the textbook
reason these patterns exist, and this is a genuine fit rather than a forced one.

---

## 9. Payments

There are **two payment paths** in the codebase, and it's worth being precise about this in an
interview because it shows you understand your own system:

### A) Real Razorpay (the one the live app uses) — `RazorpayService`

Standard **order → checkout → verify** flow, which is the correct, secure way to take card/UPI
payments:
1. **`createOrder`** — validates the ride is COMPLETED and owned by the caller, computes
   `amount = fare + tip` (converted to **paise**), creates a Razorpay order via the SDK, and
   persists a **PENDING** `Payment` with the returned `orderId`.
2. The **frontend opens Razorpay Checkout** with that order id. The customer pays inside
   Razorpay's widget — **card data never touches our server** (PCI scope stays with Razorpay).
3. **`verify`** — Razorpay returns `order_id`, `payment_id`, `signature`. The server recomputes
   the **HMAC signature** with its secret key and only marks the payment **SUCCESS** if it
   matches. *"Never trust the client's word that it paid — the signature is the proof."*

Both methods run `@Transactional(isolation = SERIALIZABLE)` with a **pessimistic row lock** on
the ride (`findByIdForUpdate`), and `verify` is **idempotent** (a second call on an
already-SUCCESS payment just returns it).

### B) Simulated gateways (the "classic patterns" path) — `PaymentService`

A `PaymentGateway` interface with `Stripe`/`PayPal`/`Razorpay` simulated implementations selected
by a **Factory** (`PaymentGatewayFactory`). `PaymentService.initiate()` runs the whole charge in
one **SERIALIZABLE** transaction: lock ride → create PENDING → charge gateway → mark SUCCESS, and
**any failure rolls the entire thing back**. This path demonstrates the gateway abstraction,
the resilient executor (§13), and the all-or-nothing transaction model.

> ⚠️ Honest gotcha to know: `RazorpayGateway` (the simulated stub under `payment/`) is **not** the
> same as `RazorpayService` (the real SDK integration under `service/`). The live app uses the
> real one; the simulated one exists to demonstrate the Factory/Strategy abstraction with a
> pluggable provider.

### The earnings-correctness fix (good story to tell)

Originally the driver's **Earnings** page counted every `COMPLETED` ride. But "completed" is set
the instant the driver taps the button — *before* the passenger pays. So earnings appeared for
unpaid rides. **Fix:** `RideResponse` now carries a `paymentStatus` (bulk-fetched from the
payments table in `history()` to avoid N+1), and the Earnings page only counts rides where
`paymentStatus === 'SUCCESS'`. Earnings now reflect *money actually received*, not just trips
finished. This is a clean example of separating "operationally done" from "financially settled."

---

## 10. Authentication & security

**Token model — JWT access + opaque refresh:**
- **Access token:** signed JWT (HS256), 15-min TTL, carries `sub` (userId), `email`, `role`.
  Stateless — validated on every request by `JwtAuthenticationFilter` with no DB hit.
- **Refresh token:** a random 256-bit opaque value, 7-day TTL. **Only its SHA-256 hash is stored**
  in Postgres, so a DB leak doesn't expose usable tokens. Refresh **rotates**: presenting one
  revokes it and issues a fresh pair (detects token theft/replay).

**Passwords:** BCrypt, strength factor 12.

**Phone login:** two options — (a) server-side OTP: a 6-digit code cached in Redis with a 5-min
TTL, single-use (deleted on verify); (b) Firebase phone auth: the server verifies Google's ID
token and signs the user in by phone number, creating the account on first use.

**Rate limiting:** a fixed-window per-IP filter on the abuse-prone endpoints
(`/auth/login`, `/auth/register`, `/auth/otp/request`) — 5 requests/min/IP, returns `429` with
`Retry-After`. (In-memory, single-instance; Redis-backed is the scaling step.)

**Spring Security hardening:** stateless sessions, CSRF disabled (token auth, not cookies),
method-level `@PreAuthorize`/role checks, and security headers (CSP, HSTS, frame-deny,
referrer-policy). Admin-only actuator endpoints are role-gated.

**CORS / WebSocket cross-origin:** because the frontend (static site) and API are on **different
origins**, CORS uses `setAllowedOriginPatterns("*")` + `setAllowCredentials(true)` (a literal
`*` is rejected by browsers for credentialed requests), and the SockJS endpoint sets
`setSessionCookieNeeded(false)` so the handshake isn't blocked. (This was a real bug — see §17.)

---

## 11. Background jobs (Quartz)

Four scheduled jobs, all simple repeating triggers:

| Job | Interval | Purpose |
|---|---|---|
| **DispatchSweepJob** | 5s | Roll lapsed offers to the next driver; EXPIRE rides past the 120s window |
| **ScheduledRideDispatchJob** | 30s | Promote "book for later" rides to REQUESTED when their time arrives |
| **FleetSimulationJob** | 2s | One movement step for every simulated driver (§12) |
| **MaintenanceNotificationJob** | 24h | Housekeeping / periodic notifications |

**Why Quartz over `@Scheduled`?** Quartz gives named jobs/triggers, configurable intervals via
properties, and a clean path to clustered/persistent scheduling later. The dispatch sweeper in
particular *must* be a background process — it's what makes offer-rollover and expiry happen
without anyone holding an HTTP request open.

---

## 12. The simulated fleet

So the demo is alive without a second device, `FleetSimulationService` ticks every 2s and nudges
each simulated driver:
- **Idle drivers** wander gently around the city centre (keeps the map lively and matching
  reliable).
- A driver **just offered a ride auto-accepts** (stands in for a human tapping Accept), then
  **drives to pickup** (steps ~200m/tick toward it), **auto-starts** with the PIN, **drives to
  drop-off**, and **auto-completes**.
- Crucially, each simulated move calls `moveAndBroadcast()` which both persists the location
  **and** pushes it to `/topic/ride/{id}/driver-location` — so a fleet-driven ride animates on
  the passenger's screen exactly like a real one.

**It yields to real drivers.** `DriverPresenceService` keeps a short-TTL Redis key
(`driver:client-active:{id}`) refreshed on every location push from a real driver dashboard. The
simulator **skips any driver whose key is present**, so a human driver app always wins; when the
dashboard closes, the key lapses and the simulator reclaims the driver. **Payment stays manual** —
the simulator never pays, which is exactly why the earnings-vs-payment distinction (§9) matters.

This is controlled by `FLEET_ENABLED`; turning it off gives a pure manual-driver demo.

---

## 13. Resilience & failure handling

The design principle is **graceful degradation** — a dependency failing should reduce quality,
not cause an outage.

- **OSRM routing → straight-line fallback.** `RoutingService` calls a real OSRM server for road
  distance/ETA, cached in Redis. If OSRM is disabled, unreachable, *or returns a bogus result*
  (a point snapped >5km onto an out-of-region road — a real edge case with a single-region OSRM
  extract), it falls back to a haversine straight-line estimate. **Fares and the map never break.**
- **Redis → PostGIS fallback** for matching, and best-effort writes everywhere (geo cache, surge
  cache, OTP). Redis down = slower, not broken.
- **Payment gateway → circuit breaker.** `ResilientGatewayExecutor` wraps gateway calls with
  **Resilience4j** `@CircuitBreaker` + `@Retry` + `@Bulkhead`. When the breaker opens it
  **fails fast** with `GatewayUnavailableException` instead of piling up threads on a dead
  provider — classic cascading-failure isolation. (There's even a `PaymentOutageSimulator` to
  exercise this path on demand.)
- **Payment correctness:** SERIALIZABLE isolation + `SELECT … FOR UPDATE` row lock on the ride
  serializes concurrent charge/refund attempts; signature verification gates SUCCESS; verify is
  idempotent. No double-charges, no "paid but not recorded."

---

## 14. Design patterns used

| Pattern | Where | Why |
|---|---|---|
| **State machine** | `RideStatus.canTransitionTo` | Make illegal ride transitions impossible |
| **Strategy** | `FareStrategy` (Standard/Surge) | Swappable pricing algorithm |
| **Decorator** | `SurgeFareStrategy` wraps a base strategy | Layer surge on any base fare at runtime |
| **Factory** | `PaymentGatewayFactory` | Resolve the right gateway by provider enum |
| **Observer** | `RideEventPublisher` → `NotificationObserver`, `AuditObserver` | Decouple side-effects (push, audit) from ride logic |
| **Repository** | Spring Data JPA repositories | Persistence abstraction |
| **Cache-aside** | Redis geo / surge / route caches | Read-through with DB fallback |
| **Circuit breaker / Bulkhead / Retry** | `ResilientGatewayExecutor` | Fault isolation around external calls |

The honest version: these were chosen where they *fit*, not to tick boxes. Strategy+Decorator for
fares and Observer for events are the strongest fits; Factory for gateways is reasonable; some
(like the simulated gateways) exist as much to demonstrate the abstraction as to be used.

---

## 15. Frontend architecture

- **React 18 + TypeScript + Vite.** Types in `types/index.ts` **mirror the backend DTOs** exactly
  (kept in sync by hand) — so the API contract is typed end to end.
- **State:** **Zustand** for client/UI state (auth store, ride store with live driver location);
  **React Query** for server state (caching, refetch intervals, e.g. nearby drivers every 5s).
- **Routing & guards:** React Router with a `ProtectedRoute` that gates by auth + role.
- **Maps:** Leaflet + OpenStreetMap tiles, custom `divIcon` markers; the route polyline shows the
  travelled portion faded and the remaining portion solid green; nearby-driver car icons within
  5 km of the pickup.
- **Real-time:** a thin STOMP/SockJS wrapper (`lib/stomp.ts`); hooks `useRideTracking`,
  `useDriverRequests`, `useRideChat` subscribe to topics and feed stores.
- **Origin handling:** `lib/origin.ts` derives API base + WS URL from `VITE_API_ORIGIN`, so the
  same build works locally (proxy) and in production (separate API host).

---

## 16. Deployment

- **Containerized** with Docker; the API image is a multi-stage build.
- **Render**, defined as **infrastructure-as-code** in a single `render.yaml` blueprint that
  provisions four resources in one shot: Postgres+PostGIS, a Redis (Valkey) instance, the Spring
  Boot API (Docker web service with a `/actuator/health` health check), and the React app as a
  static site.
- **Config via env vars:** DB connection composed from `DB_HOST/PORT/NAME/...`, feature flags
  (`FLEET_ENABLED`, `OSRM_ENABLED`, `RAZORPAY_ENABLED`), and secrets (`JWT_SECRET`, Razorpay
  keys) injected at the dashboard, never committed.
- **CI/CD:** push to `master` → Render auto-deploys the changed services.

---

## 17. Challenges & how I solved them

These are real problems hit during the build — the best interview material because they're
specific and show debugging.

1. **Passenger map didn't show the moving car (driver UI did).**
   *Cause:* the simulated fleet updated the DB/Redis location but never **broadcast** to the
   passenger's STOMP topic like the real-driver path did. *Fix:* a `moveAndBroadcast()` helper
   that both persists and pushes to `/topic/ride/{id}/driver-location`.

2. **…and even after that, SockJS wouldn't connect cross-origin.**
   *Cause:* frontend and API are on different origins; SockJS's handshake sent credentials, which
   the browser blocks against `Access-Control-Allow-Origin: *`. The `/ws/info` probe reported
   `cookie_needed:true`. *Fix:* `setSessionCookieNeeded(false)` on the SockJS endpoint +
   `setAllowedOriginPatterns("*")` + `setAllowCredentials(true)` in CORS. Verified `/ws/info`
   then returned `cookie_needed:false` and the WebSocket transport connected.

3. **Deploy failed: API couldn't reach the database.**
   *Cause:* `application-dev.yml` hard-coded `localhost:5433`, and the profile-specific property
   out-ranked the composed `DB_HOST/PORT` URL on Render. *Fix:* compose the JDBC URL from env
   vars in the dev profile too.

4. **Login broke on the deployed frontend ("Unexpected end of JSON input").**
   *Cause:* `VITE_API_ORIGIN` was set from Render's `fromService host`, which returns the
   **internal** service name, not the public hostname — so the SPA hit a dead URL. *Fix:*
   hard-code the public API origin in the blueprint; verified the built bundle contained the
   right URL.

5. **Driver earnings credited before payment.** (See §9.) Separated "ride completed" from
   "payment succeeded" by threading `paymentStatus` through to the Earnings filter.

6. **Currency shown as `$` on ride cards.** A hardcoded `$` in `VehicleSelector` instead of the
   shared `formatCurrency` (which renders ₹). Small, but the kind of consistency bug worth
   catching.

7. **OSRM returning nonsense for out-of-region points.** A single-region OSRM extract snaps
   far-away points onto in-region roads, yielding distance-0 routes. *Fix:* a 5km max-snap
   threshold; beyond it, distrust OSRM and use the straight-line estimate.

8. **Stale Redis geo entries** caused wrong driver assignment after old tabs lingered. Reinforced
   that Redis is a cache and drivers are **evicted** from `drivers:geo` on going offline / on match.

---

## 18. Known limitations & feasibility

Every limitation below is real. What matters in an interview is knowing *which* you can fix and
*why* the rest are blocked — the difference between "I couldn't" and "I chose not to, and here's
the trade-off."

### Fixed (done in the codebase)

- **Unbounded refresh-token table** → a scheduled maintenance job now purges revoked/expired
  tokens (`AuthService.purgeStaleRefreshTokens()` called from the daily Quartz job).
- **Hard-coded wide-open CORS** → CORS and WebSocket origins are now driven by
  `CORS_ALLOWED_ORIGINS` (defaults to `*` for the open demo, but can be pinned to the real
  frontend origin in production without a code change).
- **Earnings credited before payment** → the driver Earnings page only counts rides whose
  `paymentStatus === 'SUCCESS'` (see §9).

### Fixable cheaply, deliberately deferred (no infra, low risk)

- **`otp.expose-code: true`** returns the OTP in the API response. It's already env-configurable
  (`rideswift.otp.expose-code`); it's left **on for the demo** so testers can log in without real
  SMS, and would simply be set `false` in a real deployment.
- **Payment idempotency key** — largely mitigated already: `createOrder` runs SERIALIZABLE with a
  `SELECT … FOR UPDATE` row lock and reuses an existing PENDING row, so same-ride double-submits
  are serialized. A dedicated idempotency key is the belt-and-braces upgrade.
- **Ratings don't influence matching; no cancellation fees** — pure feature work; deferred to keep
  the core dispatch flow stable.

### Fixable, but needs moderate work / real setup

- **Payment webhooks** — verification currently runs on the client redirect after checkout. If the
  user closes the tab post-payment, the charge succeeds at Razorpay but stays PENDING here. The fix
  (a webhook endpoint + reconciliation) is achievable since we have a public URL, but needs
  Razorpay dashboard config. **This is the highest-value real gap.**
- **Nearby-drivers polling → push** — the map polls every 5s; could be a STOMP subscription.
- **Clustered Quartz / distributed rate limiter** — only matter with multiple API replicas, which
  the current single-instance deploy doesn't run.

### Blocked by hard constraints (money / real data / real ops)

| Limitation | Why it can't be fixed here |
|---|---|
| **Free-tier cold start** (~50s after idle) | **Money** — only a paid always-on instance fixes it |
| **OSRM routing quality** (the 5km snap-threshold hack) | **Money + infra** — needs self-hosted multi-region OSRM or a paid routing API; the public demo server is single-region |
| **ML demand predictor** (currently a stub) | **Real data** — can't train demand forecasting without months of real ride history |
| **External STOMP broker for true multi-replica scale** | **Money + no real load** — a managed broker costs money and there's no traffic to justify it |
| **Live fare metering / traffic-aware ETA** | **Real GPS streams + traffic data** a portfolio app doesn't have |

**One-liner for interviews:** *"About three-quarters are code or config I can fix — and I've done
the safe high-value ones (token cleanup, configurable CORS, earnings-after-payment). The rest are
gated by budget, real historical data, or real production load, so I left honest stubs with a clear
upgrade path rather than fake them."*

---

## 19. Improvements & what I'd do next

Be ready to talk about the limits of the current design — it shows maturity.

- **Scale-out correctness:** the STOMP broker is in-memory and the rate limiter is per-instance.
  For multiple API replicas I'd move to an external broker (RabbitMQ/Redis STOMP relay) and a
  Redis-backed distributed rate limiter.
- **Location ingestion at scale:** per-fix DB writes won't hold up at thousands of drivers. I'd
  push fixes through a queue (Kafka) and batch-write, keeping Redis as the hot read path.
- **ETA quality:** swap the public OSRM demo server for a self-hosted, multi-region OSRM (or a
  traffic-aware provider) to remove the snap-threshold hack and improve ETAs.
- **Payments:** add Razorpay **webhooks** (don't rely only on the client redirect to trigger
  verify), idempotency keys on order creation, and automatic reconciliation.
- **Driver payouts/ledger:** a proper double-entry earnings ledger rather than summing completed-
  and-paid rides on read.
- **Observability:** structured tracing (OpenTelemetry), metrics on match latency / dispatch
  success rate / surge, and alerting.
- **Testing:** more integration tests around the dispatch race conditions and payment
  idempotency; load tests on matching.
- **Real driver mobile app** and **push notifications** (FCM/APNs) instead of in-app only.

---

## 20. Rapid-fire interview Q&A

**Q: Walk me through what happens when a passenger books a ride.**
The request hits `RideController` → `RideService.request()`. It computes a fare quote (OSRM route
→ FareRule → surge), saves the ride as REQUESTED, and `offerToNextDriver()` finds the nearest
eligible driver (Redis geo → PostGIS fallback), writes a RideOffer with a 35s expiry, and points
the ride at that driver. A `REQUESTED` event publishes; the Observer pushes a request popup to the
driver and "looking for a driver" to the passenger over STOMP. If the driver doesn't respond, the
5s dispatch sweeper rolls to the next driver; if nobody accepts in 120s the ride EXPIRES.

**Q: How do you find the nearest driver efficiently?**
PostGIS `geography` points with a GIST index. `ST_DWithin` prunes to a radius (index-assisted),
the `<->` KNN operator orders by true distance, and `LIMIT` stops early. In front of that, a Redis
GEO set serves the common case at in-memory speed, with PostGIS as the authoritative fallback.

**Q: Why both Redis and PostGIS for location?**
Writes are constant (moving drivers) and reads are frequent (matching). Redis absorbs that load in
memory; PostGIS stays the durable source of truth and the correctness backstop. Redis writes are
best-effort, so a cache outage degrades to "slower," never "broken."

**Q: How does the live map update?**
STOMP over WebSocket. The driver/fleet reports a position → backend persists + publishes to
`/topic/ride/{id}/driver-location` → the passenger's subscribed hook updates a Zustand store →
Leaflet re-renders the marker. SockJS gives transport fallback for restrictive networks.

**Q: How is surge calculated?**
Per zone: `(demand + predicted) / supply` over a 15-min window, rounded and clamped to the zone's
min/max, cached ~60s in Redis. If > 1, a `SurgeFareStrategy` decorator multiplies the base fare.

**Q: How do you keep payments correct?**
Razorpay order→checkout→verify, where SUCCESS is gated on **server-side HMAC signature
verification** — never the client's claim. The transaction is SERIALIZABLE with a row lock on the
ride, verify is idempotent, and the gateway calls sit behind a circuit breaker so a provider
outage fails fast instead of cascading.

**Q: How do you prevent two passengers getting the same driver?**
Accepting a ride flips the driver to `available=false` and evicts them from the geo-cache, inside a
transaction. A competing accept then fails the eligibility/offer-still-live check and rolls to the
next driver — the database is the arbiter.

**Q: What's the hardest bug you fixed?**
The cross-origin live-tracking failure — two stacked causes: the fleet never broadcast locations,
*and* SockJS's credentialed handshake was blocked by `Allow-Origin: *`. I diagnosed the second by
reading the `/ws/info` probe (`cookie_needed:true`) and fixed it with
`setSessionCookieNeeded(false)` plus credentialed CORS via origin patterns.

**Q: What would you change for production scale?**
External STOMP broker + distributed rate limiter for multi-replica; queue-based batched location
ingestion; self-hosted traffic-aware routing; Razorpay webhooks + idempotency keys; a proper
earnings ledger; and real observability (tracing/metrics/alerts).

**Q: Why JWT access + opaque refresh, and why hash the refresh token?**
Access tokens are stateless and short-lived (no DB hit per request); refresh tokens are long-lived,
so they're stored only as SHA-256 hashes (a DB leak yields nothing usable) and rotated on every
use to detect replay/theft.

**Q: How does the simulated fleet not get in the way of real drivers?**
A real driver dashboard refreshes a short-TTL Redis presence key on every location push; the
simulator skips any driver with that key present. When the dashboard closes the key lapses and the
simulator reclaims the driver. Payment is always manual, so the fleet never settles money.
