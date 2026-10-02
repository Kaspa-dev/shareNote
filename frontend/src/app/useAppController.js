import { useState } from "react";
import { readSession, request, saveSession, endSession } from "../services/api.js";
import { emptyData } from "../features/library/sections.js";
import useSessionRenewal from "../features/auth/useSessionRenewal.js";
import useLibraryData from "../features/library/useLibraryData.js";

export default function useAppController() {
  const [session, setSession] = useState(readSession);
  const [section, setSection] = useState("notes");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("");
  const [menu, setMenu] = useState(false);
  const [modal, setModal] = useState(null);
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const { data, setData, loading, error, setError } = useLibraryData(session, revision, expired);
  const admin = session?.role === "ADMIN";
  function logout(message = "") {
    endSession().catch(() => setNotice("Atsijungta šiame įrenginyje. Serverio sesijos atšaukti nepavyko."));
    setSession(null);
    setData(emptyData);
    setModal(null);
    setMenu(false);
    setError("");
    setNotice(message);
  }
  function expired() {
    logout("Sesija pasibaigė. Prisijunkite iš naujo.");
  }
  useSessionRenewal(session, setSession, expired);
  function navigate(key) {
    setSection(key);
    setQuery("");
    setFilter("");
    setMenu(false);
  }
  async function openItem(item) {
    setError("");
    try {
      const fresh = await request(`${section}/${item.id}`, {
        token: session.token,
      });
      setModal({ type: "detail", item: fresh });
    } catch (e) {
      if (e.status === 401) expired();
      else setError(e.message);
    }
  }
  async function remove() {
    setBusy(true);
    try {
      await request(`${section}/${modal.item.id}`, {
        token: session.token,
        method: "DELETE",
      });
      setModal(null);
      setNotice("Įrašas ištrintas.");
      setRevision((r) => r + 1);
    } catch (e) {
      if (e.status === 401) expired();
      else setModal((m) => ({ ...m, error: e.message }));
    } finally {
      setBusy(false);
    }
  }
  function login(value) {
    saveSession(value);
    setSession(value);
    setNotice("");
  }
  function refresh() {
    setRevision((r) => r + 1);
  }
  function saved() {
    setModal(null);
    setNotice("Pakeitimai išsaugoti.");
    refresh();
  }
  return {
    session, section, data, query, setQuery, filter, setFilter,
    menu, setMenu, modal, setModal, loading, error, notice, setNotice,
    busy, admin, logout, expired, navigate, openItem, remove, login, refresh, saved,
  };
}
