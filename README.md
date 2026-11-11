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
- Monitoring: sensors, readings, health events, occupancy conflicts, incident alerts with documented resolutions, critical capacity alerts, and reports.
- Billing: payment tokens, payments, subscriptions, charges, balances, refunds, and billing documents.

## Subscription pricing

Driver plan prices, discounts, and Tenant prices are fixed in the backend.

- Driver monthly plan: S/ 15 with a 10% discount on the final reservation price.
- Driver annual plan: S/ 160 with a 15% discount on the final reservation price.
- The driver discount does not apply to fines, overtime, or other additional charges.
- Tenant monthly plan: S/ 30 for app usage plus S/ 1 for every registered parking spot in the tenant. The count includes all registered spots, regardless of operational status.
- When a parking admin adds a spot during an active paid period, the backend calculates an immediate prorated adjustment using the tenant's default payment token. The spot remains inactive until payment is approved; the subscription snapshot is then updated.

Payments and refunds use the academic simulator. Register a synthetic payment token such as `simulated-visa-4242`. Charges start in `PENDING` and resolve asynchronously after the database transaction commits. A token ending in `0000` produces a rejection, one ending in `1111` stays pending, and other tokens produce an approval. Refunds pass through `PROCESSING` before completion. The simulator uses synthetic references and moves no money. Use the reconciliation endpoints for pending operations, including after a process restart. Retry rejected payments after 60 seconds. Subscription periods expire automatically and a new period is purchased through the subscription API.

## Local development

The default profile is dev. It recreates the mapped database schema at every startup and loads demo data from src/main/resources/db.json. Use only with a disposable local database.

Run the application:

~~~sh
./mvnw spring-boot:run
~~~

Swagger UI is available at /swagger-ui/index.html. The OpenAPI document is available at /v3/api-docs.

## Client connections

Use the same API base URL in the Driver app, Parking Admin app, and SuperAdmin web app. For Render, use the backend service's HTTPS URL. For an Android emulator, use `http://10.0.2.2:8080` when running Maven locally, or `http://10.0.2.2:18080` when Docker maps the API to host port 18080. Allow HTTP in the app's debug configuration when using a local HTTP URL. On a physical device, use the development machine's LAN address and published port. Set `CORS_ALLOWED_ORIGIN` to the SuperAdmin web app's origin, including scheme and port if applicable.

Drivers can register or sign in, Parking Admins sign in with a provisioned account, and SuperAdmins sign in with an administrative account. Send the access token as `Authorization: Bearer <token>` on authenticated requests. Configure `GOOGLE_CLIENT_ID` with the OAuth Web client ID used as the audience of the Driver app's Google ID token. Configure `RESEND_API_KEY` and `APP_PASSWORD_RESET_FROM_EMAIL` with a verified sender for password recovery and Parking Admin invitations.

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
| APP_PASSWORD_RESET_FROM_EMAIL | Verified sender address for password recovery and Parking Admin invitations. |
| RESEND_API_KEY | Resend API key for password recovery and Parking Admin invitations. |
| RESEND_API_URL | Resend endpoint; defaults to https://api.resend.com/emails. |
| EVIDENCE_STORAGE_DIRECTORY | Evidence upload directory. Use /tmp/spotgo-evidence for temporary container storage. Uploaded files in /tmp are lost when the instance is replaced or restarted. |
| MONITORING_MINIMUM_QUALITY | Minimum accepted sensor quality, from 0 to 100; defaults to 80. |
| MONITORING_FRESHNESS_SECONDS | Maximum age of a sensor reading in seconds; defaults to 300. |
| CORS_ALLOWED_ORIGIN | Additional frontend origin, including scheme and host. |
| GOOGLE_CLIENT_ID | Google OAuth client ID accepted as the audience for verified Google ID tokens. Google sign-in and account-linking endpoints return 503 until configured; password authentication remains available. |

FCM configuration:

| Variable | Purpose |
| --- | --- |
| FCM_ENABLED | Set to `true` to enable Firebase Cloud Messaging; defaults to `false`. |
| FCM_PROJECT_ID | Firebase project ID; required when FCM is enabled. |
| FCM_SERVICE_ACCOUNT_JSON | Firebase Admin service-account JSON; store the full value as a Render secret and provide it when FCM is enabled. |

## Domain events and push notifications

Identity, Profiles, Parking, Billing, and Monitoring publish typed in-process events through Spring's application event publisher. Transactional listeners process them only after the database transaction commits. The notification listener sends selected reservation, payment, subscription, and monitoring events through Firebase Admin SDK.

To enable push notifications:

1. Configure both mobile apps in the same Firebase project used by the backend. Install each app's own Firebase client configuration and grant notification permission where required.
2. Set `FCM_ENABLED=true`, `FCM_PROJECT_ID`, and `FCM_SERVICE_ACCOUNT_JSON` on the backend. Keep the full service-account JSON in the backend's secret configuration and restart or redeploy the service to apply it.
3. After sign-in, send the app's FCM registration token to `POST /api/v1/notifications/devices` and repeat when Firebase refreshes the token. Use `GET /api/v1/notifications/devices` to list registrations and `DELETE /api/v1/notifications/devices/{deviceTokenId}` to remove the device before signing out. These endpoints require authentication and never return the raw Firebase token.
4. Trigger a reservation or simulated payment status change and verify that the app receives its push notification. Firebase App Distribution is used to distribute app builds. The backend sends push notifications through FCM.

Google sign-in accepts a Google ID token at `POST /api/v1/authentication/google`; it never accepts a client-supplied Google user ID. First sign-in creates a DRIVER account. If the verified email already belongs to a SpotGo account, sign in with that account and link the Google identity using `POST /api/v1/authentication/google/link` with its access token. Configure the same OAuth Web client ID as the token audience in `GOOGLE_CLIENT_ID` and in the mobile sign-in flow.

Event delivery is in-process and asynchronous, not backed by a durable queue or outbox. A process failure after commit or a rejected FCM request can therefore prevent a push from arriving; the application records the failure in its logs.

Production loads the sample records from `src/main/resources/db.json` and uses Hibernate schema update. The seeder inserts missing fixture IDs and leaves existing rows unchanged. `db.json` does not contain a `SUPER_ADMIN`; the first production superadmin is created from the bootstrap environment variables. All other seeded demo accounts use `DEMO_ACCOUNT_PASSWORD`. Database schema updates are not a substitute for versioned migrations when a production database already contains data.

## Container

Build the image:

~~~sh
docker build -t spotgo-backend .
~~~

Run the container with production configuration supplied through your deployment environment. For local container use, provide the required environment variables and map the application port.

## Evidence storage

Set `EVIDENCE_STORAGE_DIRECTORY` to `/tmp/spotgo-evidence` for temporary uploads or to a writable directory on a persistent volume to retain evidence across container replacements.
