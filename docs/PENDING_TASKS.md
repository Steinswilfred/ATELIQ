# Pending Tasks

## Immediate (finish Phase 1)
- [ ] Build and start backend locally; fix issues; run tests
- [ ] Apply migrations to Postgres and inspect schema
- [ ] Add backend integration test with Testcontainers (Postgres) to verify migrations in CI
- [ ] Add CI workflow (backend verify, frontend build/test)
- [ ] Self-host fonts

## Phases 2-14
See DEVELOPMENT_ROADMAP.md. Not started: authentication, tailor registration and approval, admin management, catalog, customization engine and 3D, cart/checkout/Razorpay, assignment, fabric approval, delivery, finance, notifications/analytics, testing/hardening.

## Known bugs
None known (nothing beyond scaffolding has run on a live stack yet).

## Blocked on user inputs
- 3D garment assets (Phase 6)
- Razorpay test keys, SMTP, SMS provider, object storage, Google Maps key (see PROJECT_ARCHITECTURE.md section 8)
- Final brand name, logo and legal entity details for invoices (GSTIN, address)
