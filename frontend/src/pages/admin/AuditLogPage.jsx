import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { ScrollText, Search, DollarSign } from "lucide-react";
import { adminApi } from "@/api/endpoints";
import { apiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { RideStatusBadge } from "@/components/ui/badge";
import { useToast } from "@/components/ui/toast";
import { EmptyState } from "@/components/ui/misc";
import { formatCurrency, formatDateTime } from "@/lib/utils";

export function AuditLogPage() {
  const { toast } = useToast();
  const [rideId, setRideId] = useState("");
  const [entries, setEntries] = useState(null);
  const [adjustFare, setAdjustFare] = useState("");

  const lookup = useMutation({
    mutationFn: (id) => adminApi.rideAudit(id),
    onSuccess: (data) => setEntries(data),
    onError: (e) => {
      setEntries(null);
      toast(apiError(e), "error");
    },
  });

  const adjust = useMutation({
    mutationFn: () => adminApi.adjustFare(rideId.trim(), Number(adjustFare)),
    onSuccess: () => {
      toast("Fare adjusted — audit trail updated", "success");
      setAdjustFare("");
      lookup.mutate(rideId.trim());
    },
    onError: (e) => toast(apiError(e), "error"),
  });

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Audit & disputes</h1>
        <p className="text-sm text-muted-foreground">
          Inspect a ride's full change history (Hibernate Envers) and resolve
          fare disputes.
        </p>
      </div>

      <div className="flex gap-2">
        <Input
          value={rideId}
          onChange={(e) => setRideId(e.target.value)}
          placeholder="Paste a ride ID (UUID)…"
          className="font-mono text-xs"
        />

        <Button
          loading={lookup.isPending}
          disabled={!rideId.trim()}
          onClick={() => lookup.mutate(rideId.trim())}
        >
          <Search className="h-4 w-4" /> Look up
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        Tip: copy a ride ID from the passenger or driver history.
      </p>

      {entries && entries.length > 0 && (
        <>
          <div className="mt-6 rounded-lg border bg-card p-5">
            <div className="mb-3 flex items-center gap-2">
              <DollarSign className="h-4 w-4 text-primary" />
              <h2 className="font-semibold">
                Adjust fare (dispute resolution)
              </h2>
            </div>
            <div className="flex gap-2">
              <div className="flex-1 space-y-1.5">
                <Label>New actual fare (USD)</Label>
                <Input
                  type="number"
                  step="0.01"
                  min={0}
                  value={adjustFare}
                  onChange={(e) => setAdjustFare(e.target.value)}
                  placeholder="0.00"
                />
              </div>
              <div className="flex items-end">
                <Button
                  loading={adjust.isPending}
                  disabled={!adjustFare}
                  onClick={() => adjust.mutate()}
                >
                  Apply
                </Button>
              </div>
            </div>
          </div>

          <div className="mt-6">
            <h2 className="mb-3 flex items-center gap-2 font-semibold">
              <ScrollText className="h-4 w-4" /> Change history (
              {entries.length} revisions)
            </h2>
            <div className="relative space-y-4 border-l-2 border-border pl-6">
              {entries.map((e) => (
                <div key={e.revision} className="relative">
                  <span className="absolute -left-[31px] top-1 h-3.5 w-3.5 rounded-full border-2 border-primary bg-background" />
                  <div className="rounded-lg border bg-card p-4">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-medium text-muted-foreground">
                          rev #{e.revision}
                        </span>
                        <RideStatusBadge status={e.status} />
                      </div>
                      <span className="text-xs text-muted-foreground">
                        {formatDateTime(e.changedAt)}
                      </span>
                    </div>
                    <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-sm">
                      <span>
                        Est: <strong>{formatCurrency(e.estimatedFare)}</strong>
                      </span>
                      <span>
                        Actual: <strong>{formatCurrency(e.actualFare)}</strong>
                      </span>
                      {e.changedBy && (
                        <span className="text-muted-foreground">
                          by {e.changedBy}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {entries && entries.length === 0 && (
        <div className="mt-6">
          <EmptyState
            icon={<ScrollText className="h-8 w-8" />}
            title="No audit history"
            description="This ride has no recorded revisions."
          />
        </div>
      )}
    </div>
  );
}
