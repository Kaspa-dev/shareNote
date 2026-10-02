import { useEffect, useRef, useState } from "react";
import { request } from "../../services/api.js";
import { sections, emptyData } from "./sections.js";

export default function useLibraryData(session, revision, onExpired) {
  const [data, setData] = useState(emptyData);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const expired = useRef(onExpired);
  expired.current = onExpired;
  useEffect(() => {
    if (!session) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    Promise.all(
      Object.keys(sections).map((key) =>
        request(key, { token: session.token, signal: controller.signal }),
      ),
    )
      .then(([notes, groups, organizations]) =>
        setData({ notes, groups, organizations }),
      )
      .catch((e) => {
        if (e.name === "AbortError") return;
        if (e.status === 401) expired.current();
        else setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [session, revision]);
  return { data, setData, loading, error, setError };
}
