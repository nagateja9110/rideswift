import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Wallet, TrendingUp, Car } from 'lucide-react';
import { driverApi, rideApi } from '@/api/endpoints';
import { Stat, CenteredSpinner, EmptyState } from '@/components/ui/misc';
import { Select } from '@/components/ui/input';
import { formatCurrency } from '@/lib/utils';
import type { Ride } from '@/types';

type Range = 'week' | 'month';

export function EarningsPage() {
  const [range, setRange] = useState<Range>('week');
  const { data: driver } = useQuery({ queryKey: ['driver', 'me'], queryFn: driverApi.me });
  const { data: history = [], isLoading } = useQuery({ queryKey: ['rides', 'history'], queryFn: rideApi.history });

  const completed = useMemo(
    () => history.filter((r) => r.driverId === driver?.id && r.status === 'COMPLETED' && r.paymentStatus === 'SUCCESS'),
    [history, driver]
  );

  const totalEarnings = completed.reduce((sum, r) => sum + (r.actualFare ?? r.estimatedFare ?? 0), 0);
  const avgFare = completed.length ? totalEarnings / completed.length : 0;

  const chartData = useMemo(() => buildChart(completed, range), [completed, range]);

  if (isLoading) return <CenteredSpinner />;

  return (
    <div className="mx-auto max-w-4xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Earnings</h1>
          <p className="text-sm text-muted-foreground">Your completed trips and revenue</p>
        </div>
        <Select value={range} onChange={(e) => setRange(e.target.value as Range)} className="w-36">
          <option value="week">Last 7 days</option>
          <option value="month">Last 30 days</option>
        </Select>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Stat label="Total earnings" value={formatCurrency(totalEarnings)} icon={<Wallet className="h-5 w-5" />} />
        <Stat label="Completed trips" value={completed.length} icon={<Car className="h-5 w-5" />} />
        <Stat label="Avg. fare" value={formatCurrency(avgFare)} icon={<TrendingUp className="h-5 w-5" />} />
      </div>

      <div className="mt-6 rounded-lg border bg-card p-6">
        <h2 className="mb-4 font-semibold">Revenue by day</h2>
        {completed.length === 0 ? (
          <EmptyState icon={<Wallet className="h-8 w-8" />} title="No earnings yet" description="Complete rides to see your revenue here." />
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
              <XAxis dataKey="label" stroke="hsl(var(--muted-foreground))" fontSize={12} tickLine={false} />
              <YAxis stroke="hsl(var(--muted-foreground))" fontSize={12} tickLine={false} axisLine={false} />
              <Tooltip
                cursor={{ fill: 'hsl(var(--muted))' }}
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: 8,
                  fontSize: 13,
                }}
                formatter={(v: number) => [formatCurrency(v), 'Revenue']}
              />
              <Bar dataKey="value" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}

function buildChart(rides: Ride[], range: Range) {
  const days = range === 'week' ? 7 : 30;
  const buckets: { label: string; value: number; key: string }[] = [];
  const now = new Date();
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(now.getDate() - i);
    const key = d.toISOString().slice(0, 10);
    buckets.push({
      key,
      label: d.toLocaleDateString('en-US', range === 'week' ? { weekday: 'short' } : { month: 'short', day: 'numeric' }),
      value: 0,
    });
  }
  const map = new Map(buckets.map((b) => [b.key, b]));
  rides.forEach((r) => {
    const key = (r.completedAt ?? r.requestedAt).slice(0, 10);
    const b = map.get(key);
    if (b) b.value += r.actualFare ?? r.estimatedFare ?? 0;
  });
  return buckets;
}
