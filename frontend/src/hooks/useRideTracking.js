import { useEffect } from "react";
import { subscribeTopic } from "@/lib/stomp";
import { useRideStore } from "@/store/rideStore";

// Subscribes a passenger to live driver-location + ride-status pushes for one ride.
export function useRideTracking(rideId) {
  const updateDriverLocation = useRideStore((s) => s.updateDriverLocation);
  const updateStatus = useRideStore((s) => s.updateStatus);

  useEffect(() => {
    if (!rideId) return;
    const unsubLoc = subscribeTopic(
      `/topic/ride/${rideId}/driver-location`,
      (msg) => updateDriverLocation({ lat: msg.latitude, lng: msg.longitude }),
    );
    const unsubStatus = subscribeTopic(
      `/topic/ride/${rideId}/status`,
      (msg) => msg.status && updateStatus(msg.status),
    );
    return () => {
      unsubLoc();
      unsubStatus();
    };
  }, [rideId, updateDriverLocation, updateStatus]);
}
