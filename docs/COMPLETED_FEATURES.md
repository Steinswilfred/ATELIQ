# Completed Features
Items marked (built) are written and compile/build-checked; (unverified) means written but not yet compiled or run.

## Phase 1
- Frontend scaffold and shared UI (built)
- Backend skeleton, Flyway V1/V2, Docker Compose (unverified)

## Phase 2 (unverified unless noted)
- Customer and tailor registration, email link verification, phone OTP (hashed, 5 attempts, 10 min)
- Login with Spring Security `AuthenticationManager`; account-status gate runs after password check (pending-approval, rejected, blocked, suspended, unverified, deactivated messages); same check on every JWT request
- 15-minute JWT access token; refresh-token rotation with reuse detection (family revoke), HttpOnly cookie
- Forgot/reset password (signs out all sessions), change password
- Failed-login lockout; login, activity and audit logging
- Super Admin bootstrap from env vars
- RBAC API: roles, permissions, role-permission assignment, admin creation (ADMIN_CREATE only), role assignment; Super Admin role immutable; no privilege escalation; all changes audited
- Frontend (built): session store with silent refresh, ProtectedRoute, Login, Register, Tailor register, Verify email/phone, Forgot/Reset password, Account page

## Phase 3 (backend unverified; frontend built)
- Notifications table + service; bell with unread count; admins notified when a tailor completes verification
- Admin dashboard with real counts and recent activity (activity shown only with LOG_VIEW)
- User management: search/filter/paginate, detail with roles, sign-in history, activity; edit name/phone; set verification flags; block, suspend, reactivate (sessions revoked on block/suspend; reactivation never skips verification or approval); Super Admin and self protected
- Tailor applications: list/filter/search, detail, approve (assigns category, grants TAILOR role, activates account), reject (reason shown at login), request info, internal notes, full review history; row-locked so two admins cannot double-decide; email + in-app notifications; audited
- Admin creation/role editing UI, role and permission management UI with permission matrix (escalation guard in backend)
- Website settings API (validated whitelist) and public settings endpoint; admin UI for name, tagline, logo/favicon address, six theme colours, fonts, base size, with live preview; storefront applies theme at load
- Permission-filtered admin sidebar, per-route permission guards, sign-out in dashboard header

## Phase 4 (backend unverified; frontend built)
- Storage abstraction + local implementation; image upload endpoint (type decided from file bytes; JPEG/PNG/WebP/GIF only, 5 MB; SVG refused; random names), served under /media
- Categories (Men/Women/Kids seeded; two levels; delete guarded), colours, sizes (XXS-XXXL seeded), fabrics with colours and price delta
- Dynamic customization: groups and options with price delta and validated render bindings (3D/2D), soft delete
- Products: draft/active/archived, featured/trending flags, images with main image, sizes, fabrics, attached customization groups/options (one default per group, validation), soft delete; list with search/filter/pagination
- Admin UI: Categories, Fabrics/colours/sizes, Customization, Products list, Product editor; generic form dialog and image uploader
- Everything audited; permissions: PRODUCT_VIEW/PRODUCT_WRITE, CATEGORY_MANAGE, CUSTOMIZATION_MANAGE, new FABRIC_MANAGE

## Phase 5 (backend unverified; frontend built)
- Public catalog API: category tree, product search (section, garment type, text, price range, fabric, size, featured/trending, sort, pagination), product detail with active fabrics/colours/groups/options
- Server-side quote/validation engine (`DesignService.quote`): base + fabric + options, required groups, one option per group, colour must belong to fabric, size xor measurement profile; unit-tested pure logic; reused for cart and orders later
- Measurement fields (seeded, with instructions), per-customer profiles (cm storage, cm/in display, range-validated, default profile, max 20)
- Saved designs (validated against live catalog and own profiles, max 50), reopen/update/delete; archived products flagged unavailable
- Trend collections (admin-curated, per-collection product picker, kinds incl. wedding/festival/seasonal) and public Trending page
- Storefront UI: home, Shop/Men/Women/Kids listing with URL-driven filters, product page with live price, trending page, account area (saved designs, measurements); admin Trend collections page
- Motion respects the user's reduced-motion setting

## Phase 6 (backend unverified; frontend built, binding logic unit-tested, 3D not yet seen in a browser)
- Render-binding engine (pure, tested): part toggles with "controlled parts" semantics, material colour (explicit or chosen fabric colour), textures, shapes, 2D layers
- 3D viewer (React Three Fiber): works with built-in placeholder models and uploaded .glb files; live updates, drag-rotate, wheel/pinch and button zoom, Front/Back/Left/Right views, optional 360° spin, reduced-motion aware, lazy-loaded
- 2D fallback with stacked option layers when there is no model, no WebGL or a load error; photo gallery always available
- Procedural placeholder shirt and trousers following the real naming contract
- Admin: choose built-in or upload .glb (header checked, 20 MB); bindings validated server-side by type; clearer binding hints
- docs/3D_MODEL_GUIDE.md: naming rules and binding types for 3D artists

## Phase 7 (backend unverified; frontend built)
- Tailor categories: admin CRUD with skill level, per-garment stitching price, active flag; five EXAMPLE categories seeded with illustrative prices; public list; delete blocked while tailors are assigned
- Approval now assigns a real category and creates the tailor profile and availability; earlier-approved tailors are backfilled by the migration
- Admin tailor management: list/filter/search, detail, change category, set availability and active-order cap, link to account actions; ratings/earnings clearly shown as pending until orders exist
- Tailor portal: dashboard (category and price read-only, availability status/return date/note), profile editor with photo upload, change password
- Quote engine: optional tailor category adds an admin-priced "Stitching" line (unit-tested); product page tailor selector; saved designs keep the choice

## Phase 8 (backend unverified; frontend built; pure-logic tests written)
- Addresses (max 10, default, PIN/phone validation, optional Google Maps search and pin)
- Cart: choices only, always repriced from the live catalog; edit customization, quantities 1-10, invalid/unavailable items flagged and block checkout
- Coupons: percent/fixed, minimum order, cap, date window, total and per-customer limits, row-locked redemption; admin CRUD
- One server-side calculation for preview and order (`CheckoutPricingService` + `PricingCalculator`); configurable tax, delivery, free-delivery threshold, COD switch
- Orders with price/address/measurement/selection snapshots; full 20-status lifecycle enforced by `OrderStatusMachine` (cutting only after FABRIC_APPROVED); order number and invoice number sequences; status history; customer and admin views; printable invoice
- Razorpay: server creates the order from the stored total, browser callback verified by HMAC AND by fetching the payment from Razorpay (amount and order must match; authorized payments are captured), idempotent confirmation under row lock, retry for unpaid orders, webhook with raw-body HMAC, duplicate-event protection, payment audit trail
- Cash on Delivery: no payment marked collected until DELIVERED, admin-recorded and audited
- Admin: Orders, Payments (filters, detail, COD collection), Coupons, Checkout charges settings
- Customer: cart badge, cart, checkout, payment result page (polls while unconfirmed), orders, order detail with progress timeline, addresses

## Phase 9 (backend unverified; frontend built; concurrency test written, not run)
- New orders notify eligible tailors after the order's transaction commits (approved, right category, status/capacity rules, not previously removed from that order)
- Tailor browse: summary without customer identity or measurements; refreshes every 15s
- Atomic first-accept: per-tailor row lock for the capacity check, single conditional UPDATE to claim the order, unique partial index on active assignments as backstop; losers get a clear "already taken" answer
- Accepted order moves through TAILOR_ASSIGNED to FABRIC_SAMPLE_PENDING; customer and tailor are notified; customer sees the tailor's first name
- Tailor job view only for the assigned tailor (404 otherwise); includes full customization and measurements
- Admin: assignment history, assign, reassign (before sample submission), return to pool; mandatory reason; audited; removed tailors never see the order again
- Assignment rules in Website settings (require Available status, respect capacity, escalation hours); scheduled escalation alerts, once per order
- Tailor dashboard shows real active/awaiting/completed counts
- Testcontainers concurrency test and Failsafe wiring

## Phase 10 (backend unverified; frontend built; tests written, not run)
- Tailor sends 1-6 sample photos with an optional note; only the assigned tailor, only while a sample is wanted (first time or after a rejection); photos must be server-stored images
- Customer reviews enlargeable photos and approves or rejects (reason required, 5-500 characters); only the owner, only the sample currently waiting; decisions are row-locked and cannot be repeated
- Revision loop: rejection returns the order to the tailor, who sends a numbered revision; repeated rejections (3+) alert admins
- Order statuses driven through the state machine; cutting remains reachable only from FABRIC_APPROVED
- Complete history: fabric_samples, photos and an append-only fabric_approvals table with person, role and time; shown to customer, tailor and (read-only) admin
- Notifications to customer (in-app and email) on submission and to the tailor on approval or rejection
- One shared SamplePanel for all three viewers

## Phase 11 (backend unverified; frontend built; tests written, not run)
- Tailor production steps (own orders only, one step at a time): cutting, stitching, quality check, ready for dispatch with 1-6 finished-garment photos required; rework from quality check back to stitching; each step notifies the customer; cutting only after fabric approval
- Timeline entries can carry photos (order_status_history.photos)
- Admin shipping and delivery: ship (courier, tracking number, https tracking link, estimated date), out for delivery, delivered, in-transit updates; strictly ordered by the state machine; notifications and emails; COD reminder; delivered unlocks COD cash collection
- Courier integration extension point (`CourierTrackingProvider`), no implementation; delivery updates are admin-managed and labelled as such; no GPS
- Customer: 11-step tracking list derived from real history, delivery panel, 30-second auto refresh
- Tailor: Production panel; admin: Delivery panel with actions

## Phase 12 (backend unverified; frontend built; tests written, not run)
- Earnings: on delivery the order total is split (items net of discount, commission on the stitching charge, tailor net, tax, delivery) into an append-only ledger; one earning per order with the commission rate snapshotted; settlement hold; tailors see held/payable/paid
- Payouts: admin records a bank-transfer payout that settles everything payable for a tailor; audited; tailor notified
- Cancellation: customer policy (before assignment full refund; fee after assignment until cutting; none during production) with an exact preview; admin cancel into production with an optional refund amount; tailor released and notified; unpaid cancellations return the coupon use
- Refunds: Razorpay refund API called outside transactions after a conditional PROCESSING claim; refund webhooks (created/processed/failed) finish or fail them; failed refunds can be retried; manual refunds (COD) recorded with a reference; admin-requested refunds need a different approver; rejected refunds return the order to its previous status; optional reversal of the tailor's earning, with a recovery alert if already paid
- Ledger accounts keep customer payments, item revenue, delivery, commission, tax, tailor payable and post-delivery refunds separate; payments and COD collections now write to it
- Admin: Refunds, Earnings & payouts, Financial report (date range, totals, account breakdown, CSV), cancel/refund actions on the order page, finance settings
- Tailor Earnings page; customer cancel button and refund status on the order page
