import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth, UserRole } from '../contexts/AuthContext';

/**
 * Route guard: renders child routes only when a session with the required
 * role exists. Anonymous visitors go to the login screen; a logged-in user
 * of the other role is sent to their own home instead.
 */
export const RequireAuth: React.FC<{ role: UserRole }> = ({ role }) => {
  const { session, loading } = useAuth();
  const location = useLocation();

  if (loading) return null; // session still being restored from storage

  if (!session) {
    return <Navigate to={`/login?role=${role}`} replace state={{ from: location }} />;
  }
  if (session.role !== role) {
    return <Navigate to={session.role === 'teacher' ? '/teacher/dashboard' : '/student/home'} replace />;
  }
  return <Outlet />;
};
