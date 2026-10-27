# ShareNote Postman guide

## One file to import

Import [ShareNote.postman_collection.json](ShareNote.postman_collection.json). It includes all requests, variables, and this guide in the collection description. No environment JSON is needed. There are 224 requests: 26 in the main demo, three in optional registration, 149 in the full response-code suite covering all 19 API operations, and 46 in the editor ownership suite.

## Start the API

Install Docker with Linux containers and Java JDK 21 or newer. Start Docker. Configure `ADMIN_USERNAME` and `ADMIN_PASSWORD` in the project's local `.env`, as described in the root README. There is no default administrator password. Passwords require at least 12 characters and at most 72 UTF-8 bytes. Startup does not reset existing passwords or promote USER accounts.

Run from PowerShell in the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -BackendOnly
```

Keep the terminal open. Wait until [Swagger UI](http://localhost:8080/swagger-ui.html) loads. The frontend is unnecessary for Postman. To start it too, omit `-BackendOnly`; that also requires Node.js and npm.

## Import and configure

1. In Postman choose **Import** and select **ShareNote.postman_collection.json**.
2. Select **No environment**, open the imported collection, and open its **Variables** tab.
3. Set the collection variables `adminUsername` and `adminPassword` to your configured administrator credentials. Postman does not load `.env` automatically.
4. Keep `baseUrl` as `http://127.0.0.1:8080` for local use. For another server, use its origin without a trailing slash or `/api`.

Keep passwords local and do not export populated secrets. Registration fields can stay blank for the main demonstration.

## Run the demonstration

Select **ShareNote - Complete API Demo**, choose **Run**, and use a local functional run with **1 iteration**, **0 ms delay**, and folders 00–07 selected in this order. Leave folder 08 unchecked. The administrator logout also stops a whole-collection run before optional registration:

| Folder | Action |
| --- | --- |
| 00 - Administrator login | Sign in, save both tokens, then rotate them |
| 01 - Create demo resources | Create an organization, group, and note; save their IDs |
| 02 - Organizations | List, read, and update organizations |
| 03 - Groups | List, read, and update the new organization's groups |
| 04 - Notes | List, read, and update the new group's notes |
| 05 - Error responses | Check deliberate 400, 404, 409, and 422 responses |
| 06 - Delete demo resources | Delete note, group, then organization; verify deletion |
| 07 - Logout | Revoke the refresh token and clear captured tokens |

Start the run and inspect the test results. Expected error responses count as passing tests. Other checks cover fields, IDs, Location headers, content types, and empty 204 bodies.

This run creates and deletes demo records on the selected server. Interrupted runs may leave records behind. Finish cleanup with the captured IDs before starting a new run, which replaces those IDs. Do not substitute unrelated records into delete requests.

Official references: [Postman import](https://learning.postman.com/latest-v-12/docs/getting-started/importing-and-exporting/importing-data) and [Collection Runner](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs).

## Send requests individually

Open **Login** and click **Send**, then send the three requests in folder 01 in order. You can then send list/get/update requests individually. Scripts capture variables when using Send too; Runner stop-on-failure behavior does not prevent you from manually sending another request.

Authorization is inherited as `Bearer {{accessToken}}`. Login and refresh save `accessToken` and `refreshToken` as collection variables. Creation saves `orgId`, `groupId`, and `noteId`. Do not define these IDs or tokens in the environment or iteration data, which would override captured values.

Access tokens last three minutes. Send **Refresh administrator tokens** or **Login** again when needed; the collection does not automatically retry expired requests. Refresh tokens are single-use, and the refresh request saves the replacement pair.

Group and note parents are specified in the URL:

```text
POST /api/organizations
POST /api/organizations/{{orgId}}/groups
POST /api/organizations/{{orgId}}/groups/{{groupId}}/notes
```

Do not add parent IDs to group/note JSON bodies. PUT requires all editable fields shown in the example. Delete children before parents. Finish manual exploration with folder 06, then folder 07. Logout revokes the refresh token; existing access JWTs remain valid until expiry or an API restart.

## Optional registration

Set `registrationUsername` and `registrationPassword` in the collection Variables tab. Usernames require 3-80 ASCII letters, digits, underscores, periods, or hyphens and are case-insensitive. Use a new username and a password meeting the rules above.

Run only the **08 - Optional reader registration** folder, with its three requests in order. It creates a persistent USER account, reads organizations, then logs out. USER accounts can read the entire library but cannot create, edit, or delete library records. This folder uses `readerAccessToken` and `readerRefreshToken`, separate from the administrator tokens. Run only when you want a new account; repeated registration with the same username returns 409.

## Test response codes per endpoint

After setting administrator credentials in collection Variables, select **09 - Full response-code suite** and run that folder alone, with **1 iteration** and all requests selected. Do not run it together with folders 00–08: the normal demo deliberately stops at folder 07.

Inside folder 09, requests are grouped into **19 endpoint folders**, sorted by URL and HTTP method. Each folder keeps all response-code cases together, sorted by status code. Runner scripts use unique request names to follow the required setup, test, and cleanup sequence automatically, so the execution log can jump between folders. Run the entire 09 folder; individual endpoint folders depend on shared setup and are not standalone runs.

This suite tests 99 distinct endpoint/status combinations using 149 requests, including success, malformed JSON/IDs, missing or invalid JWTs, reader access, forbidden writes, missing resources/parents, duplicate registration, dependency conflicts, unsupported media types, validation errors, consumed refresh tokens, revoked refresh tokens, and idempotent logout.

It creates **one persistent reader account per run** to test registration and 403 responses. Its unique username is saved as `suiteReaderUsername`; its password is generated automatically. The API cannot delete accounts. Library records are cleaned up and both sessions are logged out. Interrupted runs may leave library records: use folder 06 with the captured IDs before rerunning.

[Response-code coverage](response-code-coverage.md) lists tested codes for every endpoint and explains 16 documented combinations that cannot be deterministically reproduced through this API. Those are not counted as passing tests. The complete matrix is also embedded in the JSON's collection description.

**Verification (2026-10-03):** the full folder ran against the local API through Newman 6.2.2: 149 requests and 557 assertions passed, with zero failures, including a rerun with all suite request IDs reassigned to simulate importing. Desktop Postman itself was not automated. The run cleaned up its library records and logged out both sessions; its reader account remains because the API has no account deletion operation.

Run `node scripts/check-postman-routing.cjs` to check routing after simulated ID reassignment, script structure, and stop-on-login-failure behavior. Routing uses Postman's supported [request-name targets](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows/).

To rebuild the generated suite after editing the demo or OpenAPI file, run `node scripts/expand-postman.cjs` from the project root.

After an update, import the new JSON as a new collection and run folder 09 from that newly imported copy. Each suite request has a unique `[09-NNN]` suffix used for routing; keep those names unchanged. Do not rerun an old saved run configuration.

## Editor ownership tests

Configure `adminUsername`, `adminPassword`, `editorOneUsername`, `editorOnePassword`, `editorTwoUsername`, and `editorTwoPassword`. The editors must be **two different existing EDITOR accounts**. Public registration only creates USER accounts: register each editor account once, then have the database administrator grant EDITOR as described in [authorization.md](../authorization.md#kaip-suteikti-editor-rolę). Login reads the new role. This collection does not grant roles or create editor accounts.

Run **10 - Editor ownership suite** alone with every request selected, one iteration, and zero delay. It checks both editor roles and distinct JWT subjects before creating fixtures, then tests:

- Editor read access and creation with `ownerId` matching the JWT subject.
- Own-note updates and deletion, and 403 for writes to another editor's or administrator's notes.
- 403 for organization/group creation, update, and deletion.
- Rejection of `ownerId` injection, unchanged data after denied writes, and wrong-parent 404.
- Administrator editing/deleting editor notes while preserving their ownership.
- Child-first fixture deletion, deletion verification, and logout of all three sessions.

The suite creates an isolated organization, group, and three notes. It uses separate `editor*` IDs and tokens, leaving the main demo's variables available. Any unexpected response stops the run. If interrupted, use the saved `editorOrgId`, `editorGroupId`, `editorNoteOneId`, `editorNoteTwoId`, and `editorAdminNoteId` with administrator credentials to delete only those fixtures, notes first. Clear those IDs after cleanup before rerunning; the suite refuses to overwrite leftover fixture IDs. Keep credentials local, and do not define captured IDs or tokens in an environment.

Historical notes with null owners are covered by the backend integration tests; this suite cannot create that legacy state through the public API. Folder 09's endpoint/status matrix does not include folder 10's additional authorization scenarios.

**Verification (2026-10-08):** generated scripts and routing were checked locally. The updated editor suite has not been run against a live API; the local API was unavailable. The earlier Newman result above applies only to the previous folder 09 version.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Connection refused | Start the API, wait for startup, check `baseUrl` and port 8080. |
| Missing credential script error | Open collection Variables and fill the corresponding credentials. |
| 401 on login | Check credentials; changing `.env` does not reset existing passwords. |
| 401 on other requests | Refresh or log in again. API restarts invalidate access JWTs. |
| 403 on writes | Use ADMIN credentials; registration creates USER accounts. |
| Unresolved variables or 404 | Run creation first and use matching parent/child IDs. |
| 409 on deletion | Delete notes, then groups, then the organization. |
| 400 or 422 | Check JSON syntax, allowed fields, required values, and field lengths. |
| 409 on registration | Choose an unused username. |

Folder 05 deliberately triggers several of these errors.

## Command-line option

With Node.js installed, run from the project root:

```powershell
npm.cmd exec --yes --package=newman -- newman run docs/postman/ShareNote.postman_collection.json
```

For this option, add Newman options `--env-var adminUsername=YOUR_USERNAME --env-var adminPassword=YOUR_PASSWORD` using your own values and appropriate shell quoting. These overrides require no environment file. Avoid recording secrets in shell history. The first invocation downloads Newman through npm. Do not export captured tokens or passwords when sharing collections.

To run the expanded suite with Newman, add `--folder "09 - Full response-code suite"` to the command above.
