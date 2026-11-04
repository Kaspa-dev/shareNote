import { Link } from "react-router";
import { HelpCircle } from "lucide-react";

export default function Footer({ onHelp }) {
  return (
    <footer className="site-footer">
      <div>
        <Link
          className="footer-brand"
          to="/"
        >
          ShareNote.
        </Link>
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
