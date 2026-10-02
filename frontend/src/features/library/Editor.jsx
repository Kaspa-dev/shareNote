import { useState } from "react";
import Modal from "../../components/Modal.jsx";
import { request } from "../../services/api.js";
import { singular } from "./sections.js";

export default function Editor({ section, item, data, token, onSaved, onClose, onExpired }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const note = section === "notes";
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const body = Object.fromEntries(new FormData(event.currentTarget));
    if (note) body.groupId = Number(body.groupId);
    if (section === "groups")
      body.organizationId = body.organizationId
        ? Number(body.organizationId)
        : null;
    try {
      await request(`${section}${item ? `/${item.id}` : ""}`, {
        token,
        method: item ? "PUT" : "POST",
        body,
      });
      onSaved();
    } catch (e) {
      if (e.status === 401) onExpired();
      else setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={`${item ? "Redaguoti" : "Sukurti"} ${singular[section]}`}
      onClose={onClose}
      busy={busy}
    >
      <form onSubmit={submit}>
        <label>
          {note ? "Antraštė" : "Pavadinimas"}
          <input
            autoFocus
            name={note ? "title" : "name"}
            defaultValue={item?.title || item?.name || ""}
            required
            maxLength={note ? 200 : 120}
            pattern=".*\S.*"
          />
        </label>
        {note && (
          <div className="form-grid">
            <label>
              Kategorija
              <input
                name="category"
                defaultValue={item?.category || ""}
                required
                maxLength={100}
                pattern=".*\S.*"
                list="categories"
                placeholder="pvz., Mokymasis"
              />
              <datalist id="categories">
                {[...new Set(data.notes.map((n) => n.category))].map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </label>
            <label>
              Grupė
              <select
                aria-label="Grupė"
                name="groupId"
                defaultValue={item?.groupId || ""}
                required
              >
                <option value="" disabled>
                  Pasirinkite grupę
                </option>
                {data.groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
        {section === "groups" && (
          <label>
            Organizacija
            <select
              aria-label="Organizacija"
              name="organizationId"
              defaultValue={item?.organizationId || ""}
            >
              <option value="">Savarankiška grupė</option>
              {data.organizations.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          {note ? "Užrašo turinys" : "Aprašymas"}
          <textarea
            name={note ? "content" : "description"}
            defaultValue={item?.content || item?.description || ""}
            rows={7}
            required={note}
            maxLength={note ? 20000 : 2000}
          />
        </label>
        <small>
          {note
            ? "Iki 20 000 simbolių. Užrašą matys prisijungę naudotojai."
            : "Iki 2 000 simbolių."}
        </small>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <div className="modal-actions">
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={onClose}
          >
            Atšaukti
          </button>
          <button
            className="primary"
            disabled={busy || (note && !data.groups.length)}
          >
            {busy ? "Saugoma…" : "Išsaugoti"}
          </button>
        </div>
        {note && !data.groups.length && (
          <p className="error">Pirmiausia sukurkite grupę.</p>
        )}
      </form>
    </Modal>
  );
}
