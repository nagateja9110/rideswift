// Mirrors the Spring Boot DTOs (com.rideswift.dto). Keep in sync with the backend.

export type Role = 'PASSENGER' | 'DRIVER' | 'ADMIN';
export type VehicleType = 'ECONOMY' | 'PREMIUM' | 'XL';
export type RideStatus = 'SCHEDULED' | 'REQUESTED' | 'MATCHED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';
export type Gateway = 'STRIPE' | 'PAYPAL' | 'RAZORPAY';
export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type NotificationType = 'SMS' | 'PUSH' | 'EMAIL';

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  role: Role;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Ride {
  id: string;
  passengerId: string;
  driverId: string | null;
  vehicleType: VehicleType;
  status: RideStatus;
  pickupLatitude: number;
  pickupLongitude: number;
  dropoffLatitude: number;
  dropoffLongitude: number;
  pickupAddress: string | null;
  dropoffAddress: string | null;
  estimatedFare: number | null;
  actualFare: number | null;
  distanceKm: number | null;
  durationMinutes: number | null;
  requestedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  pickupPin: string | null; // 4-digit; only present in the passenger's view
  scheduledAt: string | null; // set for book-for-later rides
  paymentStatus: PaymentStatus | null;
}

export interface FareEstimate {
  vehicleType: VehicleType;
  distanceKm: number;
  durationMinutes: number;
  estimatedFare: number;
  currency: string;
  strategy: string;
  surgeMultiplier: number;
}

export interface FareRule {
  id: string;
  vehicleType: VehicleType;
  baseFare: number;
  perKmRate: number;
  perMinuteRate: number;
  surgeMultiplier: number;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface SurgeInfo {
  zoneId: string | null;
  zoneName: string | null;
  surgeMultiplier: number;
  demand: number;
  supply: number;
}

export interface Driver {
  id: string;
  userId: string;
  licenseNumber: string;
  rating: number;
  verificationStatus: VerificationStatus;
  available: boolean;
  latitude: number | null;
  longitude: number | null;
}

export interface NearbyDriver {
  driverId: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
}

export interface GeocodeResult {
  name: string;
  displayName: string;
  lat: number;
  lng: number;
}

export interface RouteInfo {
  distanceKm: number;
  durationMinutes: number;
  geometry: [number, number][]; // [lat, lng] pairs
  source: 'osrm' | 'haversine';
}

export interface RideMessage {
  id: string;
  rideId: string;
  senderId: string;
  senderName: string;
  content: string;
  sentAt: string;
}

export interface RideParticipants {
  passenger: { userId: string; name: string; phone: string };
  driver: {
    driverId: string;
    userId: string;
    name: string;
    phone: string;
    rating: number;
    vehicle: {
      make: string;
      model: string;
      year: number;
      licensePlate: string;
      vehicleType: VehicleType;
    } | null;
  } | null;
}

export interface Vehicle {
  id: string;
  driverId: string;
  make: string;
  model: string;
  year: number;
  licensePlate: string;
  vehicleType: VehicleType;
}

export interface Payment {
  id: string;
  rideId: string;
  amount: number;
  tipAmount: number;
  currency: string;
  gateway: Gateway;
  gatewayTransactionId: string | null;
  status: PaymentStatus;
  processedAt: string | null;
}

export interface Notification {
  id: string;
  message: string;
  type: NotificationType;
  read: boolean;
  createdAt: string;
}

export interface Dashboard {
  activeRides: number;
  completedRides: number;
  cancelledRides: number;
  totalRevenue: number;
  passengerCount: number;
  driverCount: number;
  pendingDriverVerifications: number;
}

export interface RideAuditEntry {
  revision: number;
  status: RideStatus;
  estimatedFare: number | null;
  actualFare: number | null;
  changedBy: string | null;
  changedAt: string;
}

// WebSocket payloads (mirror com.rideswift.websocket.*)
export interface DriverLocationMessage {
  rideId: string;
  driverId: string;
  latitude: number;
  longitude: number;
  timestamp: number;
}
export interface RideStatusMessage {
  rideId: string;
  status: RideStatus;
  driverId: string | null;
  message: string | null;
}
export interface RideRequestMessage {
  rideId: string;
  driverId: string;
  vehicleType: VehicleType;
  pickupLatitude: number;
  pickupLongitude: number;
  dropoffLatitude: number | null;
  dropoffLongitude: number | null;
  pickupAddress: string | null;
  dropoffAddress: string | null;
  estimatedFare: number | null;
  distanceKm: number | null;
  durationMinutes: number | null;
}
