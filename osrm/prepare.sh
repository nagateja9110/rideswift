#!/usr/bin/env bash
#
# Builds a small Coimbatore + Ettimadai OSRM dataset for self-hosted road routing.
#
# Why a bounding box: the full Southern-India graph is ~530 MB of PBF and several
# GB once processed — it won't fit a free-tier container. RideSwift's demo data is
# all in and around Coimbatore (incl. Amrita University, Ettimadai), so we cut a
# tight box around that area. The result is ~71 MB and serves from ~47 MB RAM —
# far under 256 MB, which fits free tiers (Fly.io, etc.).
#
# Run this ONCE before `docker compose up osrm`. Requires Docker.
#
set -euo pipefail
cd "$(dirname "$0")/data"

# Geofabrik's Southern-zone extract contains Tamil Nadu (and thus Coimbatore).
PBF_URL="https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf"
SRC="southern-zone-latest.osm.pbf"
# left,bottom,right,top (minLng,minLat,maxLng,maxLat) — Coimbatore metro + the
# airport (NE) and Amrita Vishwa Vidyapeetham, Ettimadai (~20 km SW).
BBOX="76.83,10.83,77.10,11.12"
OSRM_IMG="ghcr.io/project-osrm/osrm-backend:latest"
OSMIUM_IMG="stefda/osmium-tool:latest"

echo "==> 1/4 Downloading Southern-India extract (if missing)…"
[ -s "$SRC" ] || curl -L --retry 5 --retry-delay 5 --retry-all-errors -o "$SRC" "$PBF_URL"

echo "==> 2/4 Cutting Coimbatore + Ettimadai bounding box…"
docker run --rm -v "$PWD:/data" "$OSMIUM_IMG" \
  osmium extract -b "$BBOX" "/data/$SRC" -o /data/coimbatore.osm.pbf --overwrite

echo "==> 3/4 OSRM extract + partition + customize (MLD pipeline)…"
docker run --rm -v "$PWD:/data" "$OSRM_IMG" osrm-extract -p /opt/car.lua /data/coimbatore.osm.pbf
docker run --rm -v "$PWD:/data" "$OSRM_IMG" osrm-partition /data/coimbatore.osrm
docker run --rm -v "$PWD:/data" "$OSRM_IMG" osrm-customize /data/coimbatore.osrm

echo "==> 4/4 Done. Dataset at osrm/data/coimbatore.osrm"
echo "    Start it with:  docker compose up -d osrm"
echo "    Or standalone:  docker run --rm -p 5000:5000 -v \"$PWD:/data\" $OSRM_IMG \\"
echo "                      osrm-routed --algorithm mld /data/coimbatore.osrm"
