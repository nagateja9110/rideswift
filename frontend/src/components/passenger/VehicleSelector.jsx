import { Car, Gem, Users } from "lucide-react";
import { cn, formatCurrency } from "@/lib/utils";

const OPTIONS = [
  {
    type: "ECONOMY",
    label: "Economy",
    desc: "Affordable everyday rides",
    icon: <Car className="h-6 w-6" />,
  },
  {
    type: "PREMIUM",
    label: "Premium",
    desc: "Top-rated drivers, nicer cars",
    icon: <Gem className="h-6 w-6" />,
  },
  {
    type: "XL",
    label: "XL",
    desc: "Extra room, up to 6 riders",
    icon: <Users className="h-6 w-6" />,
  },
];

export function VehicleSelector({ value, onChange, fares }) {
  return (
    <div className="space-y-2">
      {OPTIONS.map((o) => {
        const info = fares?.[o.type];
        const active = value === o.type;
        return (
          <button
            key={o.type}
            type="button"
            onClick={() => onChange(o.type)}
            className={cn(
              "flex w-full items-center gap-4 rounded-lg border p-3 text-left transition-colors",
              active
                ? "border-primary bg-primary/10"
                : "hover:border-muted-foreground/40",
            )}
          >
            <div
              className={cn(
                "shrink-0",
                active ? "text-primary" : "text-muted-foreground",
              )}
            >
              {o.icon}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium">{o.label}</span>
                {info && info.surge > 1 && (
                  <span className="rounded bg-warning/15 px-1.5 py-0.5 text-[10px] font-semibold text-warning">
                    {info.surge.toFixed(1)}× surge
                  </span>
                )}
              </div>
              <p className="truncate text-xs text-muted-foreground">{o.desc}</p>
            </div>
            <div className="shrink-0 text-right">
              {info ? (
                <span className="font-semibold">
                  {formatCurrency(info.fare)}
                </span>
              ) : (
                <span className="text-xs text-muted-foreground">—</span>
              )}
            </div>
          </button>
        );
      })}
    </div>
  );
}
