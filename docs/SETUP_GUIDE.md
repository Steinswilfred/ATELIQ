# Setup Guide

## Prerequisites
JDK 21, Maven 3.9+, Node 20+, Docker.

## Run locally
```bash
cp .env.example .env                 # set JWT_SECRET (32+ chars)
docker compose up -d                 # Postgres, Redis, Mailpit (mail UI http://localhost:8025)
cd backend && export $(grep -v '^#' ../.env | xargs) && mvn spring-boot:run
cd frontend && npm install && npm run dev      # http://localhost:5173
```
Check: `curl localhost:8080/api/v1/public/ping`, Swagger at `http://localhost:8080/swagger-ui.html`.

## Razorpay (online payments, Phase 8)
1. Create a Razorpay account and open **Test Mode**. Create API keys. Put them in `.env`: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`. The secret stays on the backend only; the frontend receives the public key id from the server per payment.
2. Webhook: Dashboard → Settings → Webhooks → add `https://<public-host>/api/v1/payments/razorpay/webhook`. In local development expose the backend with a tunnel (for example ngrok) because Razorpay cannot call localhost. Choose a webhook secret, put it in `RAZORPAY_WEBHOOK_SECRET`, and enable at least: `payment.captured`, `payment.failed`, `order.paid`, `refund.created`, `refund.processed`, `refund.failed`. (Refund events are stored now; refund processing arrives with the financial module.)
3. Restart the backend. With no keys set, the checkout shows online payment as unavailable and Cash on Delivery still works.
4. Test the flow in Test Mode using the test cards, UPI ids and net-banking options listed in Razorpay's test-mode documentation (including their "payment fails" cases). Check: successful payment confirms the order; closing the window leaves the order payable from My orders; a failed payment can be retried; a repeated webhook changes nothing.
5. Going live: switch to Live keys through production environment variables only, re-create the webhook with a new secret, set `COOKIE_SECURE=true`, serve over HTTPS. Never commit keys.

## Google Maps (address search and pin, Phase 8)
Optional. Create a key with the Maps JavaScript API and Places API enabled, restrict it to your site's referrer, and set `VITE_GOOGLE_MAPS_API_KEY` in `frontend/.env`. Without it, addresses are entered by hand. A browser key is public by design, so restrict it.

## Checkout charges
Tax rate, delivery charge, free-delivery threshold and the Cash on Delivery switch are under Admin → Website settings → Checkout charges. The starting values (5% tax, ₹99 delivery) are examples: confirm the right GST treatment for your business with your accountant.
