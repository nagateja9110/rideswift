import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { SlidersHorizontal } from 'lucide-react';
import { fareApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/toast';
import { CenteredSpinner } from '@/components/ui/misc';
import { formatCurrency } from '@/lib/utils';
import type { FareRule } from '@/types';

export function FareRulesPage() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const { data = [], isLoading } = useQuery({ queryKey: ['fare', 'rules'], queryFn: fareApi.rules });
  const [editing, setEditing] = useState<FareRule | null>(null);

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Fare rules</h1>
        <p className="text-sm text-muted-foreground">Configure pricing per vehicle class</p>
      </div>

      {isLoading ? (
        <CenteredSpinner />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((rule) => (
            <div key={rule.id} className="rounded-lg border bg-card p-5">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold">{rule.vehicleType}</h3>
                {Number(rule.surgeMultiplier) > 1 && (
                  <Badge variant="warning">{Number(rule.surgeMultiplier).toFixed(1)}× surge</Badge>
                )}
              </div>
              <dl className="mt-4 space-y-2 text-sm">
                <Row label="Base fare" value={formatCurrency(rule.baseFare)} />
                <Row label="Per km" value={formatCurrency(rule.perKmRate)} />
                <Row label="Per minute" value={formatCurrency(rule.perMinuteRate)} />
                <Row label="Surge multiplier" value={`${Number(rule.surgeMultiplier).toFixed(2)}×`} />
              </dl>
              <Button variant="outline" size="sm" className="mt-4 w-full" onClick={() => setEditing(rule)}>
                <SlidersHorizontal className="h-3.5 w-3.5" /> Edit
              </Button>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <EditDialog
          rule={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            qc.invalidateQueries({ queryKey: ['fare', 'rules'] });
            toast('Fare rule updated', 'success');
          }}
          onError={(e) => toast(apiError(e), 'error')}
        />
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

function EditDialog({
  rule,
  onClose,
  onSaved,
  onError,
}: {
  rule: FareRule;
  onClose: () => void;
  onSaved: () => void;
  onError: (e: unknown) => void;
}) {
  const [baseFare, setBaseFare] = useState(String(rule.baseFare));
  const [perKmRate, setPerKmRate] = useState(String(rule.perKmRate));
  const [perMinuteRate, setPerMinuteRate] = useState(String(rule.perMinuteRate));
  const [surgeMultiplier, setSurgeMultiplier] = useState(String(rule.surgeMultiplier));

  const save = useMutation({
    mutationFn: () =>
      fareApi.updateRule(rule.id, {
        baseFare: Number(baseFare),
        perKmRate: Number(perKmRate),
        perMinuteRate: Number(perMinuteRate),
        surgeMultiplier: Number(surgeMultiplier),
      }),
    onSuccess: onSaved,
    onError,
  });

  return (
    <Dialog open onClose={onClose}>
      <h3 className="text-lg font-bold">Edit {rule.vehicleType} fare</h3>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <FareField label="Base fare" value={baseFare} onChange={setBaseFare} />
        <FareField label="Per km" value={perKmRate} onChange={setPerKmRate} />
        <FareField label="Per minute" value={perMinuteRate} onChange={setPerMinuteRate} />
        <FareField label="Surge ×" value={surgeMultiplier} onChange={setSurgeMultiplier} min={1} />
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button loading={save.isPending} onClick={() => save.mutate()}>Save changes</Button>
      </div>
    </Dialog>
  );
}

function FareField({
  label,
  value,
  onChange,
  min = 0,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  min?: number;
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      <Input type="number" step="0.01" min={min} value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}
