import Header from "./components/layout/Header.jsx";
import Footer from "./components/layout/Footer.jsx";
import Notice from "./components/Notice.jsx";
import HelpDialog from "./components/HelpDialog.jsx";
import { Navigate, Route, Routes } from "react-router";
import AuthPage from "./pages/AuthPage.jsx";
import { NotesPage, GroupsPage, OrganizationsPage } from "./pages/LibraryPages.jsx";
import NotFoundPage from "./pages/NotFoundPage.jsx";
import RequireSession from "./app/RequireSession.jsx";
import Editor from "./features/library/Editor.jsx";
import DetailDialog from "./features/library/DetailDialog.jsx";
import DeleteDialog from "./features/library/DeleteDialog.jsx";
import useAppController from "./app/useAppController.js";

export default function App() {
  const controller = useAppController();
  const {
    session, section, data,
    menu, setMenu, modal, setModal, notice, setNotice,
    busy, canEdit, logout, expired, remove, login, saved,
  } = controller;
  const closeModal = () => setModal(null);

  return (
    <>
      <a className="skip-link" href="#main">Pereiti prie turinio</a>
      <Header session={session} menu={menu} setMenu={setMenu} logout={logout} />
      {notice && <Notice notice={notice} onDismiss={() => setNotice("")} />}
      <Routes>
        <Route path="/" element={<Navigate to={session ? "/notes" : "/login"} replace />} />
        <Route path="/login" element={<AuthPage session={session} onLogin={login} />} />
        <Route path="/register" element={<AuthPage session={session} onLogin={login} register />} />
        <Route element={<RequireSession session={session} />}>
          <Route path="/notes" element={<NotesPage {...controller} />} />
          <Route path="/groups" element={<GroupsPage {...controller} />} />
          <Route path="/organizations" element={<OrganizationsPage {...controller} />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      <Footer onHelp={() => setModal({ type: "help" })} />
      {modal?.type === "help" && <HelpDialog onClose={closeModal} />}
      {session && section && modal?.type === "edit" && (
        <Editor section={section} item={modal.item} data={data} token={session.token}
          onExpired={expired} onClose={closeModal} onSaved={saved} />
      )}
      {session && section && modal?.type === "detail" && (
        <DetailDialog item={modal.item} section={section} data={data} canEdit={canEdit}
          onClose={closeModal}
          onDelete={() => setModal({ type: "delete", item: modal.item })}
          onEdit={() => setModal({ type: "edit", item: modal.item })} />
      )}
      {session && section && modal?.type === "delete" && (
        <DeleteDialog item={modal.item} section={section} error={modal.error}
          busy={busy} onClose={closeModal} onConfirm={remove} />
      )}
    </>
  );
}
