import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Car, User } from 'lucide-react';
import { authApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { useToast } from '@/components/ui/toast';
import { cn } from '@/lib/utils';
import type { Role } from '@/types';
import { AuthShell } from './AuthShell';

const schema = z.object({
  name: z.string().min(2, 'Name is required'),
  email: z.string().email('Enter a valid email'),
  phone: z.string().regex(/^\+?[0-9]{7,15}$/, 'Enter a valid phone number'),
  password: z.string().min(8, 'At least 8 characters'),
});
type Form = z.infer<typeof schema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const { toast } = useToast();
  const [role, setRole] = useState<Role>('PASSENGER');
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema),
  });

  const submit = async (data: Form) => {
    setLoading(true);
    try {
      const auth = await authApi.register({ ...data, role });
      setSession(auth);
      toast('Account created!', 'success');
      navigate(role === 'DRIVER' ? '/driver/onboarding' : '/passenger');
    } catch (e) {
      toast(apiError(e), 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="mb-6">
        <h2 className="text-2xl font-bold">Create account</h2>
        <p className="mt-1 text-sm text-muted-foreground">Join RideSwift in a few seconds.</p>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3">
        <RoleCard active={role === 'PASSENGER'} onClick={() => setRole('PASSENGER')} icon={<User className="h-5 w-5" />} label="Passenger" desc="Book rides" />
        <RoleCard active={role === 'DRIVER'} onClick={() => setRole('DRIVER')} icon={<Car className="h-5 w-5" />} label="Driver" desc="Earn money" />
      </div>

      <form onSubmit={handleSubmit(submit)} className="space-y-4">
        <Field label="Full name" error={errors.name?.message}>
          <Input placeholder="Jane Doe" {...register('name')} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <Input type="email" placeholder="you@example.com" {...register('email')} />
        </Field>
        <Field label="Phone" error={errors.phone?.message}>
          <Input placeholder="+15551234567" {...register('phone')} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input type="password" placeholder="At least 8 characters" {...register('password')} />
        </Field>
        <Button type="submit" className="w-full" loading={loading}>
          Create account
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

function RoleCard({
  active,
  onClick,
  icon,
  label,
  desc,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  desc: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex flex-col items-start gap-1 rounded-lg border p-4 text-left transition-colors',
        active ? 'border-primary bg-primary/10' : 'border-border hover:border-muted-foreground/40'
      )}
    >
      <div className={cn('rounded-md p-1.5', active ? 'text-primary' : 'text-muted-foreground')}>{icon}</div>
      <span className="font-medium">{label}</span>
      <span className="text-xs text-muted-foreground">{desc}</span>
    </button>
  );
}
