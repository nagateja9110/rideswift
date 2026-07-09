import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "@/api/endpoints";
import { registerAuthHandlers } from "@/api/client";
import { useAuthStore } from "@/store/authStore";

// Wires the axios interceptors to the auth store: silent refresh on 401,
// hard logout when refresh fails. Mount once near the app root.
export function useAuthBootstrap() {
  const navigate = useNavigate();

  useEffect(() => {
    registerAuthHandlers({
      onAuthFailure: () => {
        useAuthStore.getState().clear();
        navigate("/login");
      },
      refresh: async () => {
        const rt = useAuthStore.getState().refreshToken;
        if (!rt) return null;
        try {
          const auth = await authApi.refresh(rt);
          useAuthStore.getState().setSession(auth);
          return auth.accessToken;
        } catch {
          return null;
        }
      },
    });
  }, [navigate]);
}

export function useLogout() {
  const navigate = useNavigate();
  return async () => {
    const rt = useAuthStore.getState().refreshToken;
    if (rt) {
      try {
        await authApi.logout(rt);
      } catch {
        /* best-effort */
      }
    }
    useAuthStore.getState().clear();
    navigate("/login");
  };
}
