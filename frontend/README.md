# Frontend

React and Vite client for ShareNote. Run commands from this directory:

```sh
npm ci
npm run dev
npm run build
npm test
```

The development server proxies `/api` using `vite.config.js`. Browser tests mock
the API and do not need the backend running. Playwright requires an installed
browser; use `npx playwright install chromium` if it is missing.

## Source structure

```text
src/
  main.jsx                    React entry point, fonts and global CSS
  App.jsx                     Composes the page and selects active dialogs
  app/
    useAppController.js       Navigation, session state and user actions
  components/
    Modal.jsx                 Shared dialog behavior and focus handling
    Notice.jsx                Dismissible status message
    HelpDialog.jsx            Application help
    layout/
      Header.jsx              Branding, navigation and account controls
      Footer.jsx              Footer and help entry point
  features/
    auth/
      Auth.jsx                Login and registration form
      useSessionRenewal.js    Session synchronization and renewal timer
    library/
      Workspace.jsx           Library page composition and heading
      Sidebar.jsx             Library navigation and counts
      Collection.jsx          Search controls, records and empty states
      Editor.jsx              Create and edit forms
      DetailDialog.jsx        Record details and available actions
      DeleteDialog.jsx        Delete confirmation and errors
      useLibraryData.js       Data loading, cancellation and loading errors
      filterRecords.js        Localized search and relationship filtering
      sections.js             Section names, icons and empty data shape
  services/
    api.js                    HTTP requests, session storage and token renewal
  styles/
    global.css                Existing shared styles and responsive rules
tests/                        Playwright behavior and session-renewal tests
public/                       Assets served directly by Vite
```

## Where new code belongs

- Keep `App.jsx` focused on composition. Application-wide transitions, such as
  logging out, navigating or opening a dialog, belong in `app/`.
- Keep a feature's views, hooks, constants and pure helpers together in
  `features/<feature>/`. Notes, groups and organizations share the same library
  workflow, so they share components instead of duplicating three CRUD screens.
- Put reusable UI and the application shell in `components/`. Pass actions into
  presentational components through props; keep requests out of layout components.
- Put HTTP and session persistence in `services/`; use that service from forms
  and hooks rather than duplicating fetch, authentication or error handling.
- Use PascalCase `.jsx` names for components and camelCase `.js` names for hooks
  and helpers. Import the owning module directly rather than adding barrel files.
- Keep global styles in `styles/`. Add a feature-local stylesheet if a new
  feature grows its own styles. The current shared cascade remains in one file
  to preserve its ordering and responsive behavior.
- Add browser coverage in `tests/` for user-visible behavior. Generated `dist/`,
  dependencies and test results are not source files.
