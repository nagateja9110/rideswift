import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Power, Navigation, MapPin, Flag, ShieldAlert, Clock, Loader2, MessageSquare, Phone, User, Satellite, Joystick } from 'lucide-react';
import { driverApi, rideApi, routeApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { useDriverRequests } from '@/hooks/useDriverRequests';
import { MapView, type MapMarker } from '@/components/common/MapView';
import { ChatPanel } from '@/components/common/ChatPanel';
import { RideRequestModal } from '@/components/driver/RideRequestModal';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { VerificationBadge, RideStatusBadge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/toast';
import { CenteredSpinner } from '@/components/ui/misc';
import { formatCurrency, formatDistance, formatDuration, initials, cn } from '@/lib/utils';
import type { Driver, Ride, RideRequestMessage } from '@/types';

const BASE = { lat: 11.0168, lng: 76.9558 }; // Coimbatore base for the demo driver

export function DriverDashboardPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const qc = useQueryClient();

  const [driver, setDriver] = useState<Driver | null>(null);
  const [loading, setLoading] = useState(true);
  const [online, setOnline] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [request, setRequest] = useState<RideRequestMessage | null>(null);
  const [acting, setActing] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [pinOpen, setPinOpen] = useState(false);
  const [pinInput, setPinInput] = useState('');
  const [pinError, setPinError] = useState('');
  // 'sim' = demo motion toward pickup/drop-off (default, so the NYC demo works
  // anywhere); 'live' = real device GPS via navigator.geolocation.
  const [gpsMode, setGpsMode] = useState<'sim' | 'live'>('sim');

  const posRef = useRef({ ...BASE });
  const [pos, setPos] = useState({ ...BASE }); // mirrors posRef for the live map marker

  // Load driver profile (redirect to onboarding if none).
  useEffect(() => {
    driverApi
      .me()
      .then((d) => {
        setDriver(d);
        setOnline(d.available);
        if (d.latitude && d.longitude) {
          posRef.current = { lat: d.latitude, lng: d.longitude };
          setPos({ lat: d.latitude, lng: d.longitude });
        }
      })
      .catch(() => navigate('/driver/onboarding'))
      .finally(() => setLoading(false));
  }, [navigate]);

  // Active ride for this driver (from history).
  const { data: history } = useQuery({
    queryKey: ['rides', 'history'],
    queryFn: rideApi.history,
    refetchInterval: 3000,
    enabled: !!driver,
  });
  const activeRide: Ride | undefined = history?.find(
    (r) => r.driverId === driver?.id && (r.status === 'MATCHED' || r.status === 'IN_PROGRESS')
  );
  // A driver on a trip is "working" even if not available for *new* rides (available
  // is false during a trip, so on refresh `online` reads false — but they're on a trip).
  const working = online || !!activeRide;

  // Passenger contact details for the active trip.
  const { data: participants } = useQuery({
    queryKey: ['ride', activeRide?.id, 'participants'],
    queryFn: () => rideApi.participants(activeRide!.id),
    enabled: !!activeRide,
  });
  const passenger = participants?.passenger;

  // Road route for the current leg: driver→pickup (MATCHED) or pickup→drop-off
  // (IN_PROGRESS). Drives both the map polyline and the simulated car's path.
  const [routePath, setRoutePath] = useState<Array<[number, number]>>([]);
  const segKeyRef = useRef('');
  const segIdxRef = useRef(0);
  useEffect(() => {
    if (!activeRide) {
      setRoutePath([]);
      segKeyRef.current = '';
      return;
    }
    const key = `${activeRide.id}:${activeRide.status}`;
    if (segKeyRef.current === key) return; // already loaded this leg
    segKeyRef.current = key;
    segIdxRef.current = 0;
    const matched = activeRide.status === 'MATCHED';
    const from = matched ? posRef.current : { lat: activeRide.pickupLatitude, lng: activeRide.pickupLongitude };
    const to = matched
      ? { lat: activeRide.pickupLatitude, lng: activeRide.pickupLongitude }
      : { lat: activeRide.dropoffLatitude, lng: activeRide.dropoffLongitude };
    routeApi
      .get(from.lat, from.lng, to.lat, to.lng)
      .then((r) => setRoutePath(r.geometry))
      .catch(() => setRoutePath([[from.lat, from.lng], [to.lat, to.lng]]));
  }, [activeRide?.id, activeRide?.status]);

  // Resilient incoming-request detection: a REQUESTED ride assigned to me,
  // surfaced via the 3s poll so it works even if the WebSocket push was missed.
  // Tracks rides already acted on (accepted/declined) so a stale poll can't reopen them.
  const handledRef = useRef<Set<string>>(new Set());
  const incomingRide = history?.find(
    (r) => r.driverId === driver?.id && r.status === 'REQUESTED' && !handledRef.current.has(r.id)
  );
  useEffect(() => {
    if (incomingRide && !activeRide && !request) {
      setRequest({
        rideId: incomingRide.id,
        driverId: driver!.id,
        vehicleType: incomingRide.vehicleType,
        pickupLatitude: incomingRide.pickupLatitude,
        pickupLongitude: incomingRide.pickupLongitude,
        dropoffLatitude: incomingRide.dropoffLatitude,
        dropoffLongitude: incomingRide.dropoffLongitude,
        pickupAddress: incomingRide.pickupAddress,
        dropoffAddress: incomingRide.dropoffAddress,
        estimatedFare: incomingRide.estimatedFare,
        distanceKm: incomingRide.distanceKm,
        durationMinutes: incomingRide.durationMinutes,
      });
    }
  }, [incomingRide, activeRide, request, driver]);

  // Location broadcast loop. In 'live' mode, stream the real device GPS via
  // navigator.geolocation; in 'sim' mode, drift toward pickup/drop-off (demo).
  const publishLocation = useCallback((lat: number, lng: number) => {
    posRef.current = { lat, lng };
    setPos({ lat, lng });
    driverApi.updateLocation(lat, lng).catch(() => {});
  }, []);

  useEffect(() => {
    if (!working || !driver) return;

    if (gpsMode === 'live') {
      if (!('geolocation' in navigator)) {
        toast('This device has no GPS — using demo motion', 'error');
        setGpsMode('sim');
        return;
      }
      const watchId = navigator.geolocation.watchPosition(
        (p) => publishLocation(p.coords.latitude, p.coords.longitude),
        (err) => {
          toast(
            err.code === err.PERMISSION_DENIED
              ? 'Location blocked — allow location for this site in your browser, then tap Live GPS again'
              : "Couldn't get a GPS fix — using demo motion",
            'error',
          );
          setGpsMode('sim');
        },
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 }
      );
      return () => navigator.geolocation.clearWatch(watchId);
    }

    // Simulated motion.
    const tick = () => {
      // On a trip: drive the car step-by-step along the real road route.
      if (activeRide) {
        if (routePath.length >= 2) {
          const step = Math.max(1, Math.ceil(routePath.length / 25)); // ~25 ticks to arrive
          segIdxRef.current = Math.min(segIdxRef.current + step, routePath.length - 1);
          const [lat, lng] = routePath[segIdxRef.current];
          publishLocation(lat, lng);
          return;
        }
        // Route not loaded yet — ease toward the leg's endpoint.
        const target =
          activeRide.status === 'IN_PROGRESS'
            ? { lat: activeRide.dropoffLatitude, lng: activeRide.dropoffLongitude }
            : { lat: activeRide.pickupLatitude, lng: activeRide.pickupLongitude };
        const p = posRef.current;
        publishLocation(p.lat + (target.lat - p.lat) * 0.2, p.lng + (target.lng - p.lng) * 0.2);
        return;
      }
      // Idle (online, no trip): gentle jitter near the Manhattan base.
      const p = posRef.current;
      publishLocation(
        p.lat + (BASE.lat - p.lat) * 0.05 + (Math.random() - 0.5) * 0.0006,
        p.lng + (BASE.lng - p.lng) * 0.05 + (Math.random() - 0.5) * 0.0006
      );
    };
    tick();
    const id = setInterval(tick, 1500);
    return () => clearInterval(id);
  }, [working, driver, activeRide, gpsMode, routePath, publishLocation, toast]);

  const onRequest = useCallback(
    (req: RideRequestMessage) => {
      if (activeRide) return; // already busy
      setRequest(req);
      toast('Incoming ride request!', 'info');
    },
    [activeRide, toast]
  );
  useDriverRequests(driver?.id, online && !activeRide, onRequest);

  const toggleOnline = async () => {
    if (!driver) return;
    setToggling(true);
    try {
      const updated = await driverApi.setAvailability(!online);
      setOnline(updated.available);
      if (updated.available) {
        await driverApi.updateLocation(posRef.current.lat, posRef.current.lng);
      }
      toast(updated.available ? "You're online" : "You're offline", 'success');
    } catch (e) {
      toast(apiError(e), 'error');
    } finally {
      setToggling(false);
    }
  };

  const accept = async () => {
    if (!request) return;
    setActing(true);
    try {
      await rideApi.accept(request.rideId);
      toast('Ride accepted — head to pickup', 'success');
      handledRef.current.add(request.rideId);
      setRequest(null);
      qc.invalidateQueries({ queryKey: ['rides', 'history'] });
    } catch (e) {
      if (axios.isAxiosError(e) && e.response?.status === 409) {
        // Stale poll re-surfaced a ride we (or another driver) already handled — drop it.
        handledRef.current.add(request.rideId);
        setRequest(null);
        toast('This ride is no longer available', 'info');
        qc.invalidateQueries({ queryKey: ['rides', 'history'] });
      } else {
        toast(apiError(e), 'error');
      }
    } finally {
      setActing(false);
    }
  };

  const decline = async () => {
    if (!request) return;
    const id = request.rideId;
    handledRef.current.add(id);
    setRequest(null);
    try {
      await rideApi.decline(id);
    } catch {
      /* timeout-driven decline may race; ignore */
    }
  };

  const advance = async (action: 'start' | 'complete') => {
    if (!activeRide) return;
    setActing(true);
    try {
      await rideApi[action](activeRide.id);
      toast(action === 'start' ? 'Trip started' : 'Trip completed', 'success');
      qc.invalidateQueries({ queryKey: ['rides', 'history'] });
    } catch (e) {
      toast(apiError(e), 'error');
    } finally {
      setActing(false);
    }
  };

  // Start requires the passenger's pickup PIN.
  const startWithPin = async () => {
    if (!activeRide) return;
    setActing(true);
    setPinError('');
    try {
      await rideApi.start(activeRide.id, pinInput);
      toast('Trip started', 'success');
      setPinOpen(false);
      setPinInput('');
      qc.invalidateQueries({ queryKey: ['rides', 'history'] });
    } catch (e) {
      setPinError(apiError(e));
    } finally {
      setActing(false);
    }
  };

  if (loading) return <CenteredSpinner label="Loading your dashboard…" />;
  if (!driver) return null;

  if (driver.verificationStatus !== 'VERIFIED') {
    return (
      <div className="mx-auto max-w-md p-6">
        <div className="rounded-lg border bg-card p-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-warning/15 text-warning">
            {driver.verificationStatus === 'REJECTED' ? <ShieldAlert className="h-6 w-6" /> : <Clock className="h-6 w-6" />}
          </div>
          <h1 className="text-xl font-bold">
            {driver.verificationStatus === 'REJECTED' ? 'Verification rejected' : 'Awaiting verification'}
          </h1>
          <div className="mt-2 flex justify-center">
            <VerificationBadge status={driver.verificationStatus} />
          </div>
          <p className="mt-4 text-sm text-muted-foreground">
            {driver.verificationStatus === 'REJECTED'
              ? 'Your application was not approved. Please contact support.'
              : 'An admin is reviewing your documents. You can go online once verified.'}
          </p>
        </div>
      </div>
    );
  }

  const markers: MapMarker[] = [{ ...pos, kind: 'driver' }];
  if (activeRide) {
    markers.push({ lat: activeRide.pickupLatitude, lng: activeRide.pickupLongitude, kind: 'pickup' });
    markers.push({ lat: activeRide.dropoffLatitude, lng: activeRide.dropoffLongitude, kind: 'dropoff' });
  }

  return (
    <div className="flex h-full flex-col-reverse lg:flex-row">
      <div className="w-full overflow-y-auto border-t bg-card p-6 lg:w-[400px] lg:border-r lg:border-t-0">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Driver</h1>
          <span
            className={cn(
              'flex items-center gap-1.5 text-sm font-medium',
              activeRide ? 'text-primary' : online ? 'text-success' : 'text-muted-foreground'
            )}
          >
            <span
              className={cn(
                'h-2 w-2 rounded-full',
                activeRide ? 'bg-primary' : online ? 'bg-success' : 'bg-muted-foreground'
              )}
            />
            {activeRide ? 'On a trip' : online ? 'Online' : 'Offline'}
          </span>
        </div>

        <button
          onClick={toggleOnline}
          disabled={toggling || !!activeRide}
          className={cn(
            'mt-5 flex w-full items-center justify-center gap-3 rounded-xl border-2 py-6 text-lg font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-70',
            working ? 'border-success bg-success/10 text-success' : 'border-border hover:border-primary'
          )}
        >
          {toggling ? <Loader2 className="h-6 w-6 animate-spin" /> : <Power className="h-6 w-6" />}
          {activeRide ? 'On a trip' : online ? 'Go offline' : 'Go online'}
        </button>
        {!activeRide && online && (
          <p className="mt-3 text-center text-sm text-muted-foreground">
            <Loader2 className="mr-1 inline h-3.5 w-3.5 animate-spin" />
            Waiting for ride requests…
          </p>
        )}

        {/* Location source: demo motion (default) vs real device GPS */}
        <div className="mt-4">
          <p className="mb-1.5 text-xs font-medium text-muted-foreground">Location source</p>
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setGpsMode('sim')}
              disabled={!!activeRide}
              className={cn(
                'flex items-center justify-center gap-1.5 rounded-lg border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-60',
                gpsMode === 'sim' ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:border-primary'
              )}
            >
              <Joystick className="h-4 w-4" /> Demo motion
            </button>
            <button
              onClick={() => setGpsMode('live')}
              disabled={!!activeRide}
              className={cn(
                'flex items-center justify-center gap-1.5 rounded-lg border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-60',
                gpsMode === 'live' ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:border-primary'
              )}
            >
              <Satellite className="h-4 w-4" /> Live GPS
            </button>
          </div>
          <p className="mt-1.5 text-xs text-muted-foreground">
            {gpsMode === 'live'
              ? 'Streaming your real device location.'
              : 'Simulated motion toward the trip (works anywhere for the demo).'}
          </p>
        </div>

        {activeRide && (
          <div className="mt-6 space-y-4 animate-fade-in">
            <div className="flex items-center justify-between">
              <span className="font-semibold">Current trip</span>
              <RideStatusBadge status={activeRide.status} />
            </div>
            {/* Passenger details + chat */}
            {passenger && (
              <div className="space-y-3 rounded-lg border p-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary/15 text-sm font-bold text-primary">
                    {initials(passenger.name)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">{passenger.name}</p>
                    <p className="flex items-center gap-1 text-xs text-muted-foreground">
                      <User className="h-3 w-3" /> Passenger
                    </p>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Button variant="outline" onClick={() => setChatOpen(true)}>
                    <MessageSquare className="h-4 w-4" /> Message
                  </Button>
                  <a
                    href={`tel:${passenger.phone}`}
                    title={`Call ${passenger.phone}`}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-input bg-transparent px-4 text-sm font-medium hover:bg-accent hover:text-accent-foreground"
                  >
                    <Phone className="h-4 w-4" /> Call
                  </a>
                </div>
                {passenger.phone && (
                  <p className="text-center text-xs text-muted-foreground">
                    Passenger phone: <span className="font-medium text-foreground">{passenger.phone}</span>
                  </p>
                )}
              </div>
            )}
            <div className="rounded-lg border p-4">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2 font-medium">
                  <Navigation className="h-4 w-4 text-primary" /> {activeRide.vehicleType}
                </span>
                <span className="font-bold">{formatCurrency(activeRide.estimatedFare)}</span>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                {formatDistance(activeRide.distanceKm)} · {formatDuration(activeRide.durationMinutes)} trip
              </p>
              <div className="mt-3 space-y-1.5 text-sm">
                <p className="flex items-start gap-2">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                  <span>{activeRide.pickupAddress || 'Pickup'}</span>
                </p>
                <p className="flex items-start gap-2">
                  <Flag className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
                  <span>{activeRide.dropoffAddress || 'Drop-off'}</span>
                </p>
              </div>
            </div>
            {activeRide.status === 'MATCHED' ? (
              <Button
                className="w-full"
                size="lg"
                onClick={() => {
                  setPinInput('');
                  setPinError('');
                  setPinOpen(true);
                }}
              >
                Start ride
              </Button>
            ) : (
              <Button className="w-full" size="lg" loading={acting} onClick={() => advance('complete')}>
                Complete ride
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="relative h-[45vh] flex-1 lg:h-full">
        <MapView
          markers={markers}
          route={routePath.length >= 2 ? routePath : undefined}
          center={[pos.lat, pos.lng]}
          zoom={13}
          fit={!!activeRide}
        />
      </div>

      <RideRequestModal request={request} onAccept={accept} onDecline={decline} busy={acting} />

      {activeRide && passenger && (
        <ChatPanel
          rideId={activeRide.id}
          open={chatOpen}
          onClose={() => setChatOpen(false)}
          title={`Chat with ${passenger.name.split(' ')[0]}`}
        />
      )}

      <Dialog open={pinOpen} onClose={() => setPinOpen(false)}>
        <div className="space-y-4">
          <div>
            <h3 className="text-lg font-bold">Verify pickup</h3>
            <p className="text-sm text-muted-foreground">Ask the passenger for their 4-digit PIN.</p>
          </div>
          <Input
            value={pinInput}
            onChange={(e) => {
              setPinInput(e.target.value.replace(/\D/g, '').slice(0, 4));
              setPinError('');
            }}
            inputMode="numeric"
            placeholder="• • • •"
            autoFocus
            className="text-center text-2xl font-bold tracking-[0.5em]"
            onKeyDown={(e) => e.key === 'Enter' && pinInput.length === 4 && startWithPin()}
          />
          {pinError && <p className="text-sm text-destructive">{pinError}</p>}
          <Button
            className="w-full"
            size="lg"
            loading={acting}
            disabled={pinInput.length !== 4}
            onClick={startWithPin}
          >
            Verify &amp; start trip
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
