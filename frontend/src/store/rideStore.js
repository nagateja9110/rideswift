import { create } from "zustand";

export const useRideStore = create((set) => ({
  currentRide: null,
  driverLocation: null,
  setRide: (ride) => set({ currentRide: ride }),
  updateStatus: (status) =>
    set((s) =>
      s.currentRide ? { currentRide: { ...s.currentRide, status } } : s,
    ),
  updateDriverLocation: (loc) => set({ driverLocation: loc }),
  clearRide: () => set({ currentRide: null, driverLocation: null }),
}));
