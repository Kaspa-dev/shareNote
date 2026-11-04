# Backend structure

Primary resource responses include hypermedia navigation links. See
[the hypermedia contract](../docs/hypermedia.md) for relations and examples.

The Java source root is `src/main/java/lt/sharenote`.

```text
lt/sharenote/
  ShareNoteApplication.java       Spring Boot entry point and OpenAPI metadata
  config/                        Security and JSON configuration
  controller/                    HTTP routes, request validation, responses
  service/                       Business logic, authorization, transactions
  repository/                    JDBC queries and database row mapping
  model/                         Resource records and API request/response records
  exception/                     API exceptions and global error handling
  pagination/                    Pagination parameters and bounds
```

The dependency flow is **Controller -> Service -> Repository -> Database**.
Controllers delegate to services, which enforce business rules, authorization, and
transaction boundaries. Repositories use `JdbcClient` for SQL and row mapping;
they return records, optional results, or affected-row counts. Services translate
missing results into API errors.

Resource logic is split across `OrganizationService`, `GroupService`, and
`NoteService`. Group operations validate organizations through `OrganizationService`;
note operations validate groups through `GroupService`. These calls join the
existing transaction, keeping scoped locks until the outer operation completes.
Note write permissions and ownership checks stay in `NoteService`.

`OrganizationRepository`, `GroupRepository`, and `NoteRepository` persist library
resources; `UserRepository` and `SessionRepository` persist accounts and sessions.
Queries using `FOR UPDATE` run inside the calling service transaction so scoped
resource checks, ownership checks, and refresh-token rotation retain their locks
until the operation completes.

`Models` groups the library resource and input records. Authentication records,
including the internal `Account` and `RefreshSession` database records, have their
own files. Repositories do not depend on services or controllers.

The application class stays in the root package so Spring scans all subpackages.
Configuration and Flyway migrations remain in `src/main/resources`; migration
filenames and SQL are unchanged.

Tests are under `src/test/java/lt/sharenote`. Unit tests mirror the corresponding
production package; `ApiIntegrationTest` remains at the root as a cross-package
test of the complete API.

From this directory on Windows:

```powershell
.\mvnw.cmd clean test                   # Full suite; requires Docker for PostgreSQL
.\mvnw.cmd clean test -Dtest=PaginationTest  # Pagination unit tests only
.\mvnw.cmd package -DskipTests          # Build the application JAR
```

HTTP paths, JSON field names, validation rules, and authentication behavior are
independent of this package layout.

## Local startup port conflicts on Windows

If startup reports that port 8080 is in use but no process is listening, check
Windows reserved ports with `netsh interface ipv4 show excludedportrange protocol=tcp`.
Choose a free port outside those ranges (for example, 8180). From the repository root:

```powershell
$env:SERVER_PORT = '8180'
.\scripts\start.ps1
```

The startup script passes this port to both Spring Boot and the Vite API proxy.
Swagger UI is then available at `http://localhost:8180/swagger-ui.html`.
For a backend-only launch, set the same variable before running
`.\mvnw.cmd spring-boot:run` in this directory; PostgreSQL must already be running.
If starting Vite in a separate terminal, set `SERVER_PORT` there too and restart it.
