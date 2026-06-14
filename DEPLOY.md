# Deploying RideSwift for free

RideSwift is a 5-piece stack. Each maps to a free tier:

| Piece | Free host | Notes |
|---|---|---|
| Frontend (React/Vite) | **Vercel** | Static build. Free, instant. |
| Backend (Spring Boot) | **Render** (Docker web service) | 512 MB free; sleeps after ~15 min idle (cold start ~50 s). |
| Postgres + PostGIS | **Supabase** | PostGIS built in. |
| Redis | **Upstash** | Serverless, TLS + password. |
| OSRM routing | **Render** (Docker) *or skip* | 71 MB Coimbatore graph. Optional — without it, fares/map use a straight-line estimate. |

External services already wired (just set env vars): **Razorpay** (test keys), **Firebase** phone auth, **Nominatim** geocoding (public).

> Heads-up: free tiers **sleep when idle**, so the first request after a nap takes ~30–60 s. Fine for a demo/portfolio.

---

## Step 0 — Push to GitHub
Render and Vercel deploy from a Git repo.

```bash
cd /Users/nagateja/uber/rideswift
git init && git add -A && git commit -m "RideSwift"
# create an empty repo on github.com, then:
git remote add origin https://github.com/<you>/rideswift.git
git branch -M main && git push -u origin main
```
`.env` and `osrm/data/*` are gitignored — secrets and the big PBF stay local.

## Step 1 — Postgres + PostGIS (Supabase)
1. supabase.com → New project. Save the **database password**.
2. SQL Editor → run: `create extension if not exists postgis;`
3. Project Settings → Database → copy host/port/db/user.
4. You'll use:
   - `DB_URL = jdbc:postgresql://<host>:5432/postgres?sslmode=require`
   - `DB_USERNAME = postgres` · `DB_PASSWORD = <your password>`

## Step 2 — Redis (Upstash)
1. upstash.com → Create database (region near your backend).
2. Copy: **endpoint host**, **port**, **password**.
3. You'll use: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD=<password>`, `REDIS_USERNAME=default`, `REDIS_SSL=true`.

## Step 3 — Backend (Render)
1. render.com → New → **Web Service** → connect your GitHub repo.
2. **Root Directory:** `backend` · **Runtime:** Docker (uses `backend/Dockerfile`).
3. Environment variables:
   ```
   SPRING_PROFILES_ACTIVE = prod
   RIDESWIFT_SEED_ENABLED = true          # seeds drivers/accounts/fleet — needed for the demo
   DB_URL       = jdbc:postgresql://<supabase-host>:5432/postgres?sslmode=require
   DB_USERNAME  = postgres
   DB_PASSWORD  = <supabase password>
   REDIS_HOST   = <upstash host>
   REDIS_PORT   = <upstash port>
   REDIS_USERNAME = default
   REDIS_PASSWORD = <upstash password>
   REDIS_SSL    = true
   JWT_SECRET   = <base64 256-bit secret>      # openssl rand -base64 32
   FIREBASE_PROJECT_ID = rideswift-2d539
   RAZORPAY_KEY_ID     = <your rzp_test_ key id>
   RAZORPAY_KEY_SECRET = <your rzp_test_ secret>
   OTP_EXPOSE_CODE = false
   OSRM_URL = https://<osrm-service>.onrender.com   # from Step 4, or omit for straight-line
   ```
4. Deploy. Note the URL, e.g. `https://rideswift-api.onrender.com`.
   (Render injects `PORT`; the app honors it automatically.)

## Step 4 — OSRM routing (Render, optional)
Skip to launch faster — the app auto-falls back to straight-line fares/routes. For real road routing:
1. Build the data once: `./osrm/prepare.sh` → creates `osrm/data/coimbatore.osrm*`.
2. Commit them (~71 MB): `git add -f osrm/data/coimbatore.osrm* && git commit -m "osrm data" && git push`
3. Render → New → **Web Service** → same repo → **Root Directory:** `osrm` (uses `osrm/Dockerfile`).
4. Deploy → put its URL in the backend's `OSRM_URL` (Step 3) → redeploy backend.

## Step 5 — Frontend (Vercel)
1. vercel.com → New Project → import repo → **Root Directory:** `frontend`.
2. Preset: **Vite** (build `npm run build`, output `dist`).
3. Environment variables:
   ```
   VITE_API_BASE = https://<render-backend>.onrender.com/api/v1
   VITE_WS_URL   = https://<render-backend>.onrender.com/ws
   ```
4. Deploy → note the URL, e.g. `https://rideswift.vercel.app`.

## Step 6 — Connect Firebase to the live domain
Phone-OTP reCAPTCHA only runs on authorized domains:
- Firebase Console → Authentication → **Settings → Authorized domains** → **Add** → `rideswift.vercel.app`.

## Step 7 — Razorpay
Test keys work on any domain — nothing to change. Real money needs `rzp_live_` keys after Razorpay business KYC.

---

## Env var reference (backend)
| Var | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `RIDESWIFT_SEED_ENABLED` | `true` to seed demo data |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Supabase Postgres+PostGIS |
| `REDIS_HOST/PORT/USERNAME/PASSWORD/SSL` | Upstash Redis |
| `JWT_SECRET` | base64 256-bit signing key |
| `OSRM_URL` | OSRM service URL (omit → haversine fallback) |
| `FIREBASE_PROJECT_ID` | `rideswift-2d539` |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | Razorpay test keys |
| `OTP_EXPOSE_CODE` | `false` in prod |
| `MATCH_RADIUS_METERS` | optional, default 15000 (Coimbatore↔Ettimadai) |

## Local full stack
```bash
./osrm/prepare.sh            # optional: real routing
docker compose up --build    # postgres + redis + osrm + backend + frontend
```

## Demo accounts (seeded)
`alice@rideswift.io` (passenger), `mike@rideswift.io` (driver), `admin@rideswift.io` — password `password123`. Or use the demo-login buttons / phone OTP.
