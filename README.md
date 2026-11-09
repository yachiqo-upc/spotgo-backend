# SpotGo Backend

REST API for SpotGo. Handles IAM with JWT, parking operations, monitoring, billing, profiles, vehicles, etc. Built with Spring Boot 4.0 and Java 26.

## Tech Stack

- **Java 26** — Latest LTS
- **Spring Boot 4.0.7** — Web, Data JPA, Validation, Security, Mail, DevTools
- **PostgreSQL** — Primary database
- **JWT (jjwt 0.12.6)** — Stateless authentication
- **Springdoc OpenAPI 3.0.3** — API documentation (Swagger UI)
- **Lombok** — Boilerplate reduction
- **DDD** — Domain-driven design with clear bounded contexts

## Architecture

The project follows **Domain-Driven Design** with a **hexagonal (ports & adapters)** structure, organized into these bounded contexts:

```
src/main/java/com/yaqhiqo/spotgo/
├── iam/            # Email/password auth, JWT, users
├── parking/        # Core domain: parkings, blueprints, spots, reservations
├── monitoring/     # Employees, occupancy by hour, weekly trends
├── billing/        # Billing domain: receipts, subscriptions, client plans
├── profiles/       # Profiles, vehicles and driver classifications
└── shared/         # Shared kernel: base entities, error handling, config
```

Each context follows the same layered pattern:

| Layer | Purpose |
|-------|---------|
| `domain/model/aggregates/` | Domain entities / aggregates |
| `domain/model/valueobjects/` | Value objects |
| `domain/model/commands/` | CQRS commands |
| `domain/model/queries/` | CQRS queries |
| `application/internal/commandservices/` | Command handlers |
| `application/internal/queryservices/` | Query handlers |
| `interfaces/rest/` | REST controllers |
| `interfaces/rest/resources/` | Request/response DTOs |
| `infrastructure/persistence/jpa/` | JPA repositories & entities |

## Auth And IDs

- Authentication is based on `email + password`.
- JWT is required for protected endpoints using `Authorization: Bearer <token>`.
- Public auth endpoints:
  - `POST /api/v1/authentication/sign-in`
  - `POST /api/v1/authentication/sign-up`
  - `POST /api/v1/authentication/password-reset/request`
  - `POST /api/v1/authentication/password-reset/confirm`
- Public sign-up creates only `client` users.
- IDs are UUID strings across the API.
- `clientReports` also expose a human-friendly sequential `code` like `RPT-00001`.

## CORS Configuration

The backend allows requests from:

- `http://localhost:4200` — Angular dev server
- `https://spotgo-frontend.vercel.app` — Production frontend on Vercel

Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`. Credentials are enabled with a 1-hour max-age cache.

## API Endpoints

### IAM / Profiles

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/authentication/sign-in` | Sign in with email and password |
| `POST` | `/api/v1/authentication/sign-up` | Public client sign-up |
| `POST` | `/api/v1/authentication/password-reset/request` | Request one-time password reset code |
| `POST` | `/api/v1/authentication/password-reset/confirm` | Confirm password reset with code |
| `GET` | `/api/v1/users` | Get all users (`ADMIN` only) |
| `GET` | `/api/v1/users/{userId}` | Get user by ID (`ADMIN` only) |
| `PATCH` | `/api/v1/users/{userId}` | Update user profile (`ADMIN` only) |
| `PATCH` | `/api/v1/users/{userId}/password` | Change password (`ADMIN` only) |
| `GET` | `/api/v1/vehicles` | Get vehicles (optional `?clientId=`) |
| `POST` | `/api/v1/vehicles` | Create vehicle |
| `PATCH` | `/api/v1/vehicles/{vehicleId}` | Update vehicle |
| `DELETE` | `/api/v1/vehicles/{vehicleId}` | Delete vehicle |

### Parking

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/parkings` | Create a parking facility |
| `GET` | `/api/v1/parkings` | Get all parkings |
| `GET` | `/api/v1/parkings/{parkingId}` | Get parking by ID |
| `PATCH` | `/api/v1/parkings/{parkingId}` | Update parking stats/rating |
| `POST` | `/api/v1/blueprints` | Create a blueprint (map) |
| `GET` | `/api/v1/blueprints` | Get all blueprints (optional `?parkingId=`) |
| `GET` | `/api/v1/blueprints/parking/{parkingId}` | Get blueprints by parking |
| `DELETE` | `/api/v1/blueprints/{blueprintId}` | Delete a blueprint |
| `POST` | `/api/v1/detectedSpots` | Create a detected spot |
| `GET` | `/api/v1/detectedSpots` | Get all spots (optional `?parkingId=` / `?blueprintId=`). Returns derived `status` plus `assignedEmployeeId` / `assignedEmployeeName` when a spot is reserved for an on-duty employee |
| `GET` | `/api/v1/detectedSpots/blueprint/{blueprintId}` | Get spots by blueprint with the same derived reservation metadata |
| `PATCH` | `/api/v1/detectedSpots/{spotId}/status?status=` | Update spot status |
| `POST` | `/api/v1/reservations` | Reserve a spot. Client reservations are rejected if the selected spot is reserved for an on-duty employee during the requested time range |
| `GET` | `/api/v1/reservations` | Get reservations (optional `?parkingId=` / `?clientId=`) |
| `PATCH` | `/api/v1/reservations/{reservationId}` | Update reservation status/details |
| `POST` | `/api/v1/clientReports` | Submit client report |
| `GET` | `/api/v1/clientReports` | Get all client reports |
| `PATCH` | `/api/v1/clientReports/{reportId}` | Update report status |

### Billing

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/clientPlans` | Get all subscription plans |
| `GET` | `/api/v1/clientPlans/{clientPlanId}` | Get plan by ID |
| `POST` | `/api/v1/receipts` | Create a receipt |
| `GET` | `/api/v1/receipts` | Get all receipts (optional `?bookingCode=`) |
| `GET` | `/api/v1/receipts/{receiptId}` | Get receipt by ID |
| `DELETE` | `/api/v1/receipts/{receiptId}` | Delete a receipt |
| `POST` | `/api/v1/subscriptions` | Create a subscription |
| `GET` | `/api/v1/subscriptions` | Get all subscriptions |
| `GET` | `/api/v1/subscriptions/{subscriptionId}` | Get subscription by ID |
| `PUT` | `/api/v1/subscriptions/{subscriptionId}` | Update a subscription |
| `PATCH` | `/api/v1/subscriptions/{subscriptionId}` | Patch a subscription |
| `PATCH` | `/api/v1/subscriptions/{subscriptionId}/savings` | Patch subscription savings |

### Monitoring

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/employees` | Create an employee, optionally with `assignedSpot` |
| `GET` | `/api/v1/employees` | Get all employees |
| `PUT` | `/api/v1/employees/{employeeId}` | Update an employee, including `assignedSpot`, shift, and `on-duty` / `off-duty` state |
| `DELETE` | `/api/v1/employees/{employeeId}` | Delete an employee |
| `GET` | `/api/v1/occupancyByHour` | Get occupancy by hour (optional `?parkingId=`) |
| `GET` | `/api/v1/weeklyTrends` | Get weekly occupancy trends (optional `?parkingId=`) |

## Prerequisites

- **Java 26** (or compatible JDK)
- **PostgreSQL** running on `localhost:5432`
- **Maven** (or use the bundled `mvnw` wrapper)

### Preloaded accounts

| Role | Email | Password |
|------|-------|----------|
| Client | `maria.garcia@gmail.com` | `Password123!` |
| Admin | `piero.quiroz@spotgo.com` | `Password123!` |

## Running Locally

```bash
# Run the app
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is available at `/swagger-ui/index.html`.
