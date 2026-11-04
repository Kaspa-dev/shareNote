import { Navigate, Outlet, useLocation } from "react-router";

export default function RequireSession({ session }) {
  const location = useLocation();
  return session ? <Outlet /> : (
    <Navigate to="/login" replace state={{ from: location.pathname + location.search + location.hash }} />
  );
}
