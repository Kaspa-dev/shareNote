import { useState } from "react";
import { BookOpen, ChevronRight, RefreshCw, Search } from "lucide-react";
import { sections, icons } from "./sections.js";
import { filterRecords } from "./filterRecords.js";
import { priorities } from "./priorities.js";

export default function Collection({ section, data, admin, loading, error, query, setQuery, filter, setFilter, refresh, openItem }) {
  const rows = filterRecords(data[section], section, query, filter);
  const [pageSize, setPageSize] = useState(10);
  const [pagination, setPagination] = useState({ key: "", page: 1 });
  const pageKey = JSON.stringify([section, query, filter, pageSize]);
  const pageCount = Math.max(1, Math.ceil(rows.length / pageSize));
  const page = pagination.key === pageKey ? Math.min(pagination.page, pageCount) : 1;
  if (pagination.key !== pageKey || pagination.page !== page) {
    setPagination({ key: pageKey, page });
  }
  const start = (page - 1) * pageSize;
  const visibleRows = rows.slice(start, start + pageSize);
  const Icon = icons[section];
  return (
    <section
      className="collection"
      aria-label={sections[section]}
      aria-busy={loading}
    >
      <div className="toolbar">
        <label className="search">
          <Search size={18} />
          <input
            type="search"
            aria-label="Ieškoti"
            placeholder="Ieškoti bibliotekoje…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        {section !== "organizations" && (
          <select
            aria-label={
              section === "notes"
                ? "Filtruoti pagal grupę"
                : "Filtruoti pagal organizaciją"
            }
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="">
              {section === "notes"
                ? "Visos grupės"
                : "Visos organizacijos"}
            </option>
            {data[section === "notes" ? "groups" : "organizations"].map(
              (g) => (
                <option value={g.id} key={g.id}>
                  {g.name}
                </option>
              ),
            )}
          </select>
        )}
        <button
          className="icon-button"
          aria-label="Atnaujinti sąrašą"
          disabled={loading}
          onClick={refresh}
        >
          <RefreshCw size={18} className={loading ? "spin" : ""} />
        </button>
      </div>
      {error && (
        <div className="error" role="alert">
          {error}
          <button
            className="text-button"
            onClick={refresh}
          >
            Bandyti dar kartą
          </button>
        </div>
      )}
      <div className="list-heading">
        <span>
          {query || filter ? "PAIEŠKOS REZULTATAI" : "VISI ĮRAŠAI"}
        </span>
        <span>{rows.length} įrašų</span>
      </div>
      {loading ? (
        <div className="empty" role="status">
          Kraunami įrašai…
        </div>
      ) : error ? null : rows.length ? (
        <div className="record-list">
          {visibleRows.map((item) => (
            <button
              className="record"
              key={item.id}
              onClick={() => openItem(item)}
            >
              <span className="record-icon">
                <Icon size={22} />
              </span>
              <span className="record-body">
                <span className="record-meta">
                  {section === "notes" ? (
                    <>
                      {item.category}
                      <span>·</span>
                      Svarba: {priorities[item.priority || "MEDIUM"]}
                      <span>·</span>
                      {data.groups.find((g) => g.id === item.groupId)
                        ?.name || "Grupė"}
                    </>
                  ) : section === "groups" ? (
                    data.organizations.find(
                      (o) => o.id === item.organizationId,
                    )?.name || "Organizacija nerasta"
                  ) : (
                    "Organizacija"
                  )}
                </span>
                <strong>{item.title || item.name}</strong>
                <span className="excerpt">
                  {item.content ||
                    item.description ||
                    "Aprašymas nepridėtas."}
                </span>
              </span>
              <ChevronRight className="record-arrow" size={20} />
            </button>
          ))}
        </div>
      ) : (
        <div className="empty">
          <img src="/notebook.svg" alt="Užrašinė" />
          <h2>
            {query || filter ? "Įrašų nerasta" : "Čia dar nėra įrašų"}
          </h2>
          <p>
            {query || filter
              ? "Pakeiskite paiešką arba pasirinktą filtrą."
              : admin
                ? "Pradėkite sukurdami pirmąjį įrašą."
                : "Administratorius dar nepridėjo įrašų."}
          </p>
          {(query || filter) && (
            <button
              className="secondary"
              onClick={() => {
                setQuery("");
                setFilter("");
              }}
            >
              Išvalyti filtrus
            </button>
          )}
        </div>
      )}
      {!loading && !error && rows.length > 0 && (
        <nav className="pagination" aria-label="Puslapiavimas">
          <label>
            Įrašų puslapyje
            <select aria-label="Įrašų puslapyje" value={pageSize}
              onChange={(event) => setPageSize(Number(event.target.value))}>
              {[10, 25, 50].map((size) => <option key={size} value={size}>{size}</option>)}
            </select>
          </label>
          <span role="status">{start + 1}–{Math.min(start + pageSize, rows.length)} iš {rows.length} · {page} / {pageCount}</span>
          <div className="pagination-actions">
            <button className="secondary" disabled={page === 1}
              onClick={() => setPagination({ key: pageKey, page: page - 1 })}>Ankstesnis</button>
            <button className="secondary" disabled={page === pageCount}
              onClick={() => setPagination({ key: pageKey, page: page + 1 })}>Kitas</button>
          </div>
        </nav>
      )}
      <div className="collection-bottom">
        <span>
          <BookOpen size={15} />
          Bendros žinios prasideda nuo vieno užrašo.
        </span>
        <span>ShareNote</span>
      </div>
    </section>
  );
}
