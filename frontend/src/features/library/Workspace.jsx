import { ChevronRight, Plus } from "lucide-react";
import { sections, singular } from "./sections.js";
import Sidebar from "./Sidebar.jsx";
import Collection from "./Collection.jsx";

export default function Workspace({ section, data, admin, navigate, setModal, loading, error, query, setQuery, filter, setFilter, refresh, openItem }) {
  return (
    <main id="main" className="workspace">
      <div className="breadcrumb">
        Darbo erdvė
        <ChevronRight size={14} />
        <span>{sections[section]}</span>
      </div>
      <div className="page-heading">
        <div>
          <span className="eyebrow">JŪSŲ BENDRA BIBLIOTEKA</span>
          <h1>
            {sections[section]}
            <span className="count">{data[section].length}</span>
          </h1>
          <p>
            {section === "notes"
              ? "Idėjos, planai ir žinios iš jūsų grupių."
              : section === "groups"
                ? "Bendros temos ir komandos vienoje vietoje."
                : "Erdvės, kuriose susitinka jūsų komandos."}
          </p>
        </div>
        {admin && (
          <button
            className="primary"
            onClick={() => setModal({ type: "edit" })}
          >
            <Plus size={18} />
            Sukurti {singular[section]}
          </button>
        )}
      </div>
      <div className="workspace-grid">
        <Sidebar section={section} data={data} admin={admin} navigate={navigate} onHelp={() => setModal({ type: "help" })} />
        <Collection section={section} data={data} admin={admin} loading={loading} error={error}
          query={query} setQuery={setQuery} filter={filter} setFilter={setFilter}
          refresh={refresh} openItem={openItem} />
      </div>
    </main>
  );
}
