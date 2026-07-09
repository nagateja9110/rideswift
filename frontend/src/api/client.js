import axios from "axios";
import { apiBase } from "@/lib/origin";

export const api = axios.create({
  baseURL: apiBase,
  headers: { "Content-Type": "application/json" },
});

// --- token plumbing (kept out of React so interceptors can reach it) ---
let accessToken = null;
let onAuthFailure = null;
let refreshHandler = null;

export function setAccessToken(token) {
  accessToken = token;
}
export function registerAuthHandlers(handlers) {
  onAuthFailure = handlers.onAuthFailure;
  refreshHandler = handlers.refresh;
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// On 401, try a single refresh then replay the original request.
let refreshing = null;

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const isAuthCall = original?.url?.includes("/auth/");
    if (
      error.response?.status === 401 &&
      original &&
      !original._retry &&
      !isAuthCall &&
      refreshHandler
    ) {
      original._retry = true;
      try {
        refreshing = refreshing ?? refreshHandler();
        const newToken = await refreshing;
        refreshing = null;
        if (newToken) {
          original.headers.Authorization = `Bearer ${newToken}`;
          return api(original);
        }
      } catch {
        refreshing = null;
      }
      onAuthFailure?.();
    }
    return Promise.reject(error);
  },
);

export function apiError(error) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;
    return data?.message || data?.error || error.message || "Request failed";
  }
  return error instanceof Error ? error.message : "Something went wrong";
}
