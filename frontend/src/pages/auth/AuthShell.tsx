import { Car, MapPin, Zap, ShieldCheck } from 'lucide-react';

export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-background">
      {/* Brand panel */}
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-primary p-12 text-primary-foreground lg:flex">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/20">
            <Car className="h-5 w-5" />
          </div>
          <span className="text-xl font-bold">RideSwift</span>
        </div>
        <div className="relative z-10 space-y-6">
          <h1 className="text-4xl font-bold leading-tight">
            Your ride, matched in seconds.
          </h1>
          <p className="max-w-md text-primary-foreground/80">
            Real-time driver matching, dynamic fares, live tracking and atomic payments —
            a production-grade ride-sharing platform.
          </p>
          <div className="space-y-3 pt-4">
            <Feature icon={<Zap className="h-5 w-5" />} text="Nearest-driver matching with PostGIS + Redis" />
            <Feature icon={<MapPin className="h-5 w-5" />} text="Live GPS tracking over WebSockets" />
            <Feature icon={<ShieldCheck className="h-5 w-5" />} text="Secure, atomic multi-gateway payments" />
          </div>
        </div>
        <p className="relative z-10 text-sm text-primary-foreground/60">© 2026 RideSwift</p>
        <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-black/10 blur-3xl" />
      </div>

      {/* Form panel */}
      <div className="flex w-full flex-col items-center justify-center px-6 py-12 lg:w-1/2">
        <div className="w-full max-w-sm">{children}</div>
      </div>
    </div>
  );
}

function Feature({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15">{icon}</div>
      <span className="text-sm text-primary-foreground/90">{text}</span>
    </div>
  );
}
