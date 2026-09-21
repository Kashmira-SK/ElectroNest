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
