# ShareNote

REST API for shared notes, built with Java 21, Spring Boot, PostgreSQL 17, and Flyway.
Includes 15 CRUD operations for **organizations, groups, and notes**. Groups may be standalone; every note belongs to a group. Authentication and membership management are outside this lab's scope.

## Run

Requires Docker Desktop with Linux containers.

```sh
docker compose up -d --build
```

On Windows, `powershell -ExecutionPolicy Bypass -File scripts/start.ps1` also waits until the API is ready. Start the environment before the demonstration; the first build downloads dependencies.

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [Live OpenAPI](http://localhost:8080/v3/api-docs) · [Saved specification](docs/openapi.json)

The database is seeded with 2 organizations, 4 groups, and 5 notes. Data persists in a Docker volume. Stop with `docker compose down`.

## Demo

With Node.js 18+ and the API running:

```sh
node scripts/demo.mjs
```

Checks all 15 operations, response bodies, headers, and error codes, then removes its own data. The Docker demo completed 28 HTTP checks in 1.17 seconds. Set `API_URL` to use another server.

Alternatively, import the [Postman collection](docs/ShareNote.postman_collection.json) and run all requests in order with 1 iteration and 0 ms delay.

## API

Each resource (`organizations`, `groups`, `notes`) supports:

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/{resource}` | 200 |
| GET | `/api/{resource}/{id}` | 200 |
| POST | `/api/{resource}` | 201 + Location |
| PUT | `/api/{resource}/{id}` | 200 |
| DELETE | `/api/{resource}/{id}` | 204, empty body |

Filter groups by `?organizationId=1` and notes by `?groupId=1`. PUT replaces all editable fields. Delete notes before their group, and groups before their organization.

Requests and successful bodies use JSON; errors use `application/problem+json`: **400** malformed input, **404** not found, **409** relationship conflict, **415** unsupported content type, **422** validation failure. See Swagger for payloads and field limits.

## Development

Requires JDK 21 (`JAVA_HOME`) and Docker. Maven Wrapper is included.

```powershell
docker compose up -d --wait db
./mvnw.cmd spring-boot:run
./mvnw.cmd test
```

Run tests in another terminal. Stop the Compose API first (`docker compose stop api`) to free port 8080. On Linux/macOS, use `sh ./mvnw`. Integration tests use a separate PostgreSQL container.

Local DB: `localhost:5433`, database/user `sharenote`, development password `sharenote-local`. Configure the API with `DB_URL`, `DB_USER`, and `DB_PASSWORD`; set Compose `DB_PASSWORD` in `.env` before creating the database.

After API changes, refresh the saved spec with `powershell -ExecutionPolicy Bypass -File scripts/export-openapi.ps1`.
