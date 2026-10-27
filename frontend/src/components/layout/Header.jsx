import { BookOpen, LogOut, Menu, X } from "lucide-react";
import { sections } from "../../features/library/sections.js";

export default function Header({ session, section, menu, setMenu, navigate, logout }) {
  return (
    <header className="site-header">
      <div className="header-inner">
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate("notes");
          }}
        >
          <span className="brand-icon">
            <BookOpen size={23} />
          </span>
          ShareNote<span className="brand-dot">.</span>
        </a>
        {session && (
          <>
            <button
              className="hamburger icon-button"
              aria-label={menu ? "Uždaryti meniu" : "Atidaryti meniu"}
              aria-expanded={menu}
              aria-controls="navigation"
              onClick={() => setMenu(!menu)}
            >
              {menu ? <X /> : <Menu />}
            </button>
            <nav
              id="navigation"
              aria-label="Pagrindinis meniu"
              className={menu ? "open" : ""}
            >
              {Object.entries(sections).map(([key, name]) => (
                <button
                  key={key}
                  aria-current={section === key ? "page" : undefined}
                  onClick={() => navigate(key)}
                >
                  {name}
                </button>
              ))}
              <button className="mobile-logout" onClick={() => logout()}>
                Atsijungti
              </button>
            </nav>
            <div className="account">
              <span className="avatar">
                {session.username.slice(0, 1).toUpperCase()}
              </span>
              <span>
                {session.username}
                <small>{session.role === "ADMIN" ? "Administratorius" : session.role === "EDITOR" ? "Redaktorius" : "Skaitytojas"}</small>
              </span>
              <button
                className="icon-button"
                aria-label="Atsijungti"
                onClick={() => logout()}
              >
                <LogOut size={18} />
              </button>
            </div>
          </>
        )}
        {!session && (
          <span className="header-caption">Užrašai, kurie sujungia.</span>
        )}
      </div>
    </header>
  );
}
