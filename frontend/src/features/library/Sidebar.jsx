import { ArrowRight, BookOpen } from "lucide-react";
import { sections, icons } from "./sections.js";

export default function Sidebar({ section, data, admin, editor, navigate, onHelp }) {
  return (
    <aside className="sidebar">
      <span className="eyebrow">BIBLIOTEKA</span>
      {Object.entries(sections).map(([key, name]) => {
        const NavIcon = icons[key];
        return (
          <button
            className={section === key ? "selected" : ""}
            key={key}
            onClick={() => navigate(key)}
          >
            <NavIcon size={19} />
            {name}
            <span>{data[key].length}</span>
          </button>
        );
      })}
      <div className="sidebar-info">
        <BookOpen size={24} />
        <h3>Viskas savo vietoje.</h3>
        <p>Organizacijos jungia grupes, o grupės – bendrus užrašus.</p>
        <button
          className="text-button"
          onClick={onHelp}
        >
          Kaip tai veikia
          <ArrowRight size={15} />
        </button>
      </div>
      <div className="access-note">
        <span className="status-dot" />
        {admin ? "Galite kurti ir redaguoti" : editor ? "Galite valdyti savo užrašus" : "Peržiūros prieiga"}
      </div>
    </aside>
  );
}
