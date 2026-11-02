# Development Roadmap

Rule: a phase is done only when its code runs, its tests pass, and the docs are updated. Never start the next phase on a broken build.

| Phase | Scope | Exit criteria |
|---|---|---|
| **1 Foundation** (current) | Repo, Docker (Postgres/Redis), Spring Boot skeleton, Flyway V1-V4, response/exception framework, deny-by-default security, OpenAPI; Vite/React/TS/Tailwind app, theme tokens, UI kit, layouts, route map | Backend builds and starts against Postgres, migrations apply cleanly, `/system/ping` works from the SPA; frontend build + tests green |
| **2 Auth** | Customer registration, email verification, phone OTP, login, JWT + refresh rotation, password reset, rate limits, Super Admin bootstrap from env, `ProtectedRoute`. **Tailor registration** (form, documents upload, email+phone verification, `PENDING_APPROVAL`), **login gate for PENDING_APPROVAL/REJECTED/BLOCKED/SUSPENDED with the exact spec messages**, application status page | Integration tests for every account status at login; blocked user rejected on next request |
| **3 Admin foundation** | Dashboard shell, user management, **admin creation by Super Admin only**, roles/permissions CRUD and assignment, permission-based endpoint guards, **tailor approval dashboard (pending/approved/rejected/blocked/suspended, review, approve with category + availability, reject with reason, request info, notes, history)**, admin notifications, audit/activity logging, website/theme settings, login history | 403 test per guarded admin endpoint; approval grants TAILOR role and enables login |
| **4 Catalog admin** | Categories, products, images, fabrics, colors, sizes, customization groups/options (with `render_binding`, price deltas, dependencies) | Admin can add a new garment type and options with no code change |
| **5 Shopping** | Home, browse, search/filter, details, addresses, measurement profiles, saved designs | |
| **6 Customization engine** | Dynamic option UI, live price, R3F viewer, 360/zoom/views, 2D fallback, persistence | Needs 3D assets (see architecture 8). Built first for shirt and trousers |
| **7 Tailor management** | Tailor categories and pricing admin, profiles, availability, tailor dashboard | |
| **8 Cart, checkout, Razorpay** | Cart, trusted pricing, taxes, coupons, Google Maps address, **Razorpay order create, Checkout, server-side signature + status verification, webhook with idempotency, failure/retry**, COD, order creation, invoice | Test-mode payment end to end; replayed webhook creates no duplicates |
| **9 Assignment** | Eligible-tailor notification, atomic accept, history, admin reassign, assignment rules | Parallel-accept test: exactly one winner |
| **10 Fabric approval** | Sample upload, approve/reject, revisions, hard gate before cutting, full history | |
| **11 Tailoring and delivery** | Cutting/stitching/QC/dispatch states, timeline with photos, admin-managed delivery (courier optional) | |
| **12 Finance** | Commission rules, tailor earnings, ledger, **refunds via Razorpay + admin approval rules, COD collection marking, reconciliation job**, settlements, reports | Ledger balances; refund webhook idempotent |
| **13 Notifications and analytics** | STOMP real-time, email, SMS, notification settings, charts, reports with CSV/PDF export, security and API error logs | |
| **14 Hardening** | Unit/integration/API/frontend/security/performance tests, deployment docs | |

Spec additions (sections 22-24) are folded into phases 2, 3, 8, 9 and 12 above.
