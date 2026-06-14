import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Megaphone, Zap, AlertTriangle } from 'lucide-react';
import { adminApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input, Label, Select } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/components/ui/toast';
import { cn } from '@/lib/utils';
import type { Role } from '@/types';

export function OperationsPage() {
  const { toast } = useToast();
  const [message, setMessage] = useState('');
  const [audience, setAudience] = useState<Role | 'ALL'>('ALL');
  const [outage, setOutage] = useState(false);

  const broadcast = useMutation({
    mutationFn: () => adminApi.broadcast({ message, role: audience === 'ALL' ? null : audience }),
    onSuccess: (res) => {
      toast(`Broadcast sent to ${res.recipients} user(s)`, 'success');
      setMessage('');
    },
    onError: (e) => toast(apiError(e), 'error'),
  });

  const toggleOutage = useMutation({
    mutationFn: (enabled: boolean) => adminApi.setPaymentOutage(enabled),
    onSuccess: (res) => {
      setOutage(res.paymentOutage);
      toast(res.paymentOutage ? 'Payment outage simulated — breaker will trip' : 'Payment provider restored', 'info');
    },
    onError: (e) => toast(apiError(e), 'error'),
  });

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Operations</h1>
        <p className="text-sm text-muted-foreground">Broadcast messages and exercise resilience controls</p>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Megaphone className="h-5 w-5 text-primary" />
              <CardTitle>Broadcast notification</CardTitle>
            </div>
            <CardDescription>Push an in-app message to users by role.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <Label>Audience</Label>
              <Select value={audience} onChange={(e) => setAudience(e.target.value as never)}>
                <option value="ALL">All users</option>
                <option value="PASSENGER">Passengers</option>
                <option value="DRIVER">Drivers</option>
                <option value="ADMIN">Admins</option>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Message</Label>
              <Input
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder="e.g. Surge pricing active downtown tonight"
                maxLength={500}
              />
            </div>
            <Button loading={broadcast.isPending} disabled={!message.trim()} onClick={() => broadcast.mutate()}>
              Send broadcast
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Zap className="h-5 w-5 text-warning" />
              <CardTitle>Resilience controls</CardTitle>
            </div>
            <CardDescription>
              Simulate a payment-provider outage to demonstrate the Resilience4j circuit breaker.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div
              className={cn(
                'flex items-center justify-between rounded-lg border p-4',
                outage && 'border-destructive/40 bg-destructive/5'
              )}
            >
              <div className="flex items-center gap-3">
                <AlertTriangle className={cn('h-5 w-5', outage ? 'text-destructive' : 'text-muted-foreground')} />
                <div>
                  <p className="font-medium">Payment gateway outage</p>
                  <p className="text-xs text-muted-foreground">
                    {outage ? 'Active — charges will fail and the breaker opens' : 'Normal operation'}
                  </p>
                </div>
              </div>
              <Button
                variant={outage ? 'destructive' : 'outline'}
                loading={toggleOutage.isPending}
                onClick={() => toggleOutage.mutate(!outage)}
              >
                {outage ? 'Restore' : 'Simulate outage'}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
