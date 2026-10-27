import { useEffect, useRef, useState } from "react";
import { request, resourceLink, libraryEntry } from "../../services/api.js";
import { emptyData } from "./sections.js";

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
    async function load() {
      const options = { token: session.token, signal: controller.signal };
      const organizations = await request(libraryEntry, options);
      const groups = (await Promise.all(organizations.map((organization) =>
        request(resourceLink(organization, "groups"), options),
      ))).flat();
      const notes = (await Promise.all(groups.map((group) =>
        request(resourceLink(group, "notes"), options),
      ))).flat();
      if (!controller.signal.aborted) setData({ notes, groups, organizations });
    }
    load()
      .catch((e) => {
        if (controller.signal.aborted || e.name === "AbortError") return;
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
