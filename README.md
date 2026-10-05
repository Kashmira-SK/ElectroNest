# ElectroNest

Multi-vendor online electronics store — SE2030 Group Project (2026-Y2-S1-KU-25).
The integrated project is on `main`.

## Requirements

- **Java/JDK 21**, with `JAVA_HOME` and `PATH` pointing to that JDK.
- Maven: use the included Maven Wrapper (`./mvnw` or `mvnw.cmd`); a separate Maven installation is not required.
- Docker with Docker Compose for local PostgreSQL development only.

Stack: Spring Boot 4.1.0 · Spring Data JPA · Thymeleaf · PostgreSQL (local) / H2 (demo).
Run all commands below from the repository root after pulling the latest `main`.

## Easy demo mode — H2

No Docker, PostgreSQL, or local database configuration is needed.

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

Open **http://localhost:8081**. The persistent H2 database is created and seeded
on first launch. Existing demo databases are automatically upgraded for the
`CANCELLED` order-payment status without deleting their data. No manual SQL or
reset is needed; subsequent launches use the same command.

See [DEMO.md](DEMO.md) for accounts, persistence details, and demo features.

## Local development — Docker PostgreSQL

### One-time configuration

If `src/main/resources/application-local.properties` does not exist, copy the
included example. Its defaults match Docker Compose; the local file is gitignored.

**Linux/macOS:**

```sh
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

**Windows Command Prompt:**

```bat
copy src\main\resources\application-local.properties.example src\main\resources\application-local.properties
```

**Windows PowerShell:**

```powershell
Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

The Compose service is `postgres`, exposed on host port **5433** (container port
5432). Database and username are both `electronest`; the development password is
`electronest_dev`, matching the example configuration. Data persists in the
`electronest_pgdata` Docker volume.

### Normal startup

**Linux/macOS:**

```sh
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Windows Command Prompt:**

```bat
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**Windows PowerShell:**

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The example configuration uses **http://localhost:8080**. If your existing
`application-local.properties` sets `server.port`, use that port instead.
This profile reads `application-local.properties` and uses Hibernate schema update.

### One-time upgrade for an older PostgreSQL database

Only existing PostgreSQL databases created before the `CANCELLED` order-payment
status need [this migration](database/migrations/20261004_allow_cancelled_order_payment.sql).
Fresh databases do not need it. Hibernate schema update does not expand the old
PostgreSQL CHECK constraint automatically.

With PostgreSQL running and the application stopped, run these commands once
(they work in Linux/macOS shells, Windows Command Prompt, and PowerShell):

```text
docker compose cp database/migrations/20261004_allow_cancelled_order_payment.sql postgres:/tmp/electronest-payment-status.sql
docker compose exec -T postgres psql -U electronest -d electronest -v ON_ERROR_STOP=1 -f /tmp/electronest-payment-status.sql
```

Then start the application normally. This is **not** an every-startup step and
**does not apply to H2/demo users**. The migration preserves existing rows.

## Java troubleshooting

If compilation reports many missing generated methods such as `builder()`,
`getRole()`, `getName()`, or model getters/setters, check:

```sh
java -version
mvn -version
```

If standalone Maven is not installed, use `./mvnw -version` on Linux/macOS,
`mvnw.cmd -version` in Command Prompt, or `.\mvnw.cmd -version` in PowerShell.
Check the wrapper version too if that is how you launch the app. Maven must report
that it is running under **JDK 21**; correct `JAVA_HOME`/`PATH` and reopen the terminal
if necessary. Do not add Lombok-generated methods manually to model classes.

## Team

| IT Number | Name | Module |
|---|---|---|
| IT25102258 | Peramuna P.A.D.T. | Payment Processing |
| IT25102263 | Navodya W.M.G.G.G. | Admin Dashboard, Reports & User Mgmt |
| IT25102315 | Konara K.M.D.M. | Order Management |
| IT25102320 | Abeysinghe R.W.M.W.C.S. | Product Catalog Management |
| IT25102347 | Muddassir H.M. | Product Search & Feedback Mgmt |
| IT25102351 | Kalupahana D.K. | Seller / Vendor Management |
| IT25300301 | Varshidha K. | Shopping Cart & Checkout |
