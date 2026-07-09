import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";

const HOME = {
  PASSENGER: "/passenger",
  DRIVER: "/driver",
  ADMIN: "/admin",
};

export function homeFor(role) {
  return role ? HOME[role] : "/login";
}

export function ProtectedRoute({ role, children }) {
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
