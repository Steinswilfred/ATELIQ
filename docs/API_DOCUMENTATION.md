# API Documentation

Base path `/api/v1`. Swagger UI at `/swagger-ui.html` when the backend runs. Envelope: `{ success, data | error{code,message,details}, timestamp }`.

## Implemented
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | /public/ping | none | liveness check |

## Planned (by module)
- **auth**: POST register/customer, register/tailor, login, refresh, logout, verify-email, send-phone-otp, verify-phone, forgot-password, reset-password, change-password
- **admin**: /admin/admins CRUD + permissions, /admin/users, /admin/roles, /admin/permissions, /admin/tailor-applications (+ approve, reject, request-info), /admin/settings, /admin/logs/*, /admin/reports/*
- **catalog**: GET /public/categories, /public/products, /public/products/{id}/customization-options, /public/trending
- **customer**: /me, /me/addresses, /me/measurement-profiles, /me/designs, /cart, /checkout, /orders, /orders/{id}/samples/{sid}/approve|reject
- **tailor**: /tailor/orders/available, POST /tailor/orders/{id}/accept (atomic), /tailor/orders/{id}/samples, /tailor/orders/{id}/status, /tailor/earnings
- **payments**: POST /payments/razorpay/order, /payments/razorpay/verify, POST /payments/razorpay/webhook (HMAC), /admin/payments, /admin/refunds, /admin/payments/{id}/collect-cod
- **realtime**: STOMP endpoint `/ws`, user queues `/user/queue/notifications`, `/user/queue/orders`, tailor topic per category `/topic/tailor-category/{id}`

## Phase 2 endpoints (implemented, unverified)
Public: POST /auth/register/customer, /auth/register/tailor, /auth/verify-email, /auth/verify-phone, /auth/resend-email, /auth/resend-phone-otp, /auth/login, /auth/refresh (cookie), /auth/logout, /auth/forgot-password, /auth/reset-password.
Authenticated: GET /me, POST /me/password.
Admin (permission in brackets): GET|POST /admin/roles, PUT|DELETE /admin/roles/{id}, PUT /admin/roles/{id}/permissions, GET|POST /admin/permissions [ROLE_MANAGE]; PUT /admin/users/{id}/roles [ADMIN_MANAGE]; POST /admin/admins [ADMIN_CREATE].
Login error codes (HTTP 403 unless noted): TAILOR_PENDING_APPROVAL, TAILOR_REJECTED, ACCOUNT_BLOCKED, ACCOUNT_SUSPENDED, EMAIL_NOT_VERIFIED, PHONE_NOT_VERIFIED, ACCOUNT_DEACTIVATED; INVALID_CREDENTIALS (401); ACCOUNT_LOCKED (429).

## Phase 3 endpoints (implemented, unverified)
- GET /public/settings (public theme/site settings)
- GET|PUT /admin/settings, PUT /admin/settings/{key} [SETTINGS_MANAGE]; keys: site.name, site.tagline, site.logoUrl, site.faviconUrl, theme.colors, theme.fonts, homepage.banners
- GET /admin/dashboard (any admin)
- GET /admin/users?type&status&q&page&size, GET /admin/users/{id} [USER_VIEW]; PUT /admin/users/{id}, POST /admin/users/{id}/status {action: BLOCK|SUSPEND|REACTIVATE, reason} [USER_MANAGE]. Staff accounts need ADMIN_MANAGE.
- GET /admin/tailor-applications?status&q&page&size, GET /{id}; POST /{id}/approve {category, notes}, /reject {reason, notes}, /request-info {message}, /notes {notes} [TAILOR_APPLICATION_REVIEW]
- GET /me/notifications, POST /me/notifications/{id}/read, POST /me/notifications/read-all

## Phase 4 endpoints (implemented, unverified)
- POST /admin/uploads/images (multipart `file`) → {url}
- /admin/categories GET, POST, PUT/{id}, DELETE/{id} [CATEGORY_MANAGE]; GET also with PRODUCT_VIEW
- /admin/fabrics, /admin/colors, /admin/sizes: GET, POST, PUT/{id}, DELETE/{id} [FABRIC_MANAGE]
- GET /admin/customization/groups (groups with options); POST|PUT|DELETE /admin/customization/groups[/{id}], /admin/customization/options[/{id}] [CUSTOMIZATION_MANAGE]
- GET /admin/products?categoryId&status&q&page&size, GET /{id} [PRODUCT_VIEW]; POST, PUT /{id}, DELETE /{id}, POST /{id}/status, PUT /{id}/sizes, PUT /{id}/fabrics, PUT /{id}/customization, POST /{id}/images, POST /{id}/images/{imageId}/primary, DELETE /{id}/images/{imageId} [PRODUCT_WRITE]
- Render binding shape: {"type": "mesh_toggle|material_color|texture|morph_target|layer_2d", "target": "name", "value": "optional"}
- Conflict (409) is returned when deleting something still in use.

## Phase 5 endpoints (implemented, unverified)
Public: GET /public/home, /public/categories, /public/products?section&category&q&minPrice&maxPrice&fabricId&size&featured&trending&sort&page&pageSize, /public/products/{slug}, /public/trending, /public/measurement-fields; POST /public/products/{id}/quote {sizeCode, measurementProfileId, fabricId, colorId, options:{groupId:optionId}} → {valid, errors, basePrice, lines, total}.
Customer (role CUSTOMER): GET|POST /me/measurements, PUT|DELETE /me/measurements/{id}; GET|POST /me/designs, GET|PUT|DELETE /me/designs/{id}.
Admin: GET /admin/collections [PRODUCT_VIEW]; POST, PUT/{id}, DELETE/{id}, PUT/{id}/products [PRODUCT_WRITE].

## Phase 6 changes (implemented, unverified)
- POST /admin/uploads/models (multipart `file`, .glb only, 20 MB) [PRODUCT_WRITE] → {url}
- Product `modelUrl` accepts `builtin:shirt`, `builtin:trousers`, or a hosted/https `.glb`.
- Render binding value rules: material_color = hex or empty; texture and layer_2d = image address (/media/ or https://) required; morph_target = number 0-1; mesh_toggle = none.

## Phase 7 endpoints (implemented, unverified)
- Public: GET /public/tailor-categories. Quote/design config gains optional `tailorCategoryId`.
- Admin: GET /admin/tailor-categories [TAILOR_CATEGORY_MANAGE, TAILOR_APPLICATION_REVIEW or USER_VIEW]; POST, PUT/{id}, DELETE/{id} [TAILOR_CATEGORY_MANAGE]
- Admin: GET /admin/tailors?categoryId&availability&status&q&page&size, GET /admin/tailors/{userId} [USER_VIEW]; PUT /admin/tailors/{userId}/category {categoryId}, PUT /admin/tailors/{userId}/availability {status, maxActiveOrders, note, availableAgainOn} [TAILOR_CATEGORY_MANAGE]
- Approval body changed: POST /admin/tailor-applications/{id}/approve {categoryId, notes}
- Tailor (role TAILOR): GET /tailor/dashboard, GET|PUT /tailor/profile, PUT /tailor/availability {status, note, availableAgainOn}, POST /tailor/uploads/photo; password via POST /me/password

## Phase 8 endpoints (implemented, unverified)
Customer (role CUSTOMER): GET|POST /me/addresses, PUT|DELETE /me/addresses/{id}; GET /me/cart, GET /me/cart/count, POST /me/cart/items, PUT|DELETE /me/cart/items/{id}; POST /me/checkout/preview {tailorCategoryId, couponCode}; POST /me/checkout {addressId, tailorCategoryId, couponCode, paymentMethod RAZORPAY|COD, deliveryInstructions}; POST /me/orders/{id}/pay (retry); POST /me/payments/razorpay/verify {razorpayOrderId, razorpayPaymentId, razorpaySignature}; GET /me/orders, GET /me/orders/{id}, GET /me/orders/{id}/invoice (HTML).
Public: POST /payments/razorpay/webhook (HMAC-authenticated; headers X-Razorpay-Signature, X-Razorpay-Event-Id).
Admin: GET /admin/orders, /admin/orders/{id}, /admin/orders/{id}/invoice [ORDER_VIEW]; GET /admin/payments, /admin/payments/{id} [PAYMENT_VIEW]; POST /admin/payments/{id}/collect-cod [PAYMENT_COD_COLLECT]; GET|POST|PUT|DELETE /admin/coupons [COUPON_MANAGE]. New settings keys: checkout.taxRatePercent, checkout.deliveryCharge, checkout.freeDeliveryAbove, checkout.codEnabled.

## Phase 9 endpoints (implemented, unverified)
Tailor (role TAILOR): GET /tailor/orders/available; POST /tailor/orders/{id}/accept (409 ORDER_TAKEN / NOT_AVAILABLE / AT_CAPACITY); GET /tailor/orders?scope=active|completed; GET /tailor/orders/{id} (own orders only).
Admin: GET /admin/orders/{id}/assignment [ORDER_VIEW]; POST /admin/orders/{id}/assignment {tailorUserId | null, reason} [ORDER_MANAGE] (null = return to pool). New settings keys (non-public): assignment.requireAvailable, assignment.respectCapacity, assignment.escalationHours. Order detail now includes assignedTailor (first name).

## Phase 10 endpoints (implemented, unverified)
Tailor: POST /tailor/orders/{id}/samples/upload (multipart `file`, own order, only while a sample is wanted) -> {url}; POST /tailor/orders/{id}/samples {photoUrls[1..6], note}; GET /tailor/orders/{id}/samples.
Customer: GET /me/orders/{id}/samples; POST /me/orders/{id}/samples/{sampleId}/approve; POST /me/orders/{id}/samples/{sampleId}/reject {reason}.
Admin: GET /admin/orders/{id}/samples [ORDER_VIEW] (read-only).
Errors: 409 SAMPLE_NOT_WANTED, NOT_WAITING_FOR_YOU, ALREADY_DECIDED; 404 for orders that are not the caller's.

## Phase 11 endpoints (implemented, unverified)
Tailor: POST /tailor/orders/{id}/progress {status: CUTTING_IN_PROGRESS|STITCHING_IN_PROGRESS|QUALITY_CHECK|READY_FOR_DISPATCH, note, photoUrls}; POST /tailor/orders/{id}/progress/upload (only at quality check). Errors: 409 STEP_NOT_ALLOWED, NOT_AT_QUALITY_CHECK.
Customer: GET /me/orders/{id}/delivery.
Admin: GET /admin/orders/{id}/delivery [ORDER_VIEW]; POST /admin/orders/{id}/delivery/ship {courierName, trackingNumber, trackingUrl, estimatedDelivery, note}; POST .../delivery/status {status: OUT_FOR_DELIVERY|DELIVERED, note, location}; POST .../delivery/events {note, location} [DELIVERY_MANAGE].
Order detail timeline entries now include `photos`.

## Phase 12 endpoints (implemented, unverified)
Customer: GET /me/orders/{id}/cancellation; POST /me/orders/{id}/cancel {reason}; GET /me/orders/{id}/refunds.
Tailor: GET /tailor/earnings.
Admin: POST /admin/orders/{id}/cancel {reason, refundAmount} [ORDER_MANAGE; refund needs REFUND_INITIATE]; POST /admin/orders/{id}/refund {amount, reason, reverseTailorEarning} [REFUND_INITIATE]; GET /admin/refunds?status [PAYMENT_VIEW]; POST /admin/refunds/{id}/approve | reject {reason} | retry | manual-processed {reference} [REFUND_APPROVE]; GET /admin/earnings/balances, GET /admin/earnings, GET /admin/payouts, POST /admin/payouts {tailorUserId, reference, note} [EARNINGS_MANAGE]; GET /admin/reports/finance?from&to [REPORT_VIEW].
Settings (non-public): finance.commissionPercent, finance.settlementHoldDays, finance.lateCancellationFeePercent, finance.autoApproveCustomerRefunds.
Razorpay webhook now also processes refund.created / refund.processed / refund.failed.
