import { NavLink, Outlet } from 'react-router-dom';
import {
  Car,
  LayoutDashboard,
  History,
  Wallet,
  Users,
  ShieldCheck,
  SlidersHorizontal,
  ScrollText,
  Moon,
  Sun,
  LogOut,
  Radio,
} from 'lucide-react';
import type { Role } from '@/types';
import { useAuthStore } from '@/store/authStore';
import { useLogout } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { NotificationBell } from './NotificationBell';
import { initials, cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
  end?: boolean;
}

const NAV: Record<Role, NavItem[]> = {
  PASSENGER: [
    { to: '/passenger', label: 'Book a ride', icon: <Car className="h-4 w-4" />, end: true },
    { to: '/passenger/history', label: 'Ride history', icon: <History className="h-4 w-4" /> },
  ],
  DRIVER: [
    { to: '/driver', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, end: true },
    { to: '/driver/earnings', label: 'Earnings', icon: <Wallet className="h-4 w-4" /> },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, end: true },
    { to: '/admin/drivers', label: 'Drivers', icon: <ShieldCheck className="h-4 w-4" /> },
    { to: '/admin/fare-rules', label: 'Fare rules', icon: <SlidersHorizontal className="h-4 w-4" /> },
    { to: '/admin/users', label: 'Users', icon: <Users className="h-4 w-4" /> },
    { to: '/admin/operations', label: 'Operations', icon: <Radio className="h-4 w-4" /> },
    { to: '/admin/audit', label: 'Audit & disputes', icon: <ScrollText className="h-4 w-4" /> },
  ],
};

export function AppLayout() {
  const { user, role } = useAuthStore();
  const logout = useLogout();
  const { dark, toggle } = useTheme();
  const items = role ? NAV[role] : [];

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col border-r bg-card md:flex">
        <div className="flex h-16 items-center gap-2 border-b px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Car className="h-5 w-5" />
          </div>
          <span className="text-lg font-bold tracking-tight">RideSwift</span>
        </div>
        <nav className="flex-1 space-y-1 p-3">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary/15 text-primary'
                    : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                )
              }
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t p-3">
          <div className="flex items-center gap-3 rounded-md px-3 py-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-secondary text-sm font-semibold">
              {user ? initials(user.name) : '?'}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.name}</p>
              <p className="truncate text-xs text-muted-foreground">{role}</p>
            </div>
          </div>
        </div>
      </aside>

      {/* Main */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-16 shrink-0 items-center justify-between border-b bg-card px-4 md:px-6">
          {/* Mobile brand + nav */}
          <div className="flex items-center gap-1 overflow-x-auto md:hidden">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-1.5 whitespace-nowrap rounded-md px-2.5 py-1.5 text-xs font-medium',
                    isActive ? 'bg-primary/15 text-primary' : 'text-muted-foreground'
                  )
                }
              >
                {item.icon}
                {item.label}
              </NavLink>
            ))}
          </div>
          <div className="hidden md:block" />
          <div className="flex items-center gap-1">
            <NotificationBell />
            <button
              onClick={toggle}
              className="rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-foreground"
              aria-label="Toggle theme"
            >
              {dark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </button>
            <button
              onClick={logout}
              className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground"
            >
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
