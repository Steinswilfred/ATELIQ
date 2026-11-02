# Setup Guide

## Prerequisites
Java 21, Maven 3.9+, Node 20+ (22 tested), Docker.

## 1. Environment
```bash
cp .env.example .env        # fill in values; .env is git-ignored
openssl rand -base64 48     # use the output for JWT_SECRET
```
The backend reads real environment variables. Easiest local approach: export the `.env` values in your shell (`set -a; source .env; set +a`) or set them in your IDE run configuration.

## 2. Database and Redis
```bash
docker compose up -d
```

## 3. Backend
```bash
cd backend
mvn clean verify
mvn spring-boot:run
```
Check: `curl localhost:8080/api/v1/system/ping`, Swagger UI at `/swagger-ui.html`. Flyway applies V1-V4 on first start.

## 4. Frontend
```bash
cd frontend
cp .env.example .env
npm install
npm run dev      # http://localhost:5173, /api is proxied to :8080
npm test && npm run build
```

## 5. Razorpay (used from Phase 8; verify details against Razorpay's current docs)
1. Create a Razorpay account and switch the dashboard to **Test Mode**.
2. Settings > API Keys > generate keys. Put them in `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`. The secret lives only in the backend environment, never in `frontend/`.
3. Settings > Webhooks > add `https://<public-host>/api/v1/payments/razorpay/webhook`, choose a secret, store it in `RAZORPAY_WEBHOOK_SECRET`. Subscribe to: `payment.captured`, `payment.failed`, `order.paid`, `refund.created`, `refund.processed`, `refund.failed`.
4. Local webhook testing needs a public tunnel (e.g. ngrok or cloudflared) pointing to `localhost:8080`.
5. Test with Razorpay's test cards/UPI IDs from their docs. Go live only after: Live keys in the production secret store, live webhook configured, HTTPS everywhere, reconciliation job enabled.

## 6. Deployment notes (expanded in Phase 14)
Serve the SPA as static files behind a CDN/reverse proxy; run the API behind HTTPS; managed Postgres with backups; Redis with auth; secrets from a secret manager; set `CORS_ALLOWED_ORIGINS` to the exact production origin.

## Continuing in a new AI session
Read `docs/PROJECT_STATUS.md` first, then inspect the code. Do not regenerate the project.
