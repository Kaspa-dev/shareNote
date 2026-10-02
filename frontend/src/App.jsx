import Header from "./components/layout/Header.jsx";
import Footer from "./components/layout/Footer.jsx";
import Notice from "./components/Notice.jsx";
import HelpDialog from "./components/HelpDialog.jsx";
import Auth from "./features/auth/Auth.jsx";
import Workspace from "./features/library/Workspace.jsx";
import Editor from "./features/library/Editor.jsx";
import DetailDialog from "./features/library/DetailDialog.jsx";
import DeleteDialog from "./features/library/DeleteDialog.jsx";
import useAppController from "./app/useAppController.js";

export default function App() {
  const {
    session, section, data, query, setQuery, filter, setFilter,
    menu, setMenu, modal, setModal, loading, error, notice, setNotice,
    busy, admin, logout, expired, navigate, openItem, remove, login, refresh, saved,
  } = useAppController();
  const closeModal = () => setModal(null);

  return (
    <>
      <a className="skip-link" href="#main">Pereiti prie turinio</a>
      <Header session={session} section={section} menu={menu} setMenu={setMenu}
        navigate={navigate} logout={logout} />
      {notice && <Notice notice={notice} onDismiss={() => setNotice("")} />}
      {!session ? <Auth onLogin={login} /> : (
        <Workspace section={section} data={data} admin={admin} navigate={navigate}
          setModal={setModal} loading={loading} error={error} query={query}
          setQuery={setQuery} filter={filter} setFilter={setFilter}
          refresh={refresh} openItem={openItem} />
      )}
      <Footer navigate={navigate} onHelp={() => setModal({ type: "help" })} />
      {modal?.type === "help" && <HelpDialog onClose={closeModal} />}
      {modal?.type === "edit" && (
        <Editor section={section} item={modal.item} data={data} token={session.token}
          onExpired={expired} onClose={closeModal} onSaved={saved} />
      )}
      {modal?.type === "detail" && (
        <DetailDialog item={modal.item} section={section} data={data} admin={admin}
          onClose={closeModal}
          onDelete={() => setModal({ type: "delete", item: modal.item })}
          onEdit={() => setModal({ type: "edit", item: modal.item })} />
      )}
      {modal?.type === "delete" && (
        <DeleteDialog item={modal.item} section={section} error={modal.error}
          busy={busy} onClose={closeModal} onConfirm={remove} />
      )}
    </>
  );
}
