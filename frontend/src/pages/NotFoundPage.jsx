import { Link } from "react-router";

export default function NotFoundPage() {
  return (
    <main id="main" className="workspace">
      <div className="page-heading">
        <div>
          <span className="eyebrow">404</span>
          <h1>Puslapis nerastas</h1>
          <p>Šio puslapio nėra. Patikrinkite adresą arba grįžkite į pradžią.</p>
          <Link className="text-button" to="/">Grįžti į pradžią</Link>
        </div>
      </div>
    </main>
  );
}
