# Project Status

**Date:** 2026-11-02  **Current phase:** 1 (Foundation), code written, awaiting local backend verification.

## Verified in the authoring environment
- Frontend: `npm install`, `tsc` typecheck, `vitest` (3 tests), `vite build` all succeed.
- SQL migrations V1-V4: parsed successfully by a PostgreSQL parser (syntax only).

## NOT verified (read this before continuing)
- **The backend has never been compiled or run.** The authoring sandbox had Java 21 but no Maven and no access to Maven Central. Expect possible small compile or wiring errors.
- **Migrations have not been executed against a real PostgreSQL**, so constraint/ordering errors are possible.
- Backend test (`GlobalExceptionHandlerTest`) not yet run.

## Next exact task
1. Locally: `docker compose up -d`, then `cd backend && mvn clean verify` and `mvn spring-boot:run`. Fix any compile/migration errors. Confirm `GET /api/v1/system/ping` and that Flyway applied V1-V4 (`flyway_schema_history`).
2. Run `cd frontend && npm install && npm run dev`; confirm the home page shows "Connected to clothing-platform".
3. Commit. Then start **Phase 2: Authentication** (see DEVELOPMENT_ROADMAP.md).

## Known issues
- Brand name "Stitchline" is a placeholder.
- Google Fonts are loaded from Google's CDN; self-host for production.
- `UserDetailsService` is a deliberate stub until Phase 2.
