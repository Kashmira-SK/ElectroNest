# ElectroNest Demo

Use the integrated `main` branch with **Java/JDK 21**. The launchers use the included
Maven Wrapper; a separate Maven installation is not required. Run commands from
the repository root.

## Start the demo

**Linux/macOS:**

```sh
./run-demo.sh
```

**Windows Command Prompt:**

```bat
run-demo.cmd
```

**Windows PowerShell:**

```powershell
.\run-demo.cmd
```

Open **http://localhost:8081**. On subsequent launches, use the same command.
You do not need Docker, PostgreSQL, or `application-local.properties` for demo mode.
For PostgreSQL development, follow the separate [root README instructions](README.md#local-development--docker-postgresql).

## Persistent database and automatic upgrade

The `demo` profile uses H2 file storage at `./data/electronest-demo`
(the database file is `data/electronest-demo.mv.db`). It is persistent across restarts.

- A fresh database is created and seeded automatically with demo accounts, products, and the promo code below.
- Existing H2 demo databases are automatically upgraded for the `CANCELLED` order-payment status by `DemoPaymentStatusMigration`, after Hibernate initializes the schema.
- The compatibility migration preserves existing rows and safely skips databases that already support `CANCELLED`.
- **Do not delete/reset the H2 database or run manual SQL for this upgrade.** Do not run the PostgreSQL migration in demo mode.
- Migration failures stop startup visibly rather than silently continuing.

The existing seeder refreshes the predefined demo accounts/passwords, customer
delivery details, and demo vendor profile on startup. It adds sample products only
when that vendor has none, and adds `WELCOME10` only if missing. It does not clear
existing orders or purchase history.

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | admin@electronest.lk | Admin@123 |
| Customer | customer@electronest.lk | Customer@123 |
| Vendor | vendor@electronest.lk | Vendor@123 |

## Java troubleshooting

For large numbers of missing generated methods (`builder()`, `getRole()`,
`getName()`, getters/setters), run `java -version` and `mvn -version` and ensure
Maven uses **JDK 21**. If Maven is not installed separately, check `./mvnw -version`
(Linux/macOS), `mvnw.cmd -version` (Command Prompt), or `.\mvnw.cmd -version`
(PowerShell). Correct `JAVA_HOME`/`PATH`; do not add generated methods manually.
See [Java troubleshooting](README.md#java-troubleshooting) for details.

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
<!-- order logic test 1 -->
