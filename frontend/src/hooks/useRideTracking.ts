import { useEffect } from 'react';
import { subscribeTopic } from '@/lib/stomp';
import { useRideStore } from '@/store/rideStore';
import type { DriverLocationMessage, RideStatusMessage } from '@/types';

// Subscribes a passenger to live driver-location + ride-status pushes for one ride.
export function useRideTracking(rideId: string | null | undefined) {
  const updateDriverLocation = useRideStore((s) => s.updateDriverLocation);
  const updateStatus = useRideStore((s) => s.updateStatus);

  useEffect(() => {
    if (!rideId) return;
    const unsubLoc = subscribeTopic<DriverLocationMessage>(
      `/topic/ride/${rideId}/driver-location`,
      (msg) => updateDriverLocation({ lat: msg.latitude, lng: msg.longitude })
    );
    const unsubStatus = subscribeTopic<RideStatusMessage>(
      `/topic/ride/${rideId}/status`,
      (msg) => msg.status && updateStatus(msg.status)
    );
    return () => {
      unsubLoc();
      unsubStatus();
    };
  }, [rideId, updateDriverLocation, updateStatus]);
}
