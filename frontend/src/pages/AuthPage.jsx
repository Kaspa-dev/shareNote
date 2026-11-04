import { Navigate, useLocation } from "react-router";
import Auth from "../features/auth/Auth.jsx";

export default function AuthPage({ session, onLogin, register = false }) {
  const location = useLocation();
  const from = location.state?.from;
  const destination = typeof from === "string" && /^\/(notes|groups|organizations)\/?(?:[?#].*)?$/.test(from)
    ? from : "/notes";
  if (session) return <Navigate to={destination} replace />;
  return <Auth key={register ? "register" : "login"} register={register} onLogin={onLogin} />;
}
