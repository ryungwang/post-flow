import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/store/auth";

/** Gate for protected routes — redirect to /login when there's no token. */
export function RequireAuth() {
  const token = useAuth((s) => s.token);
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
