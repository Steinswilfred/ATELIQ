# Completed Features

Only items that have been implemented. "Verified" states how far each was checked.

## Phase 1
| Feature | Verified |
|---|---|
| Repo structure, `.gitignore`, `.env.example`, Docker Compose (Postgres 16, Redis 7) | Not run |
| Spring Boot 3.3 / Java 21 skeleton, typed `app.*` properties, env-based config | Not compiled |
| Uniform `ApiResponse` envelope, `ApiException` hierarchy, `GlobalExceptionHandler` (no internals leaked) | Unit test written, not run |
| Deny-by-default stateless security, CORS from env, JSON 401/403, BCrypt(12) bean | Not compiled |
| OpenAPI/Swagger setup with bearer scheme | Not compiled |
| `GET /api/v1/system/ping` | Not run |
| Flyway V1 identity/RBAC/verification/sessions/login logs; V2 audit/activity/notifications/settings; V3 tailor categories/profiles/applications/documents/reviews; V4 seed roles, permissions, sample tailor categories, default theme | SQL syntax parsed only |
| Module package skeleton per spec section 17 | n/a |
| React + TS + Vite + Tailwind app; runtime-themable color tokens (light red / black / white); Button, Input, Skeleton, EmptyState/ErrorState, Toaster, ConfirmDialog; Public and Dashboard layouts; full route map with honest "planned for Phase N" stubs; axios client with envelope/error mapping; zustand auth and toast stores; home page with live backend connectivity check | Typecheck, build and 3 unit tests pass |
