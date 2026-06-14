import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, X, ShieldCheck } from 'lucide-react';
import { driverApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { Button } from '@/components/ui/button';
import { VerificationBadge, Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/toast';
import { CenteredSpinner, EmptyState } from '@/components/ui/misc';
import { cn } from '@/lib/utils';
import type { VerificationStatus } from '@/types';

export function DriverManagementPage() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [tab, setTab] = useState<VerificationStatus | 'ALL'>('PENDING');

  const { data = [], isLoading } = useQuery({ queryKey: ['drivers', 'all'], queryFn: driverApi.all });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['drivers', 'all'] });
    qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
  };

  const verify = useMutation({
    mutationFn: driverApi.verify,
    onSuccess: () => { toast('Driver verified', 'success'); refresh(); },
    onError: (e) => toast(apiError(e), 'error'),
  });
  const reject = useMutation({
    mutationFn: driverApi.reject,
    onSuccess: () => { toast('Driver rejected', 'info'); refresh(); },
    onError: (e) => toast(apiError(e), 'error'),
  });

  const filtered = data.filter((d) => tab === 'ALL' || d.verificationStatus === tab);
  const tabs: (VerificationStatus | 'ALL')[] = ['PENDING', 'VERIFIED', 'REJECTED', 'ALL'];

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Driver management</h1>
        <p className="text-sm text-muted-foreground">Verify driver applications and manage onboarding</p>
      </div>

      <div className="mb-4 flex gap-1 rounded-lg border bg-card p-1">
        {tabs.map((t) => {
          const count = data.filter((d) => t === 'ALL' || d.verificationStatus === t).length;
          return (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={cn(
                'flex-1 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                tab === t ? 'bg-primary/15 text-primary' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              {t === 'ALL' ? 'All' : t.charAt(0) + t.slice(1).toLowerCase()} ({count})
            </button>
          );
        })}
      </div>

      {isLoading ? (
        <CenteredSpinner />
      ) : filtered.length === 0 ? (
        <EmptyState icon={<ShieldCheck className="h-8 w-8" />} title="No drivers here" description="Drivers will appear once they apply." />
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <table className="w-full text-sm">
            <thead className="border-b bg-muted/40 text-left text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">License</th>
                <th className="px-4 py-3 font-medium">Rating</th>
                <th className="px-4 py-3 font-medium">Availability</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((d) => (
                <tr key={d.id} className="border-b last:border-0">
                  <td className="px-4 py-3 font-mono text-xs">{d.licenseNumber}</td>
                  <td className="px-4 py-3">★ {Number(d.rating).toFixed(1)}</td>
                  <td className="px-4 py-3">
                    <Badge variant={d.available ? 'success' : 'muted'}>{d.available ? 'Online' : 'Offline'}</Badge>
                  </td>
                  <td className="px-4 py-3"><VerificationBadge status={d.verificationStatus} /></td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      {d.verificationStatus !== 'VERIFIED' && (
                        <Button size="sm" loading={verify.isPending && verify.variables === d.id} onClick={() => verify.mutate(d.id)}>
                          <Check className="h-3.5 w-3.5" /> Verify
                        </Button>
                      )}
                      {d.verificationStatus !== 'REJECTED' && (
                        <Button size="sm" variant="outline" loading={reject.isPending && reject.variables === d.id} onClick={() => reject.mutate(d.id)}>
                          <X className="h-3.5 w-3.5" /> Reject
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
