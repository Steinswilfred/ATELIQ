# Development Roadmap

Spec additions (sections 22-24) are folded into the phases.

| Phase | Scope | Notes |
|---|---|---|
| 1 | Foundation: repos, Docker infra, Flyway V1/V2, base security, shared UI, layouts, docs | **in progress, see PROJECT_STATUS.md** |
| 2 | **code written, unverified.** Auth: customer + tailor registration, email verify, phone OTP, JWT + refresh rotation, account-status login gate, Super Admin bootstrap, RBAC engine, protected routes | tailor pending/rejected/blocked messages enforced in Spring Security |
| 3 | Admin foundation: dashboard shell, users, admin creation by Super Admin, roles/permissions UI, tailor application review (approve/reject/request info + history), website/theme settings | **code written, unverified.** |
| 4 | Catalog admin: categories, products, fabrics, colors, sizes, customization groups/options with render bindings, image upload | **code written, unverified.** |
| 5 | Storefront: home, browse, search/filter, product page, measurements, saved designs, trending admin | **code written, unverified.** |
| 6 | Customization engine + 3D (R3F), 2D fallback, persistence | **code written, unverified (3D not yet seen).** needs sample GLB assets |
| 7 | Tailor categories/pricing, tailor profiles, availability, tailor dashboard | **code written, unverified.** |
| 8 | Cart, pricing, coupons, addresses + Maps, Razorpay order/verify/webhook, COD, order + invoice | **code written, unverified (Razorpay untested).** |
| 9 | Eligible-tailor notification, atomic accept, reassignment, history | **code written, unverified (concurrency test not yet run).** |
| 10 | Fabric sample workflow with approval guard and history | **code written, unverified.** |
| 11 | Cutting to delivery transitions, tracking, courier abstraction | **code written, unverified (no courier integration).** |
| 12 | Earnings, commission, refunds, COD collection, financial reports | **code written, unverified (business defaults need confirming).** |
| 13 | WebSocket, email, SMS, analytics, logs, CSV/PDF export | |
| 14 | Tests (unit, integration with Testcontainers, API, frontend, security), perf, deployment docs | |

Each phase ends with: tests passing, docs updated, app runnable.
