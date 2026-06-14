import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';
import type { RideStatus, PaymentStatus, VerificationStatus } from '@/types';

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary/15 text-primary',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        success: 'border-transparent bg-success/15 text-success',
        warning: 'border-transparent bg-warning/15 text-warning',
        destructive: 'border-transparent bg-destructive/15 text-destructive',
        outline: 'text-foreground',
        muted: 'border-transparent bg-muted text-muted-foreground',
      },
    },
    defaultVariants: { variant: 'default' },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}

const RIDE_VARIANT: Record<RideStatus, BadgeProps['variant']> = {
  SCHEDULED: 'secondary',
  REQUESTED: 'warning',
  MATCHED: 'default',
  IN_PROGRESS: 'default',
  COMPLETED: 'success',
  CANCELLED: 'destructive',
  EXPIRED: 'destructive',
};
const RIDE_LABEL: Record<RideStatus, string> = {
  SCHEDULED: 'Scheduled',
  REQUESTED: 'Searching',
  MATCHED: 'Matched',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'No drivers',
};

export function RideStatusBadge({ status }: { status: RideStatus }) {
  return <Badge variant={RIDE_VARIANT[status]}>{RIDE_LABEL[status]}</Badge>;
}

const PAY_VARIANT: Record<PaymentStatus, BadgeProps['variant']> = {
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'destructive',
  REFUNDED: 'muted',
};
export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge variant={PAY_VARIANT[status]}>{status}</Badge>;
}

const VERIFY_VARIANT: Record<VerificationStatus, BadgeProps['variant']> = {
  PENDING: 'warning',
  VERIFIED: 'success',
  REJECTED: 'destructive',
};
export function VerificationBadge({ status }: { status: VerificationStatus }) {
  return <Badge variant={VERIFY_VARIANT[status]}>{status}</Badge>;
}
