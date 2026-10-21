# Askvocate Admin Console

A back-office console for Askvocate's verification desk. It supports:

- Password-only admin sign-in
- PAN manual review (queue, OCR vs. signed Aadhaar comparison, image previews, approve/reject)
- Fresher and experienced lawyer-profile approvals
- Queue overview with review-signal breakdowns

Built with Vite, React, and TypeScript. Styling is plain CSS with design tokens in
`src/styles.css`.

## Requirements

- Node.js 18 or newer
- npm 9 or newer
- The Askvocate backend running at `http://localhost:8080` for live data

## Install and run

```bash
npm install
npm run dev
```

The dev server starts at `http://localhost:5173` and proxies `/api` to
`http://localhost:8080`, so no CORS configuration is needed.

## Production build

```bash
npm run build
npm run preview
```

The static output is written to `dist/`.

## Environment variables

Copy `.env.example` to `.env` if you need to override the defaults.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `""` | API origin for deployed builds. Leave empty to use same-origin `/api` requests. |
| `VITE_DEMO_MODE` | `false` | Set to `true` to run with bundled sample data and simulated decisions. |

### Demo mode

Preview the console without a backend in either of two ways:

1. Set `VITE_DEMO_MODE=true` in `.env`
2. Append `?demo=1` to the app URL

In demo mode, any non-empty password signs you in. Sample PAN documents and lawyer
profiles are held in memory; approve/reject actions update that local data and reset on
a full page reload. Demo document images are local SVG files in `public/demo/`.

## Backend and authentication

- Backend: `http://localhost:8080`
- Login endpoint: `POST /api/admin/login` with `{ "password": "..." }`
- Admin requests send `Authorization: Bearer <appToken>`
- The token is stored in `sessionStorage` under `askvocateAdminToken`
- Any `401`/`403` from an admin endpoint clears the token and returns to login with
  "Session expired. Sign in again."

The admin password is configured on the backend via the
`verification.admin-password` property (commonly supplied through the backend's
`ADMIN_PASSWORD` environment variable). Use the backend's configured local admin
password. It is intentionally not stored in this frontend or in this README.

For a deployed frontend served from a different origin than the backend, set
`VITE_API_BASE_URL` to the backend origin and ensure the backend allows that origin.

## Project structure

```
src/
  api/          typed API client, demo data, error handling
  components/   shared UI (shell, modal, lightbox, timeline, states, toast)
  hooks/        queue data loading and decision actions
  screens/      login, overview, PAN review, lawyer approvals
  utils/        dates, labels, PAN and queue-filter helpers
  styles.css    design tokens and all component styles
public/demo/    sample PAN document artwork for demo mode
```

## Notes and limitations

- The lawyer status-filter endpoints (`/fresher/status/{status}` and
  `/experienced/status/{status}`) are supported by the backend but not exposed in the
  UI; the console starts from the pending list and updates rows locally after a decision.
- "Approved since sign-in" counts PAN and lawyer approvals made in the current admin
  session. The pending feed does not include a daily aggregate.
- Document images are served by the backend via their `secureUrl`; if an image fails to
  load, the lightbox shows a fallback message.
