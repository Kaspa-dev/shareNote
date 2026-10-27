# Response-code coverage

Run **09 - Full response-code suite** alone. It contains 149 requests and checks 99 distinct endpoint/status pairs across all 19 operations. The requests are grouped into 19 endpoint folders, sorted by URL and HTTP method, with response codes sorted inside each folder. Run the whole 09 folder: unique-request-name routing preserves fixture and cleanup dependencies while keeping all endpoint cases together in the sidebar. Do not run a single endpoint folder as a standalone suite. Each negative case checks the exact status, problem content type, and ProblemDetail status/title. Success requests check status and resource/token data; cleanup checks deletion.

One persistent reader account is registered per run (name saved as `suiteReaderUsername`) to cover registration 201/409 and USER authorization. Its password is generated at runtime. Library test records are deleted in child-first order; both sessions are logged out. Failed setup stops the run and may leave data for manual cleanup. Reuse folder 06 to clean captured library IDs before starting another run.

## Endpoint matrix

| Endpoint | Tested codes | Documented but not reproducible |
| --- | --- | --- |
| GET `/api/organizations/{organizationId}/groups/{groupId}` | 200, 400, 401, 404 | 403 |
| PUT `/api/organizations/{organizationId}/groups/{groupId}` | 200, 400, 401, 403, 404, 415, 422 | 409 |
| DELETE `/api/organizations/{organizationId}/groups/{groupId}` | 204, 400, 401, 403, 404, 409 | None |
| GET `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 200, 400, 401, 404 | 403 |
| PUT `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 200, 400, 401, 403, 404, 415, 422 | 409 |
| DELETE `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 204, 400, 401, 403, 404 | 409 |
| GET `/api/organizations/{id}` | 200, 400, 401, 404 | 403 |
| PUT `/api/organizations/{id}` | 200, 400, 401, 403, 404, 415, 422 | 409 |
| DELETE `/api/organizations/{id}` | 204, 400, 401, 403, 404, 409 | None |
| GET `/api/organizations` | 200, 401 | 400, 403, 404 |
| POST `/api/organizations` | 201, 400, 401, 403, 415, 422 | 404, 409 |
| GET `/api/organizations/{organizationId}/groups` | 200, 400, 401, 404 | 403 |
| POST `/api/organizations/{organizationId}/groups` | 201, 400, 401, 403, 404, 415, 422 | 409 |
| GET `/api/organizations/{organizationId}/groups/{groupId}/notes` | 200, 400, 401, 404 | 403 |
| POST `/api/organizations/{organizationId}/groups/{groupId}/notes` | 201, 400, 401, 403, 404, 415, 422 | 409 |
| POST `/api/auth/register` | 201, 400, 409, 415, 422 | None |
| POST `/api/auth/refresh` | 200, 400, 401, 415, 422 | None |
| POST `/api/auth/logout` | 204, 400, 415, 422 | None |
| POST `/api/auth/login` | 200, 400, 401, 415, 422 | None |

## Documentation limitations

These are inherited annotations rather than reproducible public-API cases. They are explicitly excluded, not silently counted as passing. No server code or OpenAPI responses were changed.

| Endpoint | Code | Reason |
| --- | --- | --- |
| GET `/api/organizations/{organizationId}/groups/{groupId}` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| PUT `/api/organizations/{organizationId}/groups/{groupId}` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |
| GET `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| PUT `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |
| DELETE `/api/organizations/{organizationId}/groups/{groupId}/notes/{id}` | 409 | Notes have no dependent records in the schema. |
| GET `/api/organizations/{id}` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| PUT `/api/organizations/{id}` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |
| GET `/api/organizations` | 400 | No typed input or path parameters to invalidate. |
| GET `/api/organizations` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| GET `/api/organizations` | 404 | No path resource is looked up; listing returns an array and creation generates an ID. |
| POST `/api/organizations` | 404 | No path resource is looked up; listing returns an array and creation generates an ID. |
| POST `/api/organizations` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |
| GET `/api/organizations/{organizationId}/groups` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| POST `/api/organizations/{organizationId}/groups` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |
| GET `/api/organizations/{organizationId}/groups/{groupId}/notes` | 403 | All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API. |
| POST `/api/organizations/{organizationId}/groups/{groupId}/notes` | 409 | Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite. |

Auth 415 is also tested even though it is omitted from the exported specification. General protocol errors such as 405/406 and unexpected 5xx outages are outside the documented operation response contract; the suite does not deliberately crash or corrupt the server.
