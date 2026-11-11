# Project Status

**Current phase:** 12 (Financial management): code written. Backend not verified by me; earlier phases are being run by the project owner.

## Verified
- Frontend: `npm run build` (tsc + vite) passes for Phases 1-12; `npm test` (6 render-state tests) passes.

## NOT verified (read this first)
- Phases 8-12 have not been confirmed against a running system by me: Razorpay (including the new refund API call and refund webhooks), Maps, the 3D viewer, the assignment concurrency guarantee, sample, delivery and now all money flows.
- Docker-based integration tests are written but unrun (they start PostgreSQL themselves; run under `mvn verify`): AcceptConcurrencyIT, PasswordResetIT, FabricApprovalFlowIT, FulfilmentFlowIT, **FinanceFlowIT** (earnings split adds up to the order total and is created once; payout after the hold; cancellation refunds at 100% and at 90%; no cancellation once cutting starts; other customers cannot cancel your order; admin refund needs a different approver). The payment provider is faked in that test.
- Unit tests unrun: all the earlier ones plus FinancialSplitTest, CancellationPolicyTest and the new OrderStatusMachine cases.

## DECISIONS I MADE FOR YOU (please confirm or change before going live)
You did not send the three business decisions, so these are defaults, all editable in Admin > Website settings > "Commission, payouts and cancellations":
1. **Platform commission: 15% of the stitching charge** (the item price, delivery and tax are not subject to commission; the platform keeps item revenue and delivery).
2. **Tailor payout: earning created on delivery, payable 7 days later**; payouts are recorded by an administrator after paying the tailor by bank transfer (the system moves no money to tailors).
3. **Cancellation: free and fully refunded before a tailor is assigned; a 10% fee (of the amount paid) after assignment until cutting starts; no online cancellation once cutting has begun.** Customer cancellation refunds are approved automatically (switchable). Admin-requested refunds need approval from a different administrator (a Super Admin may approve their own).
Also confirm with an accountant: GST treatment, whether delivery is taxable, and how commission and tailor payouts are invoiced. The report labels follow the model above, not a tax opinion.

## Exact next task
1. Update from the zip, restart the backend (Flyway applies V13), run `mvn verify` with Docker.
2. Manual checks:
   - Settings: change commission/hold/fee and save; confirm the values show again after reload.
   - Deliver a test order (Phase 11 flow): the tailor's Earnings page shows an earning (stitching charge minus commission) "held until" the hold date; Admin > Reports shows revenue, commission, tax and the tailor-payable total, and the five parts add up to the order total.
   - After the hold (set `payable_after` into the past in the database to test), Admin > Earnings & payouts lists the tailor as payable; "Record payout" with a reference settles it, the tailor is notified, balances go to zero.
   - Customer cancellation: before assignment (full refund), after assignment before cutting (10% fee shown before confirming), once cutting started (button replaced by a message). Online-paid: the refund goes to Razorpay and the order ends REFUNDED; a pending refund finishes through the refund.processed webhook; a cash-on-delivery order that was never paid just cancels.
   - Admin: cancel an order with and without a refund amount; request a refund on a delivered order; the same admin cannot approve it, another admin can; reject returns the order to its previous status; a failed Razorpay refund shows under Failed with Retry; a manual (COD) refund is paid by hand and recorded with a reference.
   - Refund after the tailor was already paid: choose "reverse the tailor's earning"; admins get a "recover manually" alert.
   - A late Razorpay payment arriving for a cancelled order raises an admin alert and can then be refunded (cancelled orders accept an admin refund).
3. Then Phase 13 (notifications and analytics: WebSocket push to replace polling, email/SMS provider integration, reports and charts with CSV/PDF export, activity/security/API-error logs).

## Known gaps / decisions
- Tailor payouts, and refunds for cash-on-delivery orders, are recorded by hand. No automatic transfers (that needs a payout product such as RazorpayX and bank verification).
- Only one open refund per order, and an order ends REFUNDED after a refund even when it is partial (the refund row shows the amount and any fee kept).
- Revenue is recognised at delivery; refunds after delivery are deducted as "revenue refunded". The ledger is append-only and corrections are new opposite entries.
- The finance report has totals, an account breakdown and a CSV of daily figures; charts and PDF come in Phase 13. There is no invoice for commission or credit notes for refunds yet.
- Customers have no "request a return" flow after delivery; returns are handled by an administrator (Request a refund). A customer support page/ticketing is not built.
- Still open from earlier phases: no courier integration, tailor document upload, S3 storage, SMS stub, Redis rate limiting, reapply flow, trending colours/fabrics, measurement guides, real 3D models, no seed catalog, blocked-tailor order handling, reminder for unanswered fabric samples.
