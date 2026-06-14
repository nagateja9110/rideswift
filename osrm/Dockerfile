# Self-contained OSRM routing image for cloud hosts that don't support volumes
# (Render, Koyeb, Fly, etc.). Bakes the prebuilt Coimbatore+Ettimadai graph
# (~71 MB) into the image and serves it. Build the data first with ./prepare.sh.
#
#   docker build -t rideswift-osrm ./osrm
#
FROM ghcr.io/project-osrm/osrm-backend:latest
WORKDIR /data
# Copy the prebuilt MLD dataset (run osrm/prepare.sh before building this image).
COPY data/coimbatore.osrm* /data/
EXPOSE 5000
CMD ["osrm-routed", "--algorithm", "mld", "/data/coimbatore.osrm"]
