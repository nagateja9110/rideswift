import { api } from "./client";

// ---- Auth ----
export const authApi = {
  register: (body) => api.post("/auth/register", body).then((r) => r.data),
  login: (body) => api.post("/auth/login", body).then((r) => r.data),
  requestOtp: (phone) =>
    api.post("/auth/otp/request", { phone }).then((r) => r.data),
  verifyOtp: (body) => api.post("/auth/otp/verify", body).then((r) => r.data),
  firebaseLogin: (idToken, name) =>
    api.post("/auth/firebase", { idToken, name }).then((r) => r.data),
  refresh: (refreshToken) =>
    api.post("/auth/refresh", { refreshToken }).then((r) => r.data),
  logout: (refreshToken) => api.post("/auth/logout", { refreshToken }),
};

// ---- Users ----
export const userApi = {
  me: () => api.get("/users/me").then((r) => r.data),
  update: (id, body) => api.patch(`/users/${id}`, body).then((r) => r.data),
  all: () => api.get("/users").then((r) => r.data),
};

// ---- Fare ----
export const fareApi = {
  estimate: (body) => api.post("/fare/estimate", body).then((r) => r.data),
  surge: (lat, lng) =>
    api.get("/fare/surge", { params: { lat, lng } }).then((r) => r.data),
  rules: () => api.get("/fare/rules").then((r) => r.data),
  updateRule: (id, body) =>
    api.put(`/fare/rules/${id}`, body).then((r) => r.data),
};

// ---- Rides ----
export const rideApi = {
  request: (body) => api.post("/rides/request", body).then((r) => r.data),
  get: (id) => api.get(`/rides/${id}`).then((r) => r.data),
  history: () => api.get("/rides/history").then((r) => r.data),
  accept: (id) => api.patch(`/rides/${id}/accept`).then((r) => r.data),
  decline: (id) => api.patch(`/rides/${id}/decline`).then((r) => r.data),
  start: (id, pin) =>
    api
      .patch(`/rides/${id}/start`, pin ? { pin } : undefined)
      .then((r) => r.data),
  complete: (id) => api.patch(`/rides/${id}/complete`).then((r) => r.data),
  cancel: (id) => api.patch(`/rides/${id}/cancel`).then((r) => r.data),
  participants: (id) =>
    api.get(`/rides/${id}/participants`).then((r) => r.data),
  messages: (id) => api.get(`/rides/${id}/messages`).then((r) => r.data),
  sendMessage: (id, content) =>
    api.post(`/rides/${id}/messages`, { content }).then((r) => r.data),
};

// ---- Drivers ----
export const driverApi = {
  register: (licenseNumber) =>
    api.post("/drivers/register", { licenseNumber }).then((r) => r.data),
  me: () => api.get("/drivers/me").then((r) => r.data),
  all: () => api.get("/drivers").then((r) => r.data),
  updateLocation: (latitude, longitude) =>
    api.patch("/drivers/location", { latitude, longitude }).then((r) => r.data),
  setAvailability: (available) =>
    api.patch("/drivers/availability", { available }).then((r) => r.data),
  nearby: (lat, lng, vehicleType) =>
    api
      .get("/drivers/nearby", { params: { lat, lng, vehicleType } })
      .then((r) => r.data),
  // Ambient "cars near you" layer — all available drivers within radiusKm (default 5).
  visibleNearby: (lat, lng, radiusKm = 5) =>
    api
      .get("/drivers/nearby/visible", { params: { lat, lng, radiusKm } })
      .then((r) => r.data),
  verify: (id) => api.patch(`/drivers/${id}/verify`).then((r) => r.data),
  reject: (id) => api.patch(`/drivers/${id}/reject`).then((r) => r.data),
  vehicles: (driverId) =>
    api.get(`/drivers/${driverId}/vehicles`).then((r) => r.data),
  addVehicle: (driverId, body) =>
    api.post(`/drivers/${driverId}/vehicles`, body).then((r) => r.data),
};

// ---- Geocoding (Nominatim proxy) ----
export const geocodeApi = {
  search: (q, limit) =>
    api.get("/geocode/search", { params: { q, limit } }).then((r) => r.data),
  reverse: (lat, lng) =>
    api
      .get("/geocode/reverse", { params: { lat, lng } })
      .then((r) => (r.data ? r.data : null)) // 204 → empty body
      .catch(() => null),
};

// ---- Routing (OSRM proxy) ----
export const routeApi = {
  get: (pickupLat, pickupLng, dropoffLat, dropoffLng) =>
    api
      .get("/route", {
        params: { pickupLat, pickupLng, dropoffLat, dropoffLng },
      })
      .then((r) => r.data),
};

// ---- Payments ----

export const paymentApi = {
  initiate: (body) => api.post("/payments/initiate", body).then((r) => r.data),
  get: (id) => api.get(`/payments/${id}`).then((r) => r.data),
  // Real Razorpay flow: create an order, open Checkout, then verify the callback.
  createRazorpayOrder: (body) =>
    api.post("/payments/razorpay/order", body).then((r) => r.data),
  verifyRazorpay: (body) =>
    api.post("/payments/razorpay/verify", body).then((r) => r.data),
};

// ---- Notifications ----
export const notificationApi = {
  list: () => api.get("/notifications").then((r) => r.data),
  markRead: (id) => api.patch(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.patch("/notifications/read-all").then((r) => r.data),
};

// ---- Admin ----
export const adminApi = {
  dashboard: () => api.get("/admin/dashboard").then((r) => r.data),
  pendingDrivers: () => api.get("/admin/drivers/pending").then((r) => r.data),
  adjustFare: (rideId, actualFare) =>
    api
      .patch(`/admin/rides/${rideId}/fare`, { actualFare })
      .then((r) => r.data),
  broadcast: (body) =>
    api.post("/admin/notifications/broadcast", body).then((r) => r.data),
  rideAudit: (rideId) =>
    api.get(`/admin/audit/rides/${rideId}`).then((r) => r.data),
  setPaymentOutage: (enabled) =>
    api
      .post("/admin/payments/outage", null, { params: { enabled } })
      .then((r) => r.data),
};
