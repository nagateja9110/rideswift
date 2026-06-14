import { Navigate, useLocation } from 'react-router-dom';
import type { Role } from '@/types';
import { useAuthStore } from '@/store/authStore';

const HOME: Record<Role, string> = {
  PASSENGER: '/passenger',
  DRIVER: '/driver',
  ADMIN: '/admin',
};

export function homeFor(role: Role | null): string {
  return role ? HOME[role] : '/login';
}

export function ProtectedRoute({ role, children }: { role?: Role; children: React.ReactNode }) {
  const { isAuthenticated, role: userRole } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (role && userRole !== role) {
    return <Navigate to={homeFor(userRole)} replace />;
  }
  return <>{children}</>;
}
