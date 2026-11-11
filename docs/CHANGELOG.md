# Changelog
## 0.1.0-SNAPSHOT (Phase 1)
- Added backend skeleton, Flyway V1 (identity/RBAC/logs/settings) and V2 (seed roles, permissions, theme)
- Added frontend skeleton and shared UI
- Added Docker Compose (Postgres, Redis, Mailpit), `.env.example`, project docs

## Phase 2
- Added auth, verification, refresh rotation, account-status gate, RBAC API, Super Admin bootstrap (backend)
- Added V3 migration (tailor_applications)
- Added auth pages, session store with silent refresh, route guards (frontend)

## Phase 3
- Added V4 migration; notifications, admin users, tailor application review, dashboard, settings (backend)
- Added admin portal pages, notification bell, dynamic theme loading, permission-filtered navigation (frontend)
- `AuthService.advanceStatus` is now public and notifies admins when a tailor reaches PENDING_APPROVAL

## Phase 4
- Added V5 migration and the catalog module (categories, attributes, customization, products), storage layer and upload endpoint
- Added admin catalog pages, generic FormDialog and ImageUploader; admin routes are lazy-loaded
- GlobalExceptionHandler now maps integrity violations to 409 and oversize uploads to 413

## Phase 5
- Added V6 migration, shop module (public catalog, quote engine, measurements, saved designs, trend collections)
- Added storefront pages, account area, admin Trend collections; MotionConfig honours reduced motion

## Phase 6
- Added render-state engine, 3D viewer, placeholder models, 2D fallback, preview panel; /customize/:slug route
- Added model upload endpoint, model URL validation, stricter binding validation; multipart limit raised to 20 MB (images still capped at 5 MB in code)
- Added vitest unit tests and docs/3D_MODEL_GUIDE.md

## Phase 7
- Added V7 migration and the tailor module (categories, profiles, availability, admin and tailor endpoints)
- Approval assigns a category by id; quote engine supports the tailor charge; DesignConfig gained tailorCategoryId
- Added admin Tailor categories / Tailors pages, tailor dashboard and profile, tailor choice on the product page

## Phase 8
- Added V8 migration and the address, coupon, cart, order and payment modules; Razorpay REST gateway behind `PaymentGateway`
- Added storefront cart/checkout/orders/addresses pages and admin orders/payments/coupons pages; checkout charges in Website settings
- Added frontend/.env.example (Google Maps key) and an expanded Razorpay/Maps section in SETUP_GUIDE.md

## Phase 9
- Added V9 migration and the assignment module (notification of eligible tailors, atomic accept, admin assign/reassign, escalation job)
- OrderService publishes OrderReadyForAssignment; state machine allows reassignment from FABRIC_SAMPLE_PENDING; TailorService dashboard uses real counts
- Added tailor order pages, admin assignment panel, assignment rules in settings; Testcontainers + Failsafe for the concurrency test

## Fix after Phase 9
- V10 migration: user_verifications.channel widened from VARCHAR(10) to VARCHAR(20); 'PASSWORD_RESET' (14 chars) made forgot-password and admin-invitation emails fail with "value too long for type character varying(10)"
- Free-text reasons are now length-checked before they reach columns with limits (user status 500, tailor rejection 500, assignment 250)
- Added PasswordResetIT (Docker): runs the real migrations and the forgot-password path

## Phase 10
- Added V11 migration and the approval module (FabricSampleService/Controller); SamplePanel for customer, tailor and admin
- Added FabricSampleServiceTest and FabricApprovalFlowIT (Docker)

## Phase 11
- Added V12 migration and the fulfilment module (ProgressService, DeliveryService, CourierTrackingProvider interface)
- OrderService.transition can attach photos; timelines return photos
- Added TrackingStepper, DeliveryPanel, ProgressPanel; tests ProgressServiceTest and FulfilmentFlowIT

## Phase 12
- Added V13 migration and the finance module (ledger, earnings, payouts, cancellation policy, refunds, report)
- PaymentGateway gained refund(); PaymentService writes ledger entries and routes refund webhooks; DeliveryService accrues earnings on delivery
- Added admin Refunds / Earnings & payouts / Financial report pages, tailor Earnings page, customer cancel and refund panels
- Tests: FinancialSplitTest, CancellationPolicyTest, FinanceFlowIT, new OrderStatusMachine cases
