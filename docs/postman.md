# Postman demo

1. Start the API: `docker compose up -d --build`.
2. In Postman, click **Import** and select both files:
   - [ShareNote.postman_collection.json](ShareNote.postman_collection.json)
   - [ShareNote.local.postman_environment.json](ShareNote.local.postman_environment.json)
3. Select **ShareNote - Local** as the active environment.
4. Open **ShareNote - Complete API Demo**, click **Run**, and select all six folders.
5. Use **1 iteration**, **0 ms delay**, then click **Run ShareNote - Complete API Demo**.

The collection covers all 15 API operations using 23 requests. Tests check response codes, JSON content types, created and updated fields, IDs, Location headers, empty 204 bodies, and expected 400/404/409/422 errors. Expected error responses count as successful tests.

IDs are captured automatically. Run folders in their numbered order; later requests depend on earlier ones. The final folder deletes only this run's demo resources. An interrupted run may leave demo records in the database. Keep `orgId`, `groupId`, and `noteId` out of environment/global variables so they do not override captured collection IDs.

For another server, change the environment's `baseUrl`. The API must already be running before timing the demonstration.

## Command-line run

With Node.js installed, run from the project root:

```sh
npm exec --yes --package=newman -- newman run docs/ShareNote.postman_collection.json -e docs/ShareNote.local.postman_environment.json
```
