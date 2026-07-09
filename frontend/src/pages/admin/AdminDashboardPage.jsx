import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  CheckCircle2,
  XCircle,
  DollarSign,
  Users,
  Car,
  ShieldQuestion,
} from "lucide-react";
import { adminApi } from "@/api/endpoints";
import { Stat, CenteredSpinner } from "@/components/ui/misc";
import { formatCurrency } from "@/lib/utils";

// Order matches rideData: Active (amber), Completed (green), Cancelled (red).
const COLORS = [
  "hsl(var(--warning))",
  "hsl(var(--primary))",
  "hsl(var(--destructive))",
];

export function AdminDashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: adminApi.dashboard,
    refetchInterval: 10000,
  });

  if (isLoading || !data) return <CenteredSpinner label="Loading dashboard…" />;

  const rideData = [
    { name: "Active", value: data.activeRides },
    { name: "Completed", value: data.completedRides },
    { name: "Cancelled", value: data.cancelledRides },
  ];
  const totalRides = rideData.reduce((s, d) => s + d.value, 0);

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Operations dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Live platform overview · refreshes every 10s
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Active rides"
          value={data.activeRides}
          icon={<Activity className="h-5 w-5" />}
        />
        <Stat
          label="Total revenue"
          value={formatCurrency(data.totalRevenue)}
          icon={<DollarSign className="h-5 w-5" />}
        />
        <Stat
          label="Passengers"
          value={data.passengerCount}
          icon={<Users className="h-5 w-5" />}
        />
        <Stat
          label="Drivers"
          value={data.driverCount}
          icon={<Car className="h-5 w-5" />}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Completed rides"
          value={data.completedRides}
          icon={<CheckCircle2 className="h-5 w-5" />}
        />
        <Stat
          label="Cancelled rides"
          value={data.cancelledRides}
          icon={<XCircle className="h-5 w-5" />}
        />
        <Stat
          label="Pending verifications"
          value={data.pendingDriverVerifications}
          icon={<ShieldQuestion className="h-5 w-5" />}
          hint={
            data.pendingDriverVerifications > 0 ? "Action needed" : "All clear"
          }
        />

        <Stat
          label="Completion rate"
          value={`${completionRate(data.completedRides, data.cancelledRides)}%`}
          icon={<CheckCircle2 className="h-5 w-5" />}
        />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="rounded-lg border bg-card p-6">
          <h2 className="mb-4 font-semibold">Ride status breakdown</h2>
          {totalRides === 0 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">
              No rides yet
            </p>
          ) : (
            <div className="space-y-5">
              <div className="flex h-4 w-full overflow-hidden rounded-full bg-muted">
                {rideData.map((d, i) =>
                  d.value > 0 ? (
                    <div
                      key={d.name}
                      style={{
                        width: `${(d.value / totalRides) * 100}%`,
                        background: COLORS[i],
                      }}
                      title={`${d.name}: ${d.value}`}
                    />
                  ) : null,
                )}
              </div>
              <div className="space-y-3">
                {rideData.map((d, i) => (
                  <div
                    key={d.name}
                    className="flex items-center justify-between text-sm"
                  >
                    <span className="flex items-center gap-2">
                      <span
                        className="h-3 w-3 rounded-sm"
                        style={{ background: COLORS[i] }}
                      />
                      {d.name}
                    </span>
                    <span className="font-medium">
                      {d.value}
                      <span className="ml-2 text-muted-foreground">
                        {totalRides
                          ? Math.round((d.value / totalRides) * 100)
                          : 0}
                        %
                      </span>
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="rounded-lg border bg-card p-6">
          <h2 className="mb-4 font-semibold">Platform health</h2>
          <div className="space-y-4">
            <HealthRow
              label="Total rides"
              value={
                data.activeRides + data.completedRides + data.cancelledRides
              }
            />
            <HealthRow
              label="Revenue per completed ride"
              value={formatCurrency(
                data.completedRides
                  ? data.totalRevenue / data.completedRides
                  : 0,
              )}
            />
            <HealthRow
              label="Drivers per passenger"
              value={(
                data.driverCount / Math.max(1, data.passengerCount)
              ).toFixed(2)}
            />
            <HealthRow
              label="Verifications pending"
              value={data.pendingDriverVerifications}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function HealthRow({ label, value }) {
  return (
    <div className="flex items-center justify-between border-b pb-3 last:border-0 last:pb-0">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="font-semibold">{value}</span>
    </div>
  );
}

function completionRate(completed, cancelled) {
  const total = completed + cancelled;
  return total === 0 ? 0 : Math.round((completed / total) * 100);
}
