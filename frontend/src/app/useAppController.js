import { useEffect, useState } from "react";
import { readSession, request, resourceLink, saveSession, endSession } from "../services/api.js";
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
  useEffect(() => {
    setData(emptyData);
    setModal(null);
    setMenu(false);
    setError("");
  }, [session?.sessionId]);
  const admin = session?.role === "ADMIN";
  const editor = session?.role === "EDITOR";
  const canCreate = admin || (editor && section === "notes");
  const canEdit = admin || (editor && section === "notes" && session?.userId != null &&
    modal?.item?.ownerId != null && String(modal.item.ownerId) === String(session.userId));
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
      const fresh = await request(resourceLink(item), {
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
      await request(resourceLink(modal.item), {
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
    setSession(saveSession(value));
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
    busy, admin, editor, canCreate, canEdit, logout, expired, navigate, openItem, remove, login, refresh, saved,
  };
}
