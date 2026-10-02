import { HelpCircle } from "lucide-react";

export default function Footer({ navigate, onHelp }) {
  return (
    <footer className="site-footer">
      <div>
        <a
          className="footer-brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate("notes");
          }}
        >
          ShareNote.
        </a>
        <p>Bendra erdvė jūsų žinioms.</p>
      </div>
      <span>© {new Date().getFullYear()} ShareNote</span>
      <button onClick={onHelp}>
        <HelpCircle size={16} />
        Apie ir pagalba
      </button>
    </footer>
  );
}
