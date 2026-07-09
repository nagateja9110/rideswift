import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  MapPin,
  Flag,
  Loader2,
  Navigation,
  X,
  CreditCard,
  Sparkles,
  Star,
  Phone,
  MessageSquare,
  CalendarClock,
  Frown,
  LocateFixed,
} from "lucide-react";
import {
  fareApi,
  rideApi,
  paymentApi,
  geocodeApi,
  routeApi,
  driverApi,
} from "@/api/endpoints";
import { apiError } from "@/api/client";
import { useRideStore } from "@/store/rideStore";
import { useRideTracking } from "@/hooks/useRideTracking";
import { MapView } from "@/components/common/MapView";
import { ChatPanel } from "@/components/common/ChatPanel";
import { AddressAutocomplete } from "@/components/passenger/AddressAutocomplete";
import { VehicleSelector } from "@/components/passenger/VehicleSelector";
import { RatingModal } from "@/components/passenger/RatingModal";
import { Button } from "@/components/ui/button";
import { RideStatusBadge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/toast";
import { CenteredSpinner } from "@/components/ui/misc";
import {
  formatCurrency,
  formatDistance,
  formatDuration,
  initials,
  cn,
} from "@/lib/utils";

const SAMPLE_TRIPS = [
  {
    label: "Amrita University → Coimbatore Junction",
    pickup: {
      lat: 10.9018,
      lng: 76.9006,
      address: "Amrita Vishwa Vidyapeetham, Ettimadai",
    },
    dropoff: { lat: 10.9989, lng: 76.9661, address: "Coimbatore Junction" },
  },
  {
    label: "Gandhipuram → Coimbatore Airport",
    pickup: { lat: 11.0183, lng: 76.9725, address: "Gandhipuram, Coimbatore" },
    dropoff: {
      lat: 11.0296,
      lng: 77.0434,
      address: "Coimbatore International Airport",
    },
  },
];

const ACTIVE_STATUSES = ["SCHEDULED", "REQUESTED", "MATCHED", "IN_PROGRESS"];

export function BookRidePage() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { currentRide, driverLocation, setRide, clearRide } = useRideStore();

  const [pickup, setPickup] = useState(null);
  const [dropoff, setDropoff] = useState(null);
  const [pickMode, setPickMode] = useState("pickup");
  const [locating, setLocating] = useState(false);
  const [vehicleType, setVehicleType] = useState("ECONOMY");
  const [scheduleAt, setScheduleAt] = useState(""); // datetime-local; '' = ride now
  const [requesting, setRequesting] = useState(false);
  const [paying, setPaying] = useState(false);
  const [tip, setTip] = useState(0);
  const [showRating, setShowRating] = useState(false);

  // Resume an in-flight ride on mount.
  const { data: history, isLoading: loadingHistory } = useQuery({
    queryKey: ["rides", "history"],
    queryFn: rideApi.history,
  });
  // Evaluate resume exactly once, the first time history loads. Re-running on every
  // currentRide→null transition would re-hydrate a just-cancelled ride from stale
  // history cache, leaving its map markers stuck until a page refresh.
  const resumedRef = useRef(false);
  useEffect(() => {
    if (resumedRef.current || !history) return;
    resumedRef.current = true;
    if (!currentRide) {
      const active = history.find((r) => ACTIVE_STATUSES.includes(r.status));
      if (active) setRide(active);
    }
  }, [history, currentRide, setRide]);

  // Live tracking + polling fallback for the active ride.
  const trackingActive =
    !!currentRide && ACTIVE_STATUSES.includes(currentRide.status);
  useRideTracking(trackingActive ? currentRide.id : null);
  const { data: liveRide } = useQuery({
    queryKey: ["ride", currentRide?.id],
    queryFn: () => rideApi.get(currentRide.id),
    enabled: trackingActive,
    refetchInterval: 3000,
    refetchOnWindowFocus: true,
  });
  // Sync polled ride into the store via an effect — never call setState during render.
  // Guard against an in-flight poll resolving after cancel/clear with stale ride data.
  useEffect(() => {
    if (liveRide && currentRide?.id === liveRide.id) setRide(liveRide);
  }, [liveRide, currentRide?.id, setRide]);

  // Road route geometry for the map polyline — for the active ride if any, else the
  // pickup→drop-off the user is composing.
  const routeEnds = currentRide
    ? {
        pLat: currentRide.pickupLatitude,
        pLng: currentRide.pickupLongitude,
        dLat: currentRide.dropoffLatitude,
        dLng: currentRide.dropoffLongitude,
      }
    : pickup && dropoff
      ? {
          pLat: pickup.lat,
          pLng: pickup.lng,
          dLat: dropoff.lat,
          dLng: dropoff.lng,
        }
      : null;
  const { data: routeData } = useQuery({
    queryKey: ["route", routeEnds],
    enabled: !!routeEnds,
    queryFn: () =>
      routeApi.get(
        routeEnds.pLat,
        routeEnds.pLng,
        routeEnds.dLat,
        routeEnds.dLng,
      ),
  });
  const routeGeometry = routeData?.geometry;

  // Fare estimates for all vehicle types when both points set.
  const { data: estimates } = useQuery({
    queryKey: ["fare", "estimate", pickup, dropoff],
    enabled: !!pickup && !!dropoff && !currentRide,
    queryFn: async () => {
      const types = ["ECONOMY", "PREMIUM", "XL"];
      const results = await Promise.all(
        types.map((t) =>
          fareApi.estimate({
            pickupLatitude: pickup.lat,
            pickupLongitude: pickup.lng,
            dropoffLatitude: dropoff.lat,
            dropoffLongitude: dropoff.lng,
            vehicleType: t,
          }),
        ),
      );
      return Object.fromEntries(types.map((t, i) => [t, results[i]]));
    },
  });

  const fareMap = useMemo(() => {
    if (!estimates) return undefined;
    return Object.fromEntries(
      Object.entries(estimates).map(([t, e]) => [
        t,
        { fare: e.estimatedFare, surge: e.surgeMultiplier },
      ]),
    );
  }, [estimates]);

  // Ambient "cars near you": available drivers within 5 km of the pickup (or the
  // map's default centre before a pickup is chosen). Polled so they appear to move.
  const NEARBY_RADIUS_KM = 5;
  const focusLat = pickup?.lat ?? 11.0168;
  const focusLng = pickup?.lng ?? 76.9558;
  const { data: nearbyDrivers } = useQuery({
    queryKey: ["drivers", "visible", focusLat.toFixed(3), focusLng.toFixed(3)],
    enabled: !currentRide,
    refetchInterval: 5000,
    queryFn: () =>
      driverApi.visibleNearby(focusLat, focusLng, NEARBY_RADIUS_KM),
  });

  const onMapClick = (lat, lng) => {
    if (currentRide) return;
    // Show coordinates immediately, then upgrade to a real address from reverse geocoding.
    const target = pickMode;
    const fallback = {
      lat,
      lng,
      address: `${lat.toFixed(4)}, ${lng.toFixed(4)}`,
    };
    if (target === "pickup") {
      setPickup(fallback);
      setPickMode("dropoff");
    } else {
      setDropoff(fallback);
    }
    geocodeApi.reverse(lat, lng).then((r) => {
      if (!r) return;
      const resolved = { lat, lng, address: r.displayName };
      if (target === "pickup") setPickup(resolved);
      else setDropoff(resolved);
    });
  };

  // Set pickup from the device's current GPS position (reverse-geocoded to an address).
  const useCurrentLocation = () => {
    if (!("geolocation" in navigator)) {
      toast("This device has no location support", "error");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const lat = p.coords.latitude;
        const lng = p.coords.longitude;
        setPickup({
          lat,
          lng,
          address: `Current location (${lat.toFixed(4)}, ${lng.toFixed(4)})`,
        });
        setPickMode("dropoff");
        geocodeApi.reverse(lat, lng).then((r) => {
          if (r) setPickup({ lat, lng, address: r.displayName });
        });
        setLocating(false);
      },
      (err) => {
        setLocating(false);
        toast(
          err.code === err.PERMISSION_DENIED
            ? "Location blocked — allow location for this site in your browser"
            : "Couldn't get your location, please try again",
          "error",
        );
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 10000 },
    );
  };

  const useSample = (s) => {
    setPickup(s.pickup);
    setDropoff(s.dropoff);
    setPickMode("pickup");
  };

  const reset = () => {
    setPickup(null);
    setDropoff(null);
    setPickMode("pickup");
    setScheduleAt("");
  };

  const requestRide = async () => {
    if (!pickup || !dropoff) return;
    setRequesting(true);
    try {
      const scheduledAt = scheduleAt
        ? new Date(scheduleAt).toISOString()
        : undefined;
      const ride = await rideApi.request({
        pickupLatitude: pickup.lat,
        pickupLongitude: pickup.lng,
        dropoffLatitude: dropoff.lat,
        dropoffLongitude: dropoff.lng,
        pickupAddress: pickup.address,
        dropoffAddress: dropoff.address,
        vehicleType,
        scheduledAt,
      });
      setRide(ride);
      qc.invalidateQueries({ queryKey: ["rides", "history"] });
      toast(
        ride.status === "SCHEDULED"
          ? "Ride scheduled!"
          : ride.driverId
            ? "Driver matched!"
            : "Searching for a nearby driver…",
        "success",
      );
    } catch (e) {
      toast(apiError(e), "error");
    } finally {
      setRequesting(false);
    }
  };

  const cancelRide = async () => {
    if (!currentRide) return;
    try {
      await rideApi.cancel(currentRide.id);
      toast("Ride cancelled", "info");
      clearRide();
      reset();
      qc.invalidateQueries({ queryKey: ["rides", "history"] });
    } catch (e) {
      toast(apiError(e), "error");
    }
  };

  const pay = async () => {
    if (!currentRide) return;
    const Razorpay = window.Razorpay;
    if (!Razorpay) {
      toast("Payment library failed to load — check your connection", "error");
      return;
    }
    setPaying(true);
    try {
      // 1) Ask the backend to open a Razorpay order for this ride.
      const order = await paymentApi.createRazorpayOrder({
        rideId: currentRide.id,
        tipAmount: tip || undefined,
      });
      // 2) Open Razorpay Checkout; the customer pays card/UPI in Razorpay's popup.
      const rzp = new Razorpay({
        key: order.keyId,
        amount: order.amountPaise,
        currency: order.currency,
        name: order.name,
        description: order.description,
        order_id: order.orderId,
        prefill: {
          name: order.prefillName,
          email: order.prefillEmail,
          contact: order.prefillContact,
        },
        theme: { color: "#16a34a" },
        // 3) On success, verify the signature server-side before trusting it.
        handler: async (resp) => {
          try {
            const payment = await paymentApi.verifyRazorpay({
              paymentId: order.paymentId,
              razorpayOrderId: resp.razorpay_order_id,
              razorpayPaymentId: resp.razorpay_payment_id,
              razorpaySignature: resp.razorpay_signature,
            });
            if (payment.status === "SUCCESS") {
              toast("Payment successful", "success");
              setShowRating(true);
            } else {
              toast(`Payment ${payment.status.toLowerCase()}`, "error");
            }
          } catch (e) {
            toast(apiError(e), "error");
          } finally {
            setPaying(false);
          }
        },
        modal: { ondismiss: () => setPaying(false) },
      });
      rzp.on("payment.failed", (resp) => {
        toast(
          `Payment failed: ${resp?.error?.description ?? "try again"}`,
          "error",
        );
        setPaying(false);
      });
      rzp.open();
    } catch (e) {
      toast(apiError(e), "error");
      setPaying(false);
    }
  };

  const finishRating = () => {
    setShowRating(false);
    setTip(0);
    clearRide();
    reset();
    qc.invalidateQueries({ queryKey: ["rides", "history"] });
    toast("Thanks for riding with RideSwift!", "success");
  };

  // --- Map markers ---
  const markers = [];
  if (currentRide) {
    markers.push({
      lat: currentRide.pickupLatitude,
      lng: currentRide.pickupLongitude,
      kind: "pickup",
    });
    markers.push({
      lat: currentRide.dropoffLatitude,
      lng: currentRide.dropoffLongitude,
      kind: "dropoff",
    });
    if (driverLocation)
      markers.push({
        lat: driverLocation.lat,
        lng: driverLocation.lng,
        kind: "driver",
      });
  } else {
    if (pickup)
      markers.push({ lat: pickup.lat, lng: pickup.lng, kind: "pickup" });
    if (dropoff)
      markers.push({ lat: dropoff.lat, lng: dropoff.lng, kind: "dropoff" });
    // Ambient cars within 5 km — decorative, excluded from the map's fit bounds.
    for (const d of nearbyDrivers ?? []) {
      if (d.distanceKm <= NEARBY_RADIUS_KM) {
        markers.push({ lat: d.latitude, lng: d.longitude, kind: "car" });
      }
    }
  }

  const isActive = currentRide && ACTIVE_STATUSES.includes(currentRide.status);
  const isCompleted = currentRide?.status === "COMPLETED";
  const isExpired = currentRide?.status === "EXPIRED";

  return (
    <div className="flex h-full flex-col-reverse lg:flex-row">
      {/* Panel */}
      <div className="w-full overflow-y-auto border-t bg-card p-6 lg:w-[420px] lg:border-r lg:border-t-0">
        {loadingHistory && !currentRide ? (
          <CenteredSpinner label="Loading…" />
        ) : isActive ? (
          <ActivePanel
            ride={currentRide}
            onCancel={cancelRide}
            hasDriverLoc={!!driverLocation}
          />
        ) : isCompleted ? (
          <PaymentPanel
            ride={currentRide}
            paying={paying}
            onPay={pay}
            tip={tip}
            setTip={setTip}
          />
        ) : isExpired ? (
          <ExpiredPanel
            onTryAgain={() => {
              clearRide();
              reset();
            }}
          />
        ) : (
          <div className="space-y-5">
            <div>
              <h1 className="text-2xl font-bold">Book a ride</h1>
              <p className="text-sm text-muted-foreground">
                Search an address or tap the map.
              </p>
              {(nearbyDrivers?.length ?? 0) > 0 && (
                <p className="mt-1 flex items-center gap-1.5 text-xs font-medium text-primary">
                  <span className="relative flex h-2 w-2">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-60" />
                    <span className="relative inline-flex h-2 w-2 rounded-full bg-primary" />
                  </span>
                  {nearbyDrivers.length}{" "}
                  {nearbyDrivers.length === 1 ? "driver" : "drivers"} within{" "}
                  {NEARBY_RADIUS_KM} km
                </p>
              )}
            </div>

            <div className="space-y-2">
              <AddressAutocomplete
                active={pickMode === "pickup"}
                icon={<MapPin className="h-4 w-4 shrink-0 text-primary" />}
                label="Pickup"
                placeholder="Search pickup address…"
                value={pickup?.address}
                onFocus={() => setPickMode("pickup")}
                onSelect={(p) => {
                  setPickup(p);
                  setPickMode("dropoff");
                }}
              />

              <AddressAutocomplete
                active={pickMode === "dropoff"}
                icon={<Flag className="h-4 w-4 shrink-0 text-destructive" />}
                label="Drop-off"
                placeholder="Search drop-off address…"
                value={dropoff?.address}
                onFocus={() => setPickMode("dropoff")}
                onSelect={(p) => setDropoff(p)}
              />

              <button
                type="button"
                onClick={useCurrentLocation}
                disabled={locating}
                className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline disabled:opacity-60"
              >
                {locating ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <LocateFixed className="h-4 w-4" />
                )}
                {locating ? "Locating…" : "Use my current location as pickup"}
              </button>
            </div>

            <div className="flex flex-wrap gap-2">
              {SAMPLE_TRIPS.map((s) => (
                <button
                  key={s.label}
                  onClick={() => useSample(s)}
                  className="inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs text-muted-foreground hover:border-primary hover:text-primary"
                >
                  <Sparkles className="h-3 w-3" /> {s.label}
                </button>
              ))}
              {(pickup || dropoff) && (
                <button
                  onClick={reset}
                  className="inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
                >
                  <X className="h-3 w-3" /> Clear
                </button>
              )}
            </div>

            {pickup && dropoff && (
              <div className="space-y-3 animate-fade-in">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Choose a ride</span>
                  {estimates ? (
                    <span className="text-muted-foreground">
                      {formatDistance(estimates.ECONOMY.distanceKm)} ·{" "}
                      {formatDuration(estimates.ECONOMY.durationMinutes)}
                    </span>
                  ) : (
                    <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                  )}
                </div>
                <VehicleSelector
                  value={vehicleType}
                  onChange={setVehicleType}
                  fares={fareMap}
                />

                <div className="rounded-lg border p-3">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-sm font-medium">
                      <CalendarClock className="h-4 w-4 text-primary" />{" "}
                      Schedule for later
                    </span>
                    {scheduleAt && (
                      <button
                        onClick={() => setScheduleAt("")}
                        className="text-xs text-muted-foreground hover:text-foreground"
                      >
                        Clear
                      </button>
                    )}
                  </div>
                  <input
                    type="datetime-local"
                    value={scheduleAt}
                    min={new Date(
                      Date.now() - new Date().getTimezoneOffset() * 60000,
                    )
                      .toISOString()
                      .slice(0, 16)}
                    onChange={(e) => setScheduleAt(e.target.value)}
                    className="mt-2 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  />
                </div>

                <Button
                  className="w-full"
                  size="lg"
                  loading={requesting}
                  onClick={requestRide}
                  disabled={!estimates}
                >
                  {!estimates
                    ? "Estimating fare…"
                    : scheduleAt
                      ? `Schedule ${vehicleType} · ${formatCurrency(estimates[vehicleType].estimatedFare)}`
                      : `Confirm ${vehicleType} · ${formatCurrency(estimates[vehicleType].estimatedFare)}`}
                </Button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Map */}
      <div className="relative h-[45vh] flex-1 lg:h-full">
        <MapView
          markers={markers}
          route={routeGeometry}
          onClick={onMapClick}
          center={[11.0168, 76.9558]}
          zoom={12}
        />
        {!currentRide && (
          <div className="pointer-events-none absolute left-1/2 top-4 z-[400] -translate-x-1/2 rounded-full bg-card/90 px-4 py-1.5 text-xs font-medium shadow-md backdrop-blur">
            Tap map to set {pickMode === "pickup" ? "pickup 📍" : "drop-off 🏁"}
          </div>
        )}
      </div>

      <RatingModal open={showRating} onSubmit={finishRating} />
    </div>
  );
}

function ActivePanel({ ride, onCancel, hasDriverLoc }) {
  const steps = ["REQUESTED", "MATCHED", "IN_PROGRESS"];
  const idx = steps.indexOf(ride.status);
  const labels = {
    REQUESTED: "Finding your driver",
    MATCHED: "Driver on the way",
    IN_PROGRESS: "On your trip",
  };

  // Driver contact details become available once a driver has accepted (MATCHED+).
  const matched = ride.status === "MATCHED" || ride.status === "IN_PROGRESS";
  const [chatOpen, setChatOpen] = useState(false);
  const { data: participants } = useQuery({
    queryKey: ["ride", ride.id, "participants"],
    queryFn: () => rideApi.participants(ride.id),
    enabled: matched && !!ride.driverId,
  });
  const driver = participants?.driver;

  // Scheduled (book-for-later) rides get a dedicated view until the sweeper dispatches them.
  if (ride.status === "SCHEDULED") {
    const when = ride.scheduledAt ? new Date(ride.scheduledAt) : null;
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold">Ride scheduled</h1>
          <RideStatusBadge status={ride.status} />
        </div>
        <div className="flex items-center gap-3 rounded-lg bg-primary/10 p-4 text-sm">
          <CalendarClock className="h-6 w-6 shrink-0 text-primary" />
          <div>
            <p className="font-semibold">
              {when
                ? when.toLocaleString([], {
                    weekday: "short",
                    month: "short",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })
                : "Upcoming"}
            </p>
            <p className="text-muted-foreground">
              We'll match you with a driver automatically.
            </p>
          </div>
        </div>
        <div className="space-y-1.5 text-sm">
          <p className="flex items-start gap-2">
            <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
            <span>{ride.pickupAddress || "Pickup"}</span>
          </p>
          <p className="flex items-start gap-2">
            <Flag className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
            <span>{ride.dropoffAddress || "Drop-off"}</span>
          </p>
        </div>
        <Button variant="outline" className="w-full" onClick={onCancel}>
          Cancel scheduled ride
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">{labels[ride.status]}</h1>
        <RideStatusBadge status={ride.status} />
      </div>

      {ride.status === "REQUESTED" && (
        <div className="flex items-center gap-3 rounded-lg bg-warning/10 p-4 text-sm text-warning">
          <Loader2 className="h-5 w-5 animate-spin" />
          Matching you with the nearest available driver…
        </div>
      )}

      {/* progress */}
      <div className="space-y-3">
        {steps.map((s, i) => (
          <div key={s} className="flex items-center gap-3">
            <div
              className={cn(
                "flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold",
                i <= idx
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground",
              )}
            >
              {i < idx ? "✓" : i + 1}
            </div>
            <span
              className={cn(
                "text-sm",
                i <= idx ? "font-medium" : "text-muted-foreground",
              )}
            >
              {labels[s]}
            </span>
          </div>
        ))}
      </div>

      {/* Driver details card — shown once a driver has accepted */}
      {matched && driver && (
        <div className="space-y-3 rounded-lg border p-4 animate-fade-in">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary/15 text-sm font-bold text-primary">
              {initials(driver.name)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{driver.name}</p>
              <p className="flex items-center gap-1 text-xs text-muted-foreground">
                <Star className="h-3 w-3 fill-warning text-warning" />
                {Number(driver.rating).toFixed(2)}
                {hasDriverLoc && " · live location"}
              </p>
            </div>
            <span className="font-semibold">
              {formatCurrency(ride.estimatedFare)}
            </span>
          </div>

          {driver.vehicle && (
            <div className="flex items-center justify-between rounded-md bg-muted/50 px-3 py-2 text-sm">
              <span className="flex items-center gap-2">
                <Navigation className="h-4 w-4 text-primary" />
                {driver.vehicle.make} {driver.vehicle.model}
              </span>
              <span className="rounded border border-border bg-background px-2 py-0.5 font-mono text-xs font-semibold tracking-wider">
                {driver.vehicle.licensePlate}
              </span>
            </div>
          )}

          <div className="grid grid-cols-2 gap-2">
            <Button variant="outline" onClick={() => setChatOpen(true)}>
              <MessageSquare className="h-4 w-4" /> Message
            </Button>
            <a
              href={`tel:${driver.phone}`}
              title={`Call ${driver.phone}`}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-input bg-transparent px-4 text-sm font-medium hover:bg-accent hover:text-accent-foreground"
            >
              <Phone className="h-4 w-4" /> Call
            </a>
          </div>
          {driver.phone && (
            <p className="text-center text-xs text-muted-foreground">
              Driver phone:{" "}
              <span className="font-medium text-foreground">
                {driver.phone}
              </span>
            </p>
          )}
        </div>
      )}

      {/* Pickup PIN — passenger reads this out to verify the driver at pickup */}
      {ride.status === "MATCHED" && ride.pickupPin && (
        <div className="rounded-lg border border-primary/30 bg-primary/5 p-4 text-center animate-fade-in">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Pickup PIN
          </p>
          <div className="mt-2 flex justify-center gap-2">
            {ride.pickupPin.split("").map((d, i) => (
              <span
                key={i}
                className="flex h-11 w-9 items-center justify-center rounded-md border bg-card text-2xl font-bold tabular-nums"
              >
                {d}
              </span>
            ))}
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            Share with your driver to start the trip.
          </p>
        </div>
      )}

      {/* Fallback vehicle/fare line before a driver is shown */}
      {!(matched && driver) && (
        <div className="rounded-lg border p-4">
          <div className="flex items-center gap-3">
            <Navigation className="h-5 w-5 text-primary" />
            <div className="flex-1">
              <p className="text-sm font-medium">{ride.vehicleType} ride</p>
              <p className="text-xs text-muted-foreground">
                {ride.driverId ? "Driver assigned" : "No driver yet"}
                {hasDriverLoc && " · live location"}
              </p>
            </div>
            <span className="font-semibold">
              {formatCurrency(ride.estimatedFare)}
            </span>
          </div>
        </div>
      )}

      <div className="space-y-1.5 text-sm">
        <p className="flex items-start gap-2">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span>{ride.pickupAddress || "Pickup"}</span>
        </p>
        <p className="flex items-start gap-2">
          <Flag className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
          <span>{ride.dropoffAddress || "Drop-off"}</span>
        </p>
      </div>

      {ride.status !== "IN_PROGRESS" && (
        <Button variant="outline" className="w-full" onClick={onCancel}>
          Cancel ride
        </Button>
      )}

      {driver && (
        <ChatPanel
          rideId={ride.id}
          open={chatOpen}
          onClose={() => setChatOpen(false)}
          title={`Chat with ${driver.name.split(" ")[0]}`}
        />
      )}
    </div>
  );
}

function PaymentPanel({ ride, paying, onPay, tip, setTip }) {
  const fare = ride.actualFare ?? ride.estimatedFare ?? 0;
  const total = fare + tip;
  // Preset tips: a flat ₹20 plus 15% / 20% of the fare.
  const presets = [
    { label: "None", value: 0 },
    { label: "₹20", value: 20 },
    { label: "15%", value: Math.round(fare * 0.15 * 100) / 100 },
    { label: "20%", value: Math.round(fare * 0.2 * 100) / 100 },
  ];
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold">Trip complete 🎉</h1>
        <p className="text-sm text-muted-foreground">Add a tip, then pay.</p>
      </div>

      <div className="rounded-lg border p-4">
        <Row label="Distance" value={formatDistance(ride.distanceKm)} />
        <Row label="Duration" value={formatDuration(ride.durationMinutes)} />
        <Row label="Vehicle" value={ride.vehicleType} />
        <div className="my-3 h-px bg-border" />
        <Row label="Fare" value={formatCurrency(fare)} />
        {tip > 0 && <Row label="Tip" value={formatCurrency(tip)} />}
        <div className="mt-2 flex items-center justify-between">
          <span className="font-semibold">Total</span>
          <span className="text-2xl font-bold">{formatCurrency(total)}</span>
        </div>
      </div>

      {/* Tip your driver */}
      <div className="space-y-2">
        <label className="text-sm font-medium">Add a tip for your driver</label>
        <div className="grid grid-cols-4 gap-2">
          {presets.map((p) => (
            <button
              key={p.label}
              onClick={() => setTip(p.value)}
              className={cn(
                "rounded-lg border py-2 text-sm font-medium transition-colors",
                tip === p.value
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-border hover:border-primary",
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-1.5">
        <label className="text-sm font-medium">Payment method</label>
        <div className="flex items-center gap-2 rounded-lg border px-3 py-2.5 text-sm">
          <CreditCard className="h-4 w-4 text-primary" />
          <span className="font-medium">Razorpay</span>
          <span className="text-muted-foreground">
            — UPI, cards, netbanking & wallets
          </span>
        </div>
      </div>

      <Button className="w-full" size="lg" loading={paying} onClick={onPay}>
        <CreditCard className="h-4 w-4" /> Pay {formatCurrency(total)}
      </Button>
    </div>
  );
}

function ExpiredPanel({ onTryAgain }) {
  return (
    <div className="flex h-full flex-col items-center justify-center space-y-4 py-12 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-warning/15">
        <Frown className="h-7 w-7 text-warning" />
      </div>
      <div>
        <h1 className="text-xl font-bold">No drivers available</h1>
        <p className="mx-auto mt-1 max-w-xs text-sm text-muted-foreground">
          We contacted the nearby drivers but none could take your trip right
          now. Please try again in a moment.
        </p>
      </div>
      <Button onClick={onTryAgain}>Try again</Button>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between py-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
