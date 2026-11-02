# API Documentation

Interactive docs (when backend runs): `http://localhost:8080/swagger-ui.html`, spec at `/v3/api-docs`.

## Conventions
- Base path `/api/v1`. JSON. Auth: `Authorization: Bearer <accessToken>`.
- Envelope for every response:
```json
{ "success": true, "data": {}, "error": null, "timestamp": "2026-11-02T10:00:00Z" }
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "...", "details": [{"field":"email","message":"..."}] }, "timestamp": "..." }
```
- Stable error codes (frontend branches on `code`, never on message). Pagination: `?page=0&size=20&sort=createdAt,desc`; response `data` holds `content`, `page`, `size`, `totalElements`.
- Status codes: 400 validation, 401 unauthenticated, 403 forbidden/blocked, 404, 409 conflict (e.g. order already taken), 422 invalid state transition, 429 rate limit.

## Implemented
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/system/ping` | public | service name, environment, time. Used by the home page connectivity check |
| GET | `/actuator/health` | public | liveness/readiness |

## Planned (by phase)
| Phase | Group | Endpoints (summary) |
|---|---|---|
| 2 | `/auth` | register (customer), register-tailor, verify-email, send/verify-phone-otp, login, refresh, logout, forgot/reset-password, change-password; `GET /me` |
| 2 | `/tailor-applications` | `GET /my` status; resubmit (if permitted) |
| 3 | `/admin/users`, `/admin/admins` | list/search/filter, view, edit, block/reactivate, login history, activity; create admin (permission `admin:create`), assign roles/permissions, revoke, reset permissions |
| 3 | `/admin/roles`, `/admin/permissions` | CRUD, assign to roles/users |
| 3 | `/admin/tailor-applications` | list by status, detail (docs via signed URLs), approve (category + availability), reject (reason), request-info, notes, history |
| 3 | `/admin/settings`, `/public/settings` | theme, branding, banners; public read |
| 4 | `/admin/categories|products|fabrics|colors|customization-*` | CRUD + image upload |
| 5 | `/public/catalog`, `/me/measurements`, `/me/designs`, `/me/addresses` | browse, search, filters |
| 6 | `/public/products/{id}/customization-schema`, `/me/designs` | option tree with render bindings, price preview |
| 7 | `/admin/tailor-categories`, `/tailor/profile` | pricing, availability |
| 8 | `/cart`, `/checkout`, `/payments/razorpay/orders`, `/payments/razorpay/verify`, `/payments/razorpay/webhook` | see architecture 5.2 |
| 9 | `/tailor/orders/available`, `POST /tailor/orders/{id}/accept`, `/admin/orders/{id}/reassign` | atomic accept |
| 10 | `/tailor/orders/{id}/fabric-samples`, `/me/orders/{id}/fabric-samples/{sid}/approve|reject` | |
| 11 | `/tailor/orders/{id}/progress`, `/admin/orders/{id}/delivery` | |
| 12 | `/admin/refunds`, `/admin/payments/{id}/collect-cod`, `/admin/finance/*` | |
| 13 | `/ws` (STOMP), `/me/notifications`, `/admin/reports/*` (CSV/PDF export), `/admin/logs/*` | |

Authorization rule for every `/admin/**` endpoint: a named permission (e.g. `tailor:approve`), enforced server-side, covered by a test that a user without it receives 403.
