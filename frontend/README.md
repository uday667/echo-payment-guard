# Investigator alert queue

The React + TypeScript application lists alerts from `GET /api/alerts`, supports
type and status filters with server-side pagination, and offers audited status
actions for open alerts. Actions send `PATCH /api/alerts/{id}` with a status,
an investigator note, and the `X-User: investigator` actor header.

## Run locally

From the repository root, start the backend in one terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

In another terminal, start the UI:

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite (normally `http://localhost:5173`). The Vite
development server proxies `/api` requests to `http://localhost:8080`.

Run the frontend tests and production build from `frontend/`:

```powershell
npm test
npm run build
```

This frontend does not start PostgreSQL, Redis, or Kafka, or run the Task 2
stream replay. Those service and replay commands belong to the backend setup.
The screen expects the backend to provide the alert PATCH endpoint.
