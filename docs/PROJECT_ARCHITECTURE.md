# Project Architecture

## Style
Modular monolith (Spring Boot) + SPA (React). One deployable backend with strict module boundaries (package per module: controller → service → repository → entity, DTOs + mappers at the edge). Modules talk through service interfaces and domain events, so any module can be extracted later. Not microservices: a single team and one PostgreSQL database make a monolith cheaper and safer, with the same discipline.

## Runtime topology
```
React SPA (Vite) ──HTTPS/REST──▶ Spring Boot API ──▶ PostgreSQL (source of truth, Flyway)
        └────WebSocket(STOMP)───▶      │──▶ Redis (OTP, rate limits, cache, WS fan-out later)
                                        │──▶ Razorpay (orders, webhooks, refunds)
                                        │──▶ SMTP / SMS provider / Maps / Object storage (S3-compatible)
```

## Cross-cutting rules
- Auth: short-lived JWT access token (15 min) + rotating refresh token (hashed in DB, family reuse detection).
- AuthZ: backend only. Permission codes (`PRODUCT_WRITE`) become Spring authorities; `@PreAuthorize` on service entry points. Frontend menu hiding is cosmetic.
- Account gate: authentication fails unless `account_status` allows login (tailor PENDING_APPROVAL/REJECTED/BLOCKED/SUSPENDED get specific messages from the auth layer).
- Money: `NUMERIC(12,2)` in INR; amounts always recomputed server-side from DB prices. Ledger-style financial tables, append-only.
- Idempotency: unique constraints on gateway ids and webhook event ids; processing inside a transaction.
- Every response uses the `ApiResponse` envelope; errors via `GlobalExceptionHandler`.
- Audit: approvals, permission changes, payments, refunds write `audit_logs` in the same transaction.

## Backend modules (`com.clothingplatform`)
config, security, auth, user, admin, role, permission, product, category, fabric, customization, measurement, design, cart, order, tailor, assignment, approval, payment, refund, notification, tracking, report, audit, common, exception. Only `config`, `security`, `common`, `exception` exist in Phase 1; others are added in their phase.

## Frontend structure
```
src/
  components/ui/   Button, Skeleton, EmptyState, Toaster, ConfirmDialog
  layouts/         CustomerLayout, DashboardLayout (tailor + admin)
  config/          navigation (permission-tagged admin menu)
  lib/             api (axios + envelope), theme (runtime CSS-variable theming)
  store/           zustand stores
  pages/           route pages (customer/, tailor/, admin/ added per phase)
  features/        (Phase 5+) catalog, customizer3d, cart, orders, ...
```
Theme: Tailwind colors read CSS variables, so Super Admin theme settings apply without rebuild.

## Hard problems and how they are handled
1. **3D customization.** A garment has a GLB model with named meshes (`collar_*`, `sleeve_*`, `pocket_*`) and material slots. A `customization_option` maps to a *render binding* in the DB (`{type: "mesh_toggle" | "material_color" | "texture" | "morph_target", target: "..."}`), so admin-added options drive the model without frontend code changes. The selected configuration is JSON stored with the saved design/order item. No GLB for a garment → 2D layered SVG/PNG fallback. **Requires 3D assets** (see below); shapes like collar/sleeve variants must be modeled by a 3D artist or purchased.
2. **Concurrent tailor acceptance.** `UPDATE orders SET tailor_id=:t, status='TAILOR_ASSIGNED' WHERE id=:o AND status='TAILOR_ASSIGNMENT_PENDING' AND tailor_id IS NULL`; affected rows = 1 means winner. Backed by a unique partial index on active `tailor_assignments(order_id)`. Losing tailors get a "no longer available" response and a WebSocket removal event.
3. **Payments.** Server computes the amount, creates a Razorpay order, verifies HMAC signature on callback, re-fetches status, and treats webhooks as the reconciliation path. `payment_webhook_events.event_id` is unique for duplicate protection.
4. **Order state machine.** Allowed transitions in one backend class with unit tests; the fabric-approval guard blocks `CUTTING_IN_PROGRESS` unless a sample is `APPROVED`.
5. **Dynamic customization pricing.** Options may carry price deltas; cart pricing is recomputed from DB on every cart read and at checkout.

## External services and assets
| Need | Provider (abstracted behind an interface) | Notes |
|---|---|---|
| Payments | Razorpay | account + test keys |
| SMS OTP | MSG91 / Twilio / similar | DLT registration needed in India |
| Email | any SMTP (Mailpit locally) | |
| Maps | Google Maps Platform | billing-enabled key, restrict by referrer |
| File storage | S3-compatible (S3, R2, MinIO locally) | |
| Courier tracking | Shiprocket / Delhivery etc. | else admin-managed statuses, no fake GPS |
| Trend data | none initially | admin-curated; schema ready for a provider |
| 3D models | GLB per garment | must be sourced/created; budget for this |
