import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuthBootstrap } from '@/hooks/useAuth';
import { useAuthStore } from '@/store/authStore';
import { ProtectedRoute, homeFor } from '@/components/common/ProtectedRoute';
import { AppLayout } from '@/components/common/AppLayout';

import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';

import { BookRidePage } from '@/pages/passenger/BookRidePage';
import { RideHistoryPage } from '@/pages/passenger/RideHistoryPage';

import { DriverDashboardPage } from '@/pages/driver/DriverDashboardPage';
import { DriverOnboardingPage } from '@/pages/driver/DriverOnboardingPage';
import { EarningsPage } from '@/pages/driver/EarningsPage';

import { AdminDashboardPage } from '@/pages/admin/AdminDashboardPage';
import { DriverManagementPage } from '@/pages/admin/DriverManagementPage';
import { OperationsPage } from '@/pages/admin/OperationsPage';
import { FareRulesPage } from '@/pages/admin/FareRulesPage';
import { UsersPage } from '@/pages/admin/UsersPage';
import { AuditLogPage } from '@/pages/admin/AuditLogPage';

export default function App() {
  useAuthBootstrap();
  const role = useAuthStore((s) => s.role);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Passenger */}
      <Route
        element={
          <ProtectedRoute role="PASSENGER">
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/passenger" element={<BookRidePage />} />
        <Route path="/passenger/history" element={<RideHistoryPage />} />
      </Route>

      {/* Driver */}
      <Route path="/driver/onboarding" element={<ProtectedRoute role="DRIVER"><DriverOnboardingPage /></ProtectedRoute>} />
      <Route
        element={
          <ProtectedRoute role="DRIVER">
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/driver" element={<DriverDashboardPage />} />
        <Route path="/driver/earnings" element={<EarningsPage />} />
      </Route>

      {/* Admin */}
      <Route
        element={
          <ProtectedRoute role="ADMIN">
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/admin/drivers" element={<DriverManagementPage />} />
        <Route path="/admin/operations" element={<OperationsPage />} />
        <Route path="/admin/fare-rules" element={<FareRulesPage />} />
        <Route path="/admin/users" element={<UsersPage />} />
        <Route path="/admin/audit" element={<AuditLogPage />} />
      </Route>

      <Route path="*" element={<Navigate to={homeFor(role)} replace />} />
    </Routes>
  );
}
