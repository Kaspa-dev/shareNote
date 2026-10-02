# Postman demo

1. Configure `ADMIN_USERNAME` and `ADMIN_PASSWORD` as described in README, then start the API: `docker compose up -d --build`.
2. In Postman, click **Import** and select both files:
   - [ShareNote.postman_collection.json](ShareNote.postman_collection.json)
   - [ShareNote.local.postman_environment.json](ShareNote.local.postman_environment.json)
3. Select **ShareNote - Local** as the active environment. Set `adminUsername` and `adminPassword` to the configured administrator credentials. Keep the password local and do not export its value.
4. Open **ShareNote - Complete API Demo**, click **Run**, and select all seven folders, starting with **00 - Administrator login**.
5. Use **1 iteration**, **0 ms delay**, then click **Run ShareNote - Complete API Demo**.

The collection logs in, captures the JWT automatically, and covers all 15 CRUD operations using 24 requests in total. Tests check response codes, JSON content types, created and updated fields, IDs, Location headers, empty 204 bodies, and expected 400/404/409/422 errors. Expected error responses count as successful tests.

IDs are captured automatically. Run folders in their numbered order; later requests depend on earlier ones. The final folder deletes only this run's demo resources. An interrupted run may leave demo records in the database. Keep `orgId`, `groupId`, and `noteId` out of environment/global variables so they do not override captured collection IDs.

For another server, change the environment's `baseUrl`. The API must already be running before timing the demonstration.

## Command-line run

With Node.js installed, run from the project root:

```sh
npm.cmd exec --yes --package=newman -- newman run docs/ShareNote.postman_collection.json -e docs/ShareNote.local.postman_environment.json --env-var adminUsername=admin --env-var "adminPassword=$env:ADMIN_PASSWORD"
```
