import Workspace from "../features/library/Workspace.jsx";

export function NotesPage(props) {
  return <Workspace {...props} section="notes" />;
}

export function GroupsPage(props) {
  return <Workspace {...props} section="groups" />;
}

export function OrganizationsPage(props) {
  return <Workspace {...props} section="organizations" />;
}
