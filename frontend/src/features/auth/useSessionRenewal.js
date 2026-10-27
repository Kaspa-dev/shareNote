import { useEffect, useRef } from "react";
import { readSession, renewSession } from "../../services/api.js";

export default function useSessionRenewal(session, setSession, onExpired) {
  const expired = useRef(onExpired);
  expired.current = onExpired;
  useEffect(() => {
    const sync = () => setSession(readSession());
    const storage = (event) => { if (event.key === "sharenote-session" || event.key === null) sync(); };
    window.addEventListener("storage", storage);
    window.addEventListener("session-renewed", sync);
    return () => {
      window.removeEventListener("storage", storage);
      window.removeEventListener("session-renewed", sync);
    };
  }, [setSession]);
  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    let timer;
    const renew = async () => {
      try { await renewSession(); }
      catch (e) {
        if (cancelled || e.name === "AbortError") return;
        if (e.status === 401) expired.current();
        else timer = setTimeout(renew, 30000);
      }
    };
    timer = setTimeout(renew, Math.max(0, session.expiresAt - Date.now() - 60000));
    return () => { cancelled = true; clearTimeout(timer); };
  }, [session]);
}
