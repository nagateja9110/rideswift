import { useEffect } from "react";
import { subscribeTopic } from "@/lib/stomp";

// Subscribes an online driver to incoming ride-request pushes.
export function useDriverRequests(driverId, enabled, onRequest) {
  useEffect(() => {
    if (!driverId || !enabled) return;
    const unsub = subscribeTopic(
      `/topic/driver/${driverId}/requests`,
      onRequest,
    );
    return () => unsub();
  }, [driverId, enabled, onRequest]);
}
