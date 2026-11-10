# SpotGo Backend

REST API for the SpotGo smart parking platform. It provides identity and access management, profiles, parking operations, occupancy monitoring, reservations, and billing.

## Technology

- Java 25 LTS
- Spring Boot 4.1.2
- Spring Security with JWT sessions
- PostgreSQL and Spring Data JPA
- OpenAPI and Swagger UI

## Application areas

- Identity and access: accounts, credentials, roles, sessions, and audit events.
- Profiles: driver profiles, vehicles, and driver classifications.
- Parking: tenants, zones, spots, floor plans, maps, reservations, and parking sessions.
- Monitoring: sensors, readings, health events, occupancy conflicts, alerts, and reports.
- Billing: payment tokens, payments, subscriptions, charges, balances, refunds, and billing documents.

## Subscription pricing

Driver plan prices, discounts, and Tenant prices are fixed in the backend.

- Driver monthly plan: S/ 15 with a 10% discount on the final reservation price.
- Driver annual plan: S/ 160 with a 15% discount on the final reservation price.
- The driver discount does not apply to fines, overtime, or other additional charges.
- Tenant monthly plan: S/ 30 for app usage plus S/ 1 for every registered parking spot in the tenant. The count includes all registered spots, regardless of operational status.
- When a parking admin adds a spot during an active paid period, the backend calculates an immediate prorated adjustment using the tenant's default payment token. The spot remains inactive until payment is approved; the subscription snapshot is then updated.

The default payment provider is simulated for academic demonstrations. Register synthetic token references such as `simulated-visa-4242`; card numbers and security codes are rejected. Charges are approved and refunds are completed by the simulation without contacting an external service or moving money. Replace the provider bean before processing real payments. Period expiration is scheduled; automatic monthly renewal is not implemented. A rejected payment can be retried manually after 60 seconds; pending or unknown results must be reconciled first, and payment retries are not scheduled automatically.

## Local development

The default profile is dev. It recreates the mapped database schema at every startup and loads demo data from src/main/resources/db.json. Use only with a disposable local database.

Run the application:

~~~sh
./mvnw spring-boot:run
~~~

Swagger UI is available at /swagger-ui/index.html. The OpenAPI document is available at /v3/api-docs.

## Render deployment

Select Docker as the Render runtime. The Dockerfile builds the application with Java 25 and runs the production profile. Render provides PORT.

Required environment variables:

| Variable | Purpose |
| --- | --- |
| SPRING_PROFILES_ACTIVE | Set to production. |
| SPRING_DATASOURCE_URL | PostgreSQL JDBC connection URL. |
| SPRING_DATASOURCE_USERNAME | PostgreSQL username. |
| SPRING_DATASOURCE_PASSWORD | PostgreSQL password. |
| JWT_SECRET | Secret used to sign JWTs; provide at least 32 bytes. |
| DEMO_ACCOUNT_PASSWORD | Password assigned to the demo accounts loaded from db.json. Use a strong secret, separate from the superadmin password, and keep it configured while the demo data is enabled. |
| SUPERADMIN_BOOTSTRAP_EMAIL | Initial SUPER_ADMIN email; needed only while the database has no SUPER_ADMIN. |
| SUPERADMIN_BOOTSTRAP_PASSWORD | Initial SUPER_ADMIN password; needed only while the database has no SUPER_ADMIN. It must contain at least 8 characters, including uppercase, lowercase, a number, and a symbol. Store it as a secret and remove it after the first successful startup. |

The Tenant monthly plan costs S/ 30 for app use plus S/ 1 for every registered parking spot, including inactive spots. The Driver monthly and annual plans cost S/ 15 and S/ 160 with 10% and 15% reservation discounts, respectively.

Optional environment variables:

| Variable | Purpose |
| --- | --- |
| APP_PASSWORD_RESET_FROM_EMAIL | Verified sender address used by password reset emails. |
| RESEND_API_KEY | Resend API key. Password reset email delivery is unavailable without it. |
| RESEND_API_URL | Resend endpoint; defaults to https://api.resend.com/emails. |
| EVIDENCE_STORAGE_DIRECTORY | Evidence upload directory. Use /tmp/spotgo-evidence for temporary container storage. Uploaded files in /tmp are lost when the instance is replaced or restarted. |
| MONITORING_MINIMUM_QUALITY | Minimum accepted sensor quality, from 0 to 100; defaults to 80. |
| MONITORING_FRESHNESS_SECONDS | Maximum age of a sensor reading in seconds; defaults to 300. |
| CORS_ALLOWED_ORIGIN | Additional frontend origin, including scheme and host. |

Production loads the sample records from `src/main/resources/db.json` and uses Hibernate schema update. The seeder inserts missing fixture IDs and leaves existing rows unchanged. `db.json` does not contain a `SUPER_ADMIN`; the first production superadmin is created from the bootstrap environment variables. All other seeded demo accounts use `DEMO_ACCOUNT_PASSWORD`. Database schema updates are not a substitute for versioned migrations when a production database already contains data.

## Container

Build the image:

~~~sh
docker build -t spotgo-backend .
~~~

Run the container with production configuration supplied through your deployment environment. For local container use, provide the required environment variables and map the application port.

## Evidence storage

Setting EVIDENCE_STORAGE_DIRECTORY to a temporary path does not disable image uploads. It stores uploaded evidence in the container's temporary filesystem. Use persistent storage if files must survive restarts; implement an explicit storage-disabled mode if uploads should be rejected.
