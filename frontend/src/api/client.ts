import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { apiBase } from '@/lib/origin';

export const api = axios.create({
  baseURL: apiBase,
  headers: { 'Content-Type': 'application/json' },
});

// --- token plumbing (kept out of React so interceptors can reach it) ---
let accessToken: string | null = null;
let onAuthFailure: (() => void) | null = null;
let refreshHandler: (() => Promise<string | null>) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}
export function registerAuthHandlers(handlers: {
  onAuthFailure: () => void;
  refresh: () => Promise<string | null>;
}) {
  onAuthFailure = handlers.onAuthFailure;
  refreshHandler = handlers.refresh;
}

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// On 401, try a single refresh then replay the original request.
let refreshing: Promise<string | null> | null = null;

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    const isAuthCall = original?.url?.includes('/auth/');
    if (error.response?.status === 401 && original && !original._retry && !isAuthCall && refreshHandler) {
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
  }
);

export function apiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    return data?.message || data?.error || error.message || 'Request failed';
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}
