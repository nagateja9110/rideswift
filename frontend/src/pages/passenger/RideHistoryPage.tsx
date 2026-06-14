import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Car, MapPin, Flag } from 'lucide-react';
import { rideApi } from '@/api/endpoints';
import { RideStatusBadge } from '@/components/ui/badge';
import { Select } from '@/components/ui/input';
import { CenteredSpinner, EmptyState } from '@/components/ui/misc';
import { formatCurrency, formatDistance, formatDateTime } from '@/lib/utils';
import type { RideStatus } from '@/types';

export function RideHistoryPage() {
  const [filter, setFilter] = useState<RideStatus | 'ALL'>('ALL');
  const { data = [], isLoading } = useQuery({ queryKey: ['rides', 'history'], queryFn: rideApi.history });

  const rides = [...data]
    .filter((r) => filter === 'ALL' || r.status === filter)
    .sort((a, b) => new Date(b.requestedAt).getTime() - new Date(a.requestedAt).getTime());

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Ride history</h1>
          <p className="text-sm text-muted-foreground">{data.length} total rides</p>
        </div>
        <Select value={filter} onChange={(e) => setFilter(e.target.value as never)} className="w-44">
          <option value="ALL">All statuses</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="MATCHED">Matched</option>
          <option value="REQUESTED">Requested</option>
        </Select>
      </div>

      {isLoading ? (
        <CenteredSpinner />
      ) : rides.length === 0 ? (
        <EmptyState icon={<Car className="h-8 w-8" />} title="No rides yet" description="Your completed and upcoming trips will appear here." />
      ) : (
        <div className="space-y-3">
          {rides.map((r) => (
            <div key={r.id} className="rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{r.vehicleType}</span>
                    <RideStatusBadge status={r.status} />
                  </div>
                  <div className="mt-2 space-y-1 text-sm">
                    <p className="flex items-center gap-2 text-muted-foreground">
                      <MapPin className="h-3.5 w-3.5 shrink-0 text-primary" />
                      <span className="truncate">{r.pickupAddress || `${r.pickupLatitude.toFixed(3)}, ${r.pickupLongitude.toFixed(3)}`}</span>
                    </p>
                    <p className="flex items-center gap-2 text-muted-foreground">
                      <Flag className="h-3.5 w-3.5 shrink-0 text-destructive" />
                      <span className="truncate">{r.dropoffAddress || `${r.dropoffLatitude.toFixed(3)}, ${r.dropoffLongitude.toFixed(3)}`}</span>
                    </p>
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    {formatDateTime(r.requestedAt)} · {formatDistance(r.distanceKm)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-lg font-bold">{formatCurrency(r.actualFare ?? r.estimatedFare)}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
