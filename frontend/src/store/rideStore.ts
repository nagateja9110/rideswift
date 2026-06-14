import { create } from 'zustand';
import type { Ride, RideStatus } from '@/types';

export interface LatLng {
  lat: number;
  lng: number;
}

interface RideState {
  currentRide: Ride | null;
  driverLocation: LatLng | null;
  setRide: (ride: Ride | null) => void;
  updateStatus: (status: RideStatus) => void;
  updateDriverLocation: (loc: LatLng) => void;
  clearRide: () => void;
}

export const useRideStore = create<RideState>((set) => ({
  currentRide: null,
  driverLocation: null,
  setRide: (ride) => set({ currentRide: ride }),
  updateStatus: (status) =>
    set((s) => (s.currentRide ? { currentRide: { ...s.currentRide, status } } : s)),
  updateDriverLocation: (loc) => set({ driverLocation: loc }),
  clearRide: () => set({ currentRide: null, driverLocation: null }),
}));
