# ElectroNest Demo

Requires Java 21.

## Linux / macOS / Git Bash

Run:

./run-demo.sh

## Windows

Run:

run-demo.cmd

## Open the site

http://localhost:8081

## Demo accounts

Admin

Email: admin@electronest.lk
Password: Admin@123

Customer

Email: customer@electronest.lk
Password: Customer@123

Vendor

Email: vendor@electronest.lk
Password: Vendor@123

## Notes

The demo profile uses its own local H2 database.

You do not need Docker.
You do not need PostgreSQL.
You do not need application-local.properties.

The demo database is stored locally under:

data/

To start the project again later, just run the same demo command.

## Vendor decision emails

Approval, rejection and requests for information email the user's registered address
only after the decision commits. SMTP errors are logged without undoing decisions.
Local/demo runs safely skip email when SMTP or the sender address is unconfigured.
There is no automatic retry; administrators can follow up using the registered address.

For real delivery configure environment variables (never commit credentials):
`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD`, `ELECTRONEST_MAIL_FROM`, and the provider's required
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` / `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`.
SMTP connection, read and write timeouts default to three seconds.
Admin success messages confirm the saved decision, not delivery to an inbox.

## Checkout promo code

The demo profile seeds `WELCOME10` once: 10% off the item subtotal, no minimum
or expiry. Repeated starts keep the existing code and any changes to it.
Use Apply or Remove at checkout. Only one code is held per checkout; applying again
replaces it without stacking. Invalid codes clear the discount and allow full-price checkout.
Delivery edits stay in place when JavaScript is enabled; the form also works without JavaScript.

Codes are persisted in `promo_codes` (uppercase unique code, PERCENTAGE or FIXED,
value, active flag, optional UTC activation/expiry and minimum item subtotal).
Percentage discounts apply to items; fixed discounts are in LKR and capped at the
subtotal plus delivery. Amounts round to two decimals. Delivery currently remains free.
There is no promo administration UI. Codes are revalidated at order creation;
orders and receipts retain their code/discount snapshot even if the promotion later changes.
