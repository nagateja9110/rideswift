import { api } from './client';
import type {
  AuthResponse,
  Dashboard,
  Driver,
  FareEstimate,
  FareRule,
  NearbyDriver,
  Notification,
  Payment,
  Ride,
  RideAuditEntry,
  Role,
  SurgeInfo,
  User,
  Vehicle,
  VehicleType,
  Gateway,
  RideMessage,
  RideParticipants,
  GeocodeResult,
  RouteInfo,
} from '@/types';

// ---- Auth ----
export const authApi = {
  register: (body: { name: string; email: string; phone: string; password: string; role: Role }) =>
    api.post<AuthResponse>('/auth/register', body).then((r) => r.data),
  login: (body: { email: string; password: string }) =>
    api.post<AuthResponse>('/auth/login', body).then((r) => r.data),
  requestOtp: (phone: string) =>
    api
      .post<{ phone: string; message: string; devCode: string | null }>('/auth/otp/request', { phone })
      .then((r) => r.data),
  verifyOtp: (body: { phone: string; code: string; name?: string }) =>
    api.post<AuthResponse>('/auth/otp/verify', body).then((r) => r.data),
  firebaseLogin: (idToken: string, name?: string) =>
    api.post<AuthResponse>('/auth/firebase', { idToken, name }).then((r) => r.data),
  refresh: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh', { refreshToken }).then((r) => r.data),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),
};

// ---- Users ----
export const userApi = {
  me: () => api.get<User>('/users/me').then((r) => r.data),
  update: (id: string, body: { name?: string; phone?: string }) =>
    api.patch<User>(`/users/${id}`, body).then((r) => r.data),
  all: () => api.get<User[]>('/users').then((r) => r.data),
};

// ---- Fare ----
export const fareApi = {
  estimate: (body: {
    pickupLatitude: number;
    pickupLongitude: number;
    dropoffLatitude: number;
    dropoffLongitude: number;
    vehicleType: VehicleType;
  }) => api.post<FareEstimate>('/fare/estimate', body).then((r) => r.data),
  surge: (lat: number, lng: number) =>
    api.get<SurgeInfo>('/fare/surge', { params: { lat, lng } }).then((r) => r.data),
  rules: () => api.get<FareRule[]>('/fare/rules').then((r) => r.data),
  updateRule: (
    id: string,
    body: { baseFare: number; perKmRate: number; perMinuteRate: number; surgeMultiplier: number }
  ) => api.put<FareRule>(`/fare/rules/${id}`, body).then((r) => r.data),
};

// ---- Rides ----
export const rideApi = {
  request: (body: {
    pickupLatitude: number;
    pickupLongitude: number;
    dropoffLatitude: number;
    dropoffLongitude: number;
    pickupAddress?: string;
    dropoffAddress?: string;
    vehicleType: VehicleType;
    scheduledAt?: string; // ISO instant; omit for ride-now
  }) => api.post<Ride>('/rides/request', body).then((r) => r.data),
  get: (id: string) => api.get<Ride>(`/rides/${id}`).then((r) => r.data),
  history: () => api.get<Ride[]>('/rides/history').then((r) => r.data),
  accept: (id: string) => api.patch<Ride>(`/rides/${id}/accept`).then((r) => r.data),
  decline: (id: string) => api.patch<Ride>(`/rides/${id}/decline`).then((r) => r.data),
  start: (id: string, pin?: string) =>
    api.patch<Ride>(`/rides/${id}/start`, pin ? { pin } : undefined).then((r) => r.data),
  complete: (id: string) => api.patch<Ride>(`/rides/${id}/complete`).then((r) => r.data),
  cancel: (id: string) => api.patch<Ride>(`/rides/${id}/cancel`).then((r) => r.data),
  participants: (id: string) =>
    api.get<RideParticipants>(`/rides/${id}/participants`).then((r) => r.data),
  messages: (id: string) => api.get<RideMessage[]>(`/rides/${id}/messages`).then((r) => r.data),
  sendMessage: (id: string, content: string) =>
    api.post<RideMessage>(`/rides/${id}/messages`, { content }).then((r) => r.data),
};

// ---- Drivers ----
export const driverApi = {
  register: (licenseNumber: string) =>
    api.post<Driver>('/drivers/register', { licenseNumber }).then((r) => r.data),
  me: () => api.get<Driver>('/drivers/me').then((r) => r.data),
  all: () => api.get<Driver[]>('/drivers').then((r) => r.data),
  updateLocation: (latitude: number, longitude: number) =>
    api.patch<Driver>('/drivers/location', { latitude, longitude }).then((r) => r.data),
  setAvailability: (available: boolean) =>
    api.patch<Driver>('/drivers/availability', { available }).then((r) => r.data),
  nearby: (lat: number, lng: number, vehicleType: VehicleType) =>
    api
      .get<NearbyDriver[]>('/drivers/nearby', { params: { lat, lng, vehicleType } })
      .then((r) => r.data),
  // Ambient "cars near you" layer — all available drivers within radiusKm (default 5).
  visibleNearby: (lat: number, lng: number, radiusKm = 5) =>
    api
      .get<NearbyDriver[]>('/drivers/nearby/visible', { params: { lat, lng, radiusKm } })
      .then((r) => r.data),
  verify: (id: string) => api.patch<Driver>(`/drivers/${id}/verify`).then((r) => r.data),
  reject: (id: string) => api.patch<Driver>(`/drivers/${id}/reject`).then((r) => r.data),
  vehicles: (driverId: string) =>
    api.get<Vehicle[]>(`/drivers/${driverId}/vehicles`).then((r) => r.data),
  addVehicle: (
    driverId: string,
    body: { make: string; model: string; year: number; licensePlate: string; vehicleType: VehicleType }
  ) => api.post<Vehicle>(`/drivers/${driverId}/vehicles`, body).then((r) => r.data),
};

// ---- Geocoding (Nominatim proxy) ----
export const geocodeApi = {
  search: (q: string, limit?: number) =>
    api
      .get<GeocodeResult[]>('/geocode/search', { params: { q, limit } })
      .then((r) => r.data),
  reverse: (lat: number, lng: number): Promise<GeocodeResult | null> =>
    api
      .get<GeocodeResult | ''>('/geocode/reverse', { params: { lat, lng } })
      .then((r) => (r.data ? (r.data as GeocodeResult) : null)) // 204 → empty body
      .catch(() => null),
};

// ---- Routing (OSRM proxy) ----
export const routeApi = {
  get: (pickupLat: number, pickupLng: number, dropoffLat: number, dropoffLng: number) =>
    api
      .get<RouteInfo>('/route', { params: { pickupLat, pickupLng, dropoffLat, dropoffLng } })
      .then((r) => r.data),
};

// ---- Payments ----
export type RazorpayOrder = {
  paymentId: string;
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
  name: string;
  description: string;
  prefillName: string;
  prefillEmail: string;
  prefillContact: string;
};

export const paymentApi = {
  initiate: (body: { rideId: string; gateway: Gateway; currency?: string; tipAmount?: number }) =>
    api.post<Payment>('/payments/initiate', body).then((r) => r.data),
  get: (id: string) => api.get<Payment>(`/payments/${id}`).then((r) => r.data),
  // Real Razorpay flow: create an order, open Checkout, then verify the callback.
  createRazorpayOrder: (body: { rideId: string; tipAmount?: number }) =>
    api.post<RazorpayOrder>('/payments/razorpay/order', body).then((r) => r.data),
  verifyRazorpay: (body: {
    paymentId: string;
    razorpayOrderId: string;
    razorpayPaymentId: string;
    razorpaySignature: string;
  }) => api.post<Payment>('/payments/razorpay/verify', body).then((r) => r.data),
};

// ---- Notifications ----
export const notificationApi = {
  list: () => api.get<Notification[]>('/notifications').then((r) => r.data),
  markRead: (id: string) =>
    api.patch<Notification>(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.patch<{ updated: number }>('/notifications/read-all').then((r) => r.data),
};

// ---- Admin ----
export const adminApi = {
  dashboard: () => api.get<Dashboard>('/admin/dashboard').then((r) => r.data),
  pendingDrivers: () => api.get<Driver[]>('/admin/drivers/pending').then((r) => r.data),
  adjustFare: (rideId: string, actualFare: number) =>
    api.patch<Ride>(`/admin/rides/${rideId}/fare`, { actualFare }).then((r) => r.data),
  broadcast: (body: { message: string; role?: Role | null }) =>
    api.post<{ recipients: number }>('/admin/notifications/broadcast', body).then((r) => r.data),
  rideAudit: (rideId: string) =>
    api.get<RideAuditEntry[]>(`/admin/audit/rides/${rideId}`).then((r) => r.data),
  setPaymentOutage: (enabled: boolean) =>
    api.post<{ paymentOutage: boolean }>('/admin/payments/outage', null, { params: { enabled } }).then((r) => r.data),
};
