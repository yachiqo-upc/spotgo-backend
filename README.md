# SpotGo Backend

REST API for SpotGo. Handles IAM with JWT, parking operations, monitoring, billing, profiles, vehicles, etc. Built with Spring Boot 4.0 and Java 26.

## Tech Stack

- **Java 26**
- **Spring Boot 4.0.7** — Web, Data JPA, Validation, Security, Mail, DevTools
- **PostgreSQL** — Primary database
- **JWT (jjwt 0.12.6)** — Access tokens backed by persisted, revocable sessions
- **Springdoc OpenAPI 3.0.3** — API documentation (Swagger UI)
- **Lombok** — Boilerplate reduction
- **DDD** — Domain-driven design with clear bounded contexts

## Architecture

The project follows **Domain-Driven Design** with a **hexagonal (ports & adapters)** structure, organized into these bounded contexts:

```
src/main/java/com/yachiqo/spotgo/
├── iam/            # Accounts, credentials, roles, sessions and audit
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

IAM implementa `accounts`, `credentials`, `role_assignments`, `sessions` y `audit_entries`. Sus PK y FK usan `UUID` en Java y en la base de datos. `profiles.account_id` apunta a `accounts.account_id` y es UNIQUE: una cuenta tiene cero o un perfil.

- `accounts` guarda correo y estado de acceso; los datos personales pertenecen a Profiles.
- `credentials` guarda la contraseña como hash BCrypt. El modelo admite PASSWORD y GOOGLE; el inicio de sesión con Google todavía requiere implementar la integración con el proveedor.
- `role_assignments` admite una sola asignación por cuenta, según `account_id UNIQUE` del diagrama. DRIVER y SUPER_ADMIN no tienen tenant; PARKING_ADMIN requiere tenant. `valid_to = NULL` significa sin fecha de término. El rol debe estar ACTIVE y dentro de sus fechas para otorgar permisos.
- `sessions` guarda el hash del refresh token, nunca el token original. Cada inicio de sesión crea una sesión; renovar rota el refresh token dentro de esa misma sesión. El token anterior deja de servir.
- `audit_entries` registra acciones de autenticación y administración, resultados, origen y fecha. No registra contraseñas ni tokens. `account_id` puede ser NULL cuando no se identifica una cuenta.

El registro público acepta solamente email y password y asigna DRIVER. El perfil se crea por separado mediante Profiles. Solo SUPER_ADMIN administra cuentas, asignaciones y auditoría. PARKING_ADMIN conserva el alcance de su tenant; tener ese rol no permite listar todas las cuentas.

El inicio de sesión devuelve `accessToken` (15 minutos por defecto) y `refreshToken` (sesión de 7 días por defecto). Envía `Authorization: Bearer <accessToken>` a los endpoints protegidos. Al vencer el access token, envía el refresh token a `POST /api/v1/authentication/refresh` y conserva ambos tokens nuevos. Renovar no extiende la fecha de vencimiento original de la sesión. Al cerrar sesión, revocarla o cambiar la contraseña, los access tokens asociados también dejan de funcionar porque el backend comprueba la sesión en cada petición.

Se conserva la tabla auxiliar existente `passwordResetCodes` para recuperación por correo, ahora con UUID. El envío requiere configurar Resend. Parking todavía utiliza su modelo anterior: por ahora el tenant de PARKING_ADMIN se valida contra el estacionamiento existente, hasta actualizar ese bounded context.

Errores: 400 datos inválidos; 401 credenciales o sesión inválidas; 403 rol o alcance insuficiente; 404 recurso inexistente; 409 duplicado o conflicto de integridad. Swagger documenta los códigos aplicables a cada operación.

## CORS Configuration

The backend allows requests from:

- `http://localhost:4200` — Angular dev server
- `https://spotgo-frontend.vercel.app` — Production frontend on Vercel

Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`. Credentials are enabled with a 1-hour max-age cache.

## API Endpoints

### IAM / Profiles

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/authentication/sign-up` | Crear cuenta DRIVER y sesión |
| POST | `/api/v1/authentication/sign-in` | Iniciar sesión con correo y contraseña |
| POST | `/api/v1/authentication/refresh` | Renovar tokens usando refreshToken |
| POST | `/api/v1/authentication/sign-out` | Revocar la sesión autenticada |
| POST | `/api/v1/authentication/password-reset/request` | Solicitar código de recuperación |
| POST | `/api/v1/authentication/password-reset/confirm` | Cambiar contraseña con código |
| GET | `/api/v1/accounts/me` | Consultar mi cuenta |
| PATCH | `/api/v1/accounts/me/password` | Cambiar mi contraseña y revocar mis sesiones |
| GET | `/api/v1/accounts/me/role` | Consultar mi asignación y su vigencia |
| GET | `/api/v1/accounts` | SUPER_ADMIN: listar cuentas con paginación |
| GET | `/api/v1/accounts/{accountId}` | SUPER_ADMIN: consultar cuenta |
| PATCH | `/api/v1/accounts/{accountId}/status` | SUPER_ADMIN: cambiar estado |
| GET / PUT | `/api/v1/accounts/{accountId}/role` | SUPER_ADMIN: consultar o asignar rol |
| GET | `/api/v1/sessions/me` | Listar mis sesiones sin mostrar hashes |
| DELETE | `/api/v1/sessions/{sessionId}` | Revocar una sesión propia |
| GET | `/api/v1/audit-entries` | SUPER_ADMIN: consultar auditoría paginada |
| POST | `/api/v1/profiles` | Crear mi perfil |
| GET / PATCH | `/api/v1/profiles/me` | Consultar o actualizar mi perfil |
| GET / POST | `/api/v1/vehicles` | Consultar o agregar vehículos propios |
| PATCH / DELETE | `/api/v1/vehicles/{vehicleId}` | Actualizar o desactivar vehículo propio |
| GET | `/api/v1/driver-classifications/me` | Consultar mis clasificaciones |
| GET | `/api/v1/driver-classifications/tenants/{tenantId}` | PARKING_ADMIN: consultar clasificaciones de su tenant |
| PUT | `/api/v1/driver-classifications/tenants/{tenantId}/profiles/{profileId}` | PARKING_ADMIN: asignar clasificación en su tenant |

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
| DRIVER | `juan.driver@example.com` | `Password123!` |
| DRIVER | `sofia.driver@example.com` | `Password123!` |
| PARKING_ADMIN | `ana.admin@example.com` | `Password123!` |
| SUPER_ADMIN | `super.admin@example.com` | `Password123!` |

## Running Locally

```bash
# Run the app
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is available at `/swagger-ui/index.html`.

### Probar IAM en Swagger

1. Configura `PASSWORD` y `JWT_SECRET` en el entorno local y deja activo `dev`. JWT_SECRET debe tener al menos 32 bytes. No publiques sus valores reales.
2. Con PostgreSQL y la base `spotgo` disponibles, inicia el backend. Con el seeder habilitado se insertan las cuentas demo anteriores sin sobrescribir filas existentes.
3. Abre http://localhost:8080/swagger-ui/index.html y selecciona **02-iam-actualizado**. **01-profiles-actualizado** reúne Profiles y autenticación; **03-backend-completo** muestra todos los contextos, incluidos los aún pendientes de actualizar.
4. Ejecuta sign-in con una cuenta demo. Copia solamente `accessToken` en **Authorize**.
5. Prueba `/accounts/me`, `/accounts/me/role` y `/sessions/me`. Con DRIVER, listar `/accounts` responde 403; con SUPER_ADMIN responde 200.
6. Para renovar, usa el `refreshToken` recibido en el cuerpo de `/authentication/refresh`. Actualiza el token de Authorize con el nuevo accessToken.

Los JWT de la implementación anterior ya no sirven: vuelve a iniciar sesión. Hibernate `update` no elimina tablas antiguas como `users` ni migra automáticamente sus cuentas a `accounts`. El seeder carga datos demo compatibles; no es una migración de usuarios reales.

En producción se usan las variables indicadas en `application-production.properties`, incluida `SPRING_DATASOURCE_PASSWORD`. Las cuentas demo son para desarrollo; el seeder actual está limitado a los perfiles locales habilitados.