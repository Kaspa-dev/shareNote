import { Check, X } from "lucide-react";

export default function Notice({ notice, onDismiss }) {
  return (
    <div className="notice" role="status">
      <Check size={18} />
      {notice}
      <button
        className="icon-button"
        aria-label="Slėpti pranešimą"
        onClick={onDismiss}
      >
        <X size={16} />
      </button>
    </div>
  );
}
