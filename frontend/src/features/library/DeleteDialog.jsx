import Modal from "../../components/Modal.jsx";
import { singular } from "./sections.js";

export default function DeleteDialog({ item, section, error, busy, onClose, onConfirm }) {
  return (
    <Modal
      title={`Ištrinti ${singular[section]}?`}
      busy={busy}
      onClose={onClose}
    >
      <p>
        Įrašas „{item.title || item.name}“ bus pašalintas visam
        laikui.
      </p>
      {section !== "notes" && (
        <p>
          Pirmiausia turi būti pašalinti šio įrašo{" "}
          {section === "groups" ? "užrašai" : "grupės"}.
        </p>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <div className="modal-actions">
        <button
          className="secondary"
          disabled={busy}
          onClick={onClose}
        >
          Atšaukti
        </button>
        <button className="danger" disabled={busy} onClick={onConfirm}>
          {busy ? "Trinama…" : "Taip, ištrinti"}
        </button>
      </div>
    </Modal>
  );
}
