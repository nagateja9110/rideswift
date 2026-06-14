import { useEffect, useMemo } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export interface MapMarker {
  lat: number;
  lng: number;
  kind?: 'pickup' | 'dropoff' | 'driver' | 'car';
  label?: string;
}

function pinIcon(color: string, glyph: string) {
  return L.divIcon({
    className: 'rideswift-pin',
    html: `<div style="position:relative;transform:translate(-50%,-100%)">
      <div style="width:30px;height:30px;border-radius:50% 50% 50% 0;background:${color};transform:rotate(-45deg);box-shadow:0 2px 6px rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center">
        <span style="transform:rotate(45deg);font-size:14px;line-height:1">${glyph}</span>
      </div>
    </div>`,
    iconSize: [30, 30],
    iconAnchor: [0, 0],
  });
}

const ICONS: Record<string, L.DivIcon> = {
  pickup: pinIcon('#16a34a', '📍'),
  dropoff: pinIcon('#ef4444', '🏁'),
  driver: pinIcon('#2563eb', '🚗'),
  car: pinIcon('#64748b', '🚕'),
};

function FitBounds({ markers }: { markers: MapMarker[] }) {
  const map = useMap();
  useEffect(() => {
    // Ambient 'car' markers are decorative — never let them drive the viewport.
    const focus = markers.filter((m) => m.kind !== 'car');
    if (focus.length === 0) return;
    if (focus.length === 1) {
      map.setView([focus[0].lat, focus[0].lng], 14);
      return;
    }
    const bounds = L.latLngBounds(focus.map((m) => [m.lat, m.lng] as [number, number]));
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
  }, [map, markers]);
  return null;
}

function ClickHandler({ onClick }: { onClick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onClick(e.latlng.lat, e.latlng.lng) });
  return null;
}

export function MapView({
  markers = [],
  route,
  center = [11.0168, 76.9558],
  zoom = 12,
  onClick,
  className = 'h-full w-full',
  fit = true,
}: {
  markers?: MapMarker[];
  route?: Array<[number, number]>;
  center?: [number, number];
  zoom?: number;
  onClick?: (lat: number, lng: number) => void;
  className?: string;
  fit?: boolean;
}) {
  const polyline = useMemo(() => {
    if (route && route.length >= 2) return route;
    // Straight-line fallback represents the trip only — never connect ambient
    // 'car' (or 'driver') markers, which would draw stray lines across the map.
    const trip = markers.filter((m) => m.kind === 'pickup' || m.kind === 'dropoff');
    return trip.length >= 2 ? trip.map((m) => [m.lat, m.lng] as [number, number]) : undefined;
  }, [route, markers]);
  // A supplied `route` is a real road geometry → solid; the marker fallback is a
  // straight-line approximation → dashed.
  const isRealRoute = !!route && route.length >= 2;

  // During an active ride, split the road route at the driver's current position
  // so the already-covered stretch can be drawn in a lighter "trail" green.
  const progress = useMemo(() => {
    if (!isRealRoute || !polyline) return null;
    const driver = markers.find((m) => m.kind === 'driver');
    if (!driver) return null;
    let nearest = 0;
    let best = Infinity;
    for (let i = 0; i < polyline.length; i++) {
      const dLat = polyline[i][0] - driver.lat;
      const dLng = polyline[i][1] - driver.lng;
      const d = dLat * dLat + dLng * dLng;
      if (d < best) {
        best = d;
        nearest = i;
      }
    }
    if (nearest <= 0) return null; // nothing covered yet
    return {
      travelled: polyline.slice(0, nearest + 1),
      remaining: polyline.slice(nearest), // shares the boundary vertex → no gap
    };
  }, [isRealRoute, polyline, markers]);

  return (
    <MapContainer center={center} zoom={zoom} className={className} scrollWheelZoom>
      <TileLayer
        attribution='&copy; OpenStreetMap'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {markers.map((m, i) => (
        <Marker key={i} position={[m.lat, m.lng]} icon={ICONS[m.kind ?? 'pickup']} />
      ))}
      {polyline && polyline.length >= 2 && !progress && (
        <Polyline
          positions={polyline}
          pathOptions={{
            color: '#16a34a',
            weight: isRealRoute ? 5 : 4,
            opacity: isRealRoute ? 0.85 : 0.7,
            dashArray: isRealRoute ? undefined : '8 8',
          }}
        />
      )}
      {progress && (
        <>
          {/* Already-covered stretch — faded light-green trail. */}
          <Polyline
            positions={progress.travelled}
            pathOptions={{ color: '#86efac', weight: 5, opacity: 0.4 }}
          />
          {/* Remaining stretch — full green. */}
          <Polyline
            positions={progress.remaining}
            pathOptions={{ color: '#16a34a', weight: 5, opacity: 0.85 }}
          />
        </>
      )}
      {onClick && <ClickHandler onClick={onClick} />}
      {fit && <FitBounds markers={markers} />}
    </MapContainer>
  );
}
