import { useEffect } from 'react';
import { subscribeTopic } from '@/lib/stomp';
import type { RideRequestMessage } from '@/types';

// Subscribes an online driver to incoming ride-request pushes.
export function useDriverRequests(
  driverId: string | null | undefined,
  enabled: boolean,
  onRequest: (req: RideRequestMessage) => void
) {
  useEffect(() => {
    if (!driverId || !enabled) return;
    const unsub = subscribeTopic<RideRequestMessage>(
      `/topic/driver/${driverId}/requests`,
      onRequest
    );
    return () => unsub();
  }, [driverId, enabled, onRequest]);
}
