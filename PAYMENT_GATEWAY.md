# PB-19: hosted PayHere sandbox checkout

The opt-in gateway uses PayHere's [Checkout API](https://support.payhere.lk/api-%26-mobile-sdk/checkout-api).
The endpoint is fixed to **sandbox**; this change does not enable live charges.
The existing local card simulation remains the default development behavior. Cash on delivery still works in either mode.

Configure these environment variables privately, without committing credentials:

- `ELECTRONEST_PAYHERE_ENABLED=true`
- `PAYHERE_MERCHANT_ID`: your sandbox merchant ID
- `PAYHERE_MERCHANT_SECRET`: the sandbox secret for the registered domain
- `ELECTRONEST_PUBLIC_URL`: that domain's HTTPS origin, without a path or trailing slash

PayHere must be able to POST to `/api/payments/payhere/notify` on that origin.
A localhost URL is insufficient. Merchant credentials and domain approval come from PayHere.
When enabled but incompletely configured, online checkout fails closed; it cannot fall back to a simulated success.

## Behavior

The authenticated customer starts checkout using a CSRF-protected POST. The server reserves stock under the order lock and signs the persisted discounted total. Card details are entered on PayHere, not ElectroNest. Existing saved-card metadata cannot authorize a gateway payment.

Only a verified provider notification with the expected merchant, checkout reference, amount and currency confirms payment and creates the existing digital receipt/PDF. The return/cancel browser URLs only display database state. Repeated notifications do not issue extra receipts or reserve/release stock twice. Successful payments cannot be downgraded by delayed pending/failure notifications.

A signed failure/cancellation releases reserved stock. Chargebacks or unexpected success after a terminal failure are flagged for administrator reconciliation and block fulfilment. The app does not silently treat these as refunds.

## Verification and operational limits

Automated tests use a test-only merchant secret and signed mock notifications. They verify totals, stock, receipts, invalid signatures, mismatched data, ownership, CSRF and direct-card bypass protection. These tests are **not** evidence of a transaction accepted by PayHere.

Before considering PB-19 externally verified, use a configured sandbox account and registered public origin to check hosted checkout, success, decline/cancel, delayed notifications, return before notification, and receipt/PDF download. Use sandbox test cards only.

Pending payments retain their reservation until a signed terminal notification arrives. The app intentionally does not create a second pending checkout for the same order. An abandoned hosted checkout or a missing callback requires provider reconciliation; there is no automatic timeout that might release stock for an already charged payment.

Gateway refunds/cancellations cannot use the existing local status-change API: that would incorrectly report money returned. Provider refund/retrieval reconciliation and a live-provider rollout are **not implemented**. Resolve gateway exceptions in the sandbox account and reconcile records through a separately reviewed process; do not deploy this sandbox flow for real payments. PB-19 remains pending external sandbox verification and production readiness.
