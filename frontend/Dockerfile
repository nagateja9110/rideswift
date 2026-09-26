# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
# Vite inlines VITE_* at build time, so a cross-origin deploy (frontend and backend
# as separate hosts, e.g. Azure Container Apps) needs this passed as a build arg —
# it can't be set at container runtime like a normal env var.
ARG VITE_API_ORIGIN=""
ENV VITE_API_ORIGIN=$VITE_API_ORIGIN
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
CMD ["nginx", "-g", "daemon off;"]
