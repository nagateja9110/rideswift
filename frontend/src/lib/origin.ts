// Resolves where the API/WebSocket live, for both same-origin (dev / nginx-proxied)
// and cross-origin deploys (e.g. a static frontend + a separate API host on Render).
//
// VITE_API_ORIGIN may be a bare host ("rideswift-api.onrender.com") or a full URL;
// it is normalized to an absolute origin. When unset, paths stay same-origin so the
// dev proxy / nginx handles them. Explicit VITE_API_BASE / VITE_WS_URL still win.
const raw = (import.meta.env.VITE_API_ORIGIN as string | undefined)?.trim();
const API_ORIGIN = raw
  ? (/^https?:\/\//.test(raw) ? raw.replace(/\/+$/, '') : `https://${raw}`)
  : '';

export const apiBase = (import.meta.env.VITE_API_BASE as string | undefined)
  || (API_ORIGIN ? `${API_ORIGIN}/api/v1` : '/api/v1');

export const wsUrl = (import.meta.env.VITE_WS_URL as string | undefined)
  || (API_ORIGIN ? `${API_ORIGIN}/ws` : '/ws');
