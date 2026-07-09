import { create } from "zustand";
import { persist } from "zustand/middleware";
import { setAccessToken } from "@/api/client";

export const useAuthStore = create()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      role: null,
      setSession: (auth) => {
        setAccessToken(auth.accessToken);
        set({
          user: auth.user,
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          isAuthenticated: true,
          role: auth.user.role,
        });
      },
      setUser: (user) => set({ user, role: user.role }),
      setAccessToken: (token) => {
        setAccessToken(token);
        set({ accessToken: token });
      },
      clear: () => {
        setAccessToken(null);
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          role: null,
        });
      },
    }),
    {
      name: "rideswift-auth",
      storage: {
        getItem: (name) => {
          const v = sessionStorage.getItem(name);
          return v ? JSON.parse(v) : null;
        },
        setItem: (name, value) =>
          sessionStorage.setItem(name, JSON.stringify(value)),
        removeItem: (name) => sessionStorage.removeItem(name),
      },
      // Re-prime the axios token holder after rehydration.
      onRehydrateStorage: () => (state) => {
        if (state?.accessToken) setAccessToken(state.accessToken);
      },
    },
  ),
);
