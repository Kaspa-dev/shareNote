# Backend structure

Primary resource responses include hypermedia navigation links. See
[the hypermedia contract](../docs/hypermedia.md) for relations and examples.

The Java source root is `src/main/java/lt/sharenote`.

```text
lt/sharenote/
  ShareNoteApplication.java       Spring Boot entry point and OpenAPI metadata
  config/                        Security and JSON configuration
  controller/                    HTTP routes, request validation, responses
  service/                       Business logic, transactions, JDBC queries
  model/                         Resource records and API request/response records
  exception/                     API exceptions and global error handling
  pagination/                    Pagination parameters and bounds
```

Controllers delegate to services. Services use model records and shared exceptions;
they do not depend on controllers. SQL remains in the services, matching the
existing JDBC implementation. `Models` groups the library resource and input records;
authentication request/response records have their own files.

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
