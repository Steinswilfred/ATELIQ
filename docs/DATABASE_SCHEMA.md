# Database Schema

PostgreSQL 16, Flyway migrations in `backend/src/main/resources/db/migration`. Hibernate runs with `ddl-auto: validate`.

## Implemented (V1, V2)
users, roles, permissions, user_roles, role_permissions (with `enabled`), user_permission_overrides, user_verifications (hashed tokens/OTP), refresh_tokens (rotation families), login_logs, activity_logs, audit_logs, website_settings. V2 seeds 4 system roles, 21 permissions, Super Admin grants and default theme.

Notes: `users.user_type` + `account_status` drive login gating; partial unique indexes on lower(email)/phone ignore soft-deleted rows; the Super Admin account is created at startup from env vars (Phase 2), never in SQL.

## Planned entities by phase (all with PK, FKs, timestamps; soft delete where noted*)
- **P2/P3** tailor_applications (+ documents, review_history), tailor_profiles, tailor_categories (price, order), notifications
- **P4** categories*, products*, product_images, product_variants, fabrics, colors, sizes, customization_groups, customization_options (price_delta, render_binding JSONB), product_customization_options
- **P5** addresses*, measurement_profiles, measurements, saved_designs*, design_customizations, reviews, coupons
- **P8** carts, cart_items (customization snapshot JSONB), orders, order_items, payments, payment_transactions, payment_webhook_events (unique event_id), invoices
- **P9** tailor_availability, tailor_assignments (partial unique index on active order), assignment_rules
- **P10** fabric_samples, fabric_approvals (actor + timestamp)
- **P11** order_status_history, delivery_tracking
- **P12** refunds, tailor_earnings, commissions, ledger_entries (append-only)
- **P13** api_error_logs, notification_preferences

## Core relationships
```
users 1─* user_roles *─1 roles *─* permissions (role_permissions)
users 1─0..1 tailor_profiles *─1 tailor_categories
users 1─* orders 1─* order_items ─▶ products, saved_designs
orders 1─0..1 tailor_assignments ─▶ tailor_profiles
orders 1─* fabric_samples 1─* fabric_approvals
orders 1─* order_status_history ; 1─* payments 1─* payment_transactions ; payments 1─* refunds
orders 1─* tailor_earnings ─▶ commissions
products *─* customization_options via product_customization_options ; options *─1 customization_groups
```

## V3 (Phase 2)
`tailor_applications`: user_id, address, experience_years, previous_experience, preferred_category, skills, status (tailor application statuses), reviewed_by/at, rejection_reason, internal_notes. Review history table and document uploads come in Phase 3.

## V4 (Phase 3)
`notifications` (user_id, type, title, body, link, read_at); `tailor_applications.approved_category`; `tailor_application_reviews` (application_id, reviewer_id, decision APPROVED|REJECTED|INFO_REQUESTED|NOTE, reason, notes).

## V5 (Phase 4)
categories (2 levels, slug, soft delete), colors, sizes, fabrics (+ fabric_colors), products (status, flags, model_url, soft delete), product_images (one primary per product), product_sizes, product_fabrics, customization_groups, customization_options (price_delta, render_binding JSONB), product_customization_groups, product_customization_options. New permission FABRIC_MANAGE. Seeds: Men/Women/Kids, sizes XXS-XXXL.

## V6 (Phase 5)
trend_collections (kind, source, external_ref, soft delete) + trend_collection_products; measurement_fields (seeded, cm ranges and instructions); measurement_profiles (JSONB in cm, one default per user, soft delete); saved_designs (JSONB configuration, soft delete).

## V7 (Phase 7)
tailor_categories (level, stitching_price, active, soft delete; five example rows), tailor_profiles (one per tailor user, category FK, bio/skills/address/photo, rating fields for later), tailor_availability (status AVAILABLE|BUSY|ON_LEAVE, max_active_orders 1-50, note, return date); tailor_applications.approved_category_id. Backfills profiles for already-approved tailors.

## V8 (Phase 8)
addresses, coupons, coupon_redemptions, carts, cart_items, orders (snapshots, 20-status check), order_items (measurement/selection snapshots), order_status_history, payments (unique provider order/payment ids), payment_transactions (append-only), payment_webhook_events (unique event id), invoices; sequences order_number_seq and invoice_number_seq; permission COUPON_MANAGE; checkout settings seeded with ILLUSTRATIVE defaults.

## V9 (Phase 9)
tailor_assignments (method ACCEPTED|ADMIN_ASSIGNED, status ACTIVE|RELEASED, who/why/when; unique partial index allows one ACTIVE per order); orders.escalated_at; index on orders(tailor_id, status); non-public assignment settings seeded.

## V10-V11
V10 widened user_verifications.channel to VARCHAR(20). V11: fabric_samples (revision, status PENDING|APPROVED|REJECTED, unique partial index: one PENDING per order), fabric_sample_photos, fabric_approvals (append-only: SUBMITTED|APPROVED|REJECTED with actor and role).

## V12 (Phase 11)
order_status_history.photos (JSONB); delivery_tracking (one per order: courier, tracking number/link, estimated date, shipped/delivered times, provider MANUAL); delivery_events (status, note, free-text location, who and when).

## V13 (Phase 12)
tailor_earnings (one per order, commission snapshot, status ACCRUED|PAID|REVERSED, payable_after, needs_recovery), tailor_payouts, refunds (kind, amount, fee_retained, status, method RAZORPAY|MANUAL, approvals, provider_refund_id; one open refund per order), ledger_entries (append-only, 7 accounts, signed amounts); permission EARNINGS_MANAGE; finance settings seeded with ILLUSTRATIVE defaults. State machine: cancellation allowed up to ready-for-dispatch (customer rule is narrower), REFUND_PENDING may return to DELIVERED or CANCELLED.
