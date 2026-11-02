# Changelog

## 0.1.0-SNAPSHOT (2026-11-02) Phase 1 foundation
### Added
- Monorepo: `backend/`, `frontend/`, `docs/`, Docker Compose, `.env.example`.
- Backend skeleton, security baseline, error/response framework, OpenAPI, system ping.
- Database migrations V1-V4 (see DATABASE_SCHEMA.md).
- Frontend foundation: themable design tokens, UI kit, layouts, route map, API client, stores.
- Documentation set.
### Database changes
- V1 identity and access, V2 audit/notifications/settings, V3 tailor onboarding, V4 seed reference data.
### API changes
- `GET /api/v1/system/ping`.
### Known limitations
- Backend not compiled or run in the authoring environment; migrations not executed.
