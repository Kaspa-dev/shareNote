import { Pencil, Trash2 } from "lucide-react";
import Modal from "../../components/Modal.jsx";
import { sections } from "./sections.js";
import { priorities } from "./priorities.js";

export default function DetailDialog({ item, section, data, canEdit, onClose, onDelete, onEdit }) {
  return (
    <Modal
      title={item.title || item.name}
      onClose={onClose}
    >
      <span className="detail-category">
        {item.category || sections[section]}
      </span>
      <p className="detail-content">
        {item.content ||
          item.description ||
          "Aprašymas nepridėtas."}
      </p>
      {section === "notes" && (
        <p className="detail-parent">Svarba: {priorities[item.priority || "MEDIUM"]}</p>
      )}
      {section === "notes" && (
        <p className="detail-parent">
          Grupė:{" "}
          {item.group.name}
        </p>
      )}
      {section === "groups" && (
        <p className="detail-parent">
          Organizacija:{" "}
          {data.organizations.find(
            (o) => o.id === item.organizationId,
          )?.name || "Organizacija nerasta"}
        </p>
      )}
      {canEdit && (
        <div className="modal-actions">
          <button
            className="danger-quiet"
            onClick={onDelete}
          >
            <Trash2 size={17} />
            Ištrinti
          </button>
          <button
            className="primary"
            onClick={onEdit}
          >
            <Pencil size={17} />
            Redaguoti
          </button>
        </div>
      )}
    </Modal>
  );
}
