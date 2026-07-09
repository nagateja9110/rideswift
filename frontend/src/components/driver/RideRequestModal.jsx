import { useEffect, useRef, useState } from "react";
import { MapPin, Flag, Clock, Navigation, Route } from "lucide-react";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDistance, formatDuration } from "@/lib/utils";

const WINDOW = 30;

export function RideRequestModal({ request, onAccept, onDecline, busy }) {
  const [left, setLeft] = useState(WINDOW);

  // Keep the latest onDecline without making it a dependency — otherwise the parent's
  // 3s poll re-renders recreate the callback and restart the countdown (30→27→30…).
  const onDeclineRef = useRef(onDecline);
  onDeclineRef.current = onDecline;

  // Restart the countdown only when a *new* request arrives (keyed by ride id).
  const rideId = request?.rideId;
  useEffect(() => {
    if (!rideId) return;
    setLeft(WINDOW);
    const id = setInterval(() => {
      setLeft((l) => {
        if (l <= 1) {
          clearInterval(id);
          onDeclineRef.current();
          return 0;
        }
        return l - 1;
      });
    }, 1000);
    return () => clearInterval(id);
  }, [rideId]);

  if (!request) return null;
  const pct = (left / WINDOW) * 100;
  const pickup =
    request.pickupAddress ??
    `${request.pickupLatitude.toFixed(4)}, ${request.pickupLongitude.toFixed(4)}`;
  const dropoff =
    request.dropoffAddress ??
    (request.dropoffLatitude != null && request.dropoffLongitude != null
      ? `${request.dropoffLatitude.toFixed(4)}, ${request.dropoffLongitude.toFixed(4)}`
      : "Drop-off");

  return (
    <Dialog open onClose={onDecline} dismissable={false}>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold">New ride request</h3>
          <span className="flex items-center gap-1 rounded-full bg-warning/15 px-2.5 py-1 text-sm font-semibold text-warning">
            <Clock className="h-3.5 w-3.5" /> {left}s
          </span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full bg-primary transition-all duration-1000 ease-linear"
            style={{ width: `${pct}%` }}
          />
        </div>

        <div className="space-y-3 rounded-lg border p-4">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2 font-medium">
              <Navigation className="h-4 w-4 text-primary" />{" "}
              {request.vehicleType} ride
            </span>
            <span className="text-xl font-bold">
              {formatCurrency(request.estimatedFare)}
            </span>
          </div>

          {(request.distanceKm != null || request.durationMinutes != null) && (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Route className="h-4 w-4" />
              {formatDistance(request.distanceKm)} ·{" "}
              {formatDuration(request.durationMinutes)} trip
            </p>
          )}

          <div className="space-y-1.5 border-t pt-3 text-sm">
            <p className="flex items-start gap-2">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <span className="min-w-0">{pickup}</span>
            </p>
            <p className="flex items-start gap-2">
              <Flag className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
              <span className="min-w-0">{dropoff}</span>
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Button variant="outline" onClick={onDecline} disabled={busy}>
            Decline
          </Button>
          <Button onClick={onAccept} loading={busy}>
            Accept
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
