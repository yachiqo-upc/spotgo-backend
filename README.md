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
├── parking/        # Tenants, zones, spots, floor plans, maps, reservations and stays
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

Se conserva la tabla auxiliar existente `passwordResetCodes` para recuperación por correo, ahora con UUID. El envío requiere configurar Resend. Parking ya utiliza tenants con UUID: las asignaciones de administradores y las clasificaciones de conductores tienen FK a tenants.

Errores: 400 datos inválidos; 401 credenciales o sesión inválidas; 403 rol o alcance insuficiente; 404 recurso inexistente; 409 duplicado o conflicto de integridad. Swagger documenta los códigos aplicables a cada operación.


### Clases Java y tablas

| Entidad Java | Tabla del modelo |
|---|---|
| `Account` | `accounts` |
| `Session` (dentro de IAM) | `sessions` |
| `Credential` | `credentials` |
| `RoleAssignment` | `role_assignments` |
| `AuditEntry` | `audit_entries` |
| `Profile`, `Vehicle`, `DriverClassification` | `profiles`, `vehicles`, `driver_classifications` |
| `Tenant`, `ParkingZone`, `ParkingSpot` | `tenants`, `parking_zones`, `parking_spots` |
| `FloorPlan`, `DigitalParkingMap` | `floor_plans`, `digital_parking_maps` |
| `Reservation`, `ParkingSession`, `GuestParkingSession` | `reservations`, `parking_sessions`, `guest_parking_sessions` |

`AuthenticationService` ejecuta registro, login, renovación y recuperación de contraseña. Los demás servicios, repositorios, controladores y excepciones son clases técnicas: no crean tablas por tener un archivo Java. `IamException` permite comunicar rechazos controlados y conservar la auditoría de intentos fallidos.

Se conserva `PasswordResetCode` y su tabla auxiliar anterior porque los endpoints de recuperación sí los utilizan. No se elimina funcionalidad activa para hacer coincidir el número de archivos con el número de tablas. El backend utiliza su autenticación de cuentas y sesiones; se excluye la creación automática de un usuario de seguridad en memoria de Spring Boot.

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
| GET / PUT | `/api/v1/profiles/me` | Consultar o actualizar mi perfil |
| GET / POST | `/api/v1/vehicles` | Consultar o agregar vehículos propios |
| PATCH / DELETE | `/api/v1/vehicles/{vehicleId}` | Actualizar o desactivar vehículo propio |
| GET | `/api/v1/driver-classifications/me` | Consultar mis clasificaciones |
| GET | `/api/v1/driver-classifications/tenants/{tenantId}` | PARKING_ADMIN: consultar clasificaciones de su tenant |
| PUT | `/api/v1/driver-classifications/tenants/{tenantId}/profiles/{profileId}` | PARKING_ADMIN: asignar clasificación en su tenant |

### Parking

Parking Infrastructure implementa las ocho tablas del README principal, con UUID nativos. Se retiraron las entidades y APIs antiguas `parkings`, `blueprints`, `detectedSpots` y `clientReports` del código de Parking. No se crea `parking_facilities` ni `parking_rules`.

| Tabla | Qué guarda |
|---|---|
| `tenants` | Un negocio con un estacionamiento físico, dirección, coordenadas, contacto y reglas JSONB. |
| `parking_zones` | Divisiones de ese tenant: pisos, sectores o áreas. |
| `parking_spots` | Espacios de una zona; código único dentro de la zona, categoría, características y estado administrativo. |
| `floor_plans` | Croquis por zona y versión; inicialmente DRAFT y published_at NULL. |
| `digital_parking_maps` | Elementos para renderizar el croquis, con versiones y una sola publicada por croquis. |
| `reservations` | Reserva del perfil autenticado para un vehículo propio y un espacio, intervalo, precio y bloqueo temporal. |
| `parking_sessions` | Entrada y salida efectivas de una reserva, con una sola sesión por reserva. |
| `guest_parking_sessions` | Estadía manual de un invitado sin cuenta; placa y pago físico CASH/POS. |

Las referencias internas y las de Profiles son FK físicas. IAM y driver_classifications también referencian tenants. `payment_id` es UUID nullable y UNIQUE, pendiente de una FK física cuando Billing implemente `digital_payments`. No se crea una tabla de pagos alternativa.

| Método | Ruta | Permiso / acción |
|---|---|---|
| GET | `/api/v1/tenants` y `/api/v1/tenants/{tenantId}` | Consulta autenticada de estacionamientos. |
| POST | `/api/v1/tenants` | SUPER_ADMIN registra un tenant. |
| PUT | `/api/v1/tenants/{tenantId}` | PARKING_ADMIN actualiza su tenant. |
| GET / POST | `/api/v1/parking-zones?tenantId=UUID` | Consultar zonas / administrador crea en su tenant. |
| PUT | `/api/v1/parking-zones/{zoneId}` | Administrador actualiza su zona. |
| GET / POST | `/api/v1/parking-spots?zoneId=UUID` | Consultar espacios / administrador crea en su zona. |
| PUT | `/api/v1/parking-spots/{spotId}` | Administrador actualiza su espacio. |
| GET | `/api/v1/parking-spots/availability?zoneId=UUID&startAt=ISO_INSTANT&endAt=ISO_INSTANT` | Disponibilidad calculada para un intervalo. |
| GET | `/api/v1/floor-plans?zoneId=UUID` | Administrador consulta croquis de su zona. |
| POST | `/api/v1/floor-plans` | Administrador registra croquis DRAFT. |
| POST | `/api/v1/floor-plans/{floorPlanId}/publish` o `/archive` | Publicar o archivar croquis. |
| GET | `/api/v1/digital-parking-maps?floorPlanId=UUID` | Administrador consulta mapas de su croquis. |
| POST | `/api/v1/digital-parking-maps` | Administrador registra mapa DRAFT. |
| POST | `/api/v1/digital-parking-maps/{mapId}/publish` o `/archive` | Publicar o archivar mapa. |
| GET | `/api/v1/reservations` y `/api/v1/reservations/{reservationId}` | DRIVER: propias; PARKING_ADMIN: su tenant. |
| POST | `/api/v1/reservations` | DRIVER reserva para un vehículo propio activo. |
| POST | `/api/v1/reservations/{reservationId}/confirm-payment` | Verifica el pago principal y confirma el bloqueo vigente. |
| POST | `/api/v1/reservations/{reservationId}/cancel` | Cancelar una reserva pendiente o confirmada autorizada. |
| POST | `/api/v1/reservations/{reservationId}/no-show` | Administrador registra ausencia después del intervalo reservado. |
| GET | `/api/v1/parking-sessions` | DRIVER: propias; administrador: su tenant. |
| POST | `/api/v1/parking-sessions` | Administrador registra entrada con reservationId. |
| POST | `/api/v1/parking-sessions/{sessionId}/exit` | Administrador registra salida. |
| GET / POST | `/api/v1/guest-parking-sessions` | Administrador lista invitados o registra entrada manual. |
| POST | `/api/v1/guest-parking-sessions/{guestSessionId}/exit` | Calcula importe y registra salida/pago físico. |
| POST | `/api/v1/guest-parking-sessions/{guestSessionId}/confirm-payment` | Confirma el pago físico de una estadía ya terminada. |

#### Reglas implementadas y decisiones de contrato

- Tenant: ACTIVE/SUSPENDED. Zona: ACTIVE/INACTIVE. Espacio: ACTIVE/INACTIVE/MAINTENANCE. Los estados propuestos del README quedan explícitos en estos enums.
- `spot_type` sigue siendo texto, por ejemplo STANDARD; no se inventa un catálogo cerrado pendiente de acordar. `features` y `location` son objetos JSON. Coordenadas requieren latitude y longitude numéricas dentro de sus rangos geográficos. El color utiliza #RRGGBB.
- Las reglas requieren `tariff_rules.period_minutes` entero positivo y `price_per_period` no negativo. El redondeo implementado es CEILING: se cobran periodos completos o fracción, en soles. `allocation_rules` solo admite {} hasta definir sus criterios; `coexistence_rules` admite una lista de textos.
- Se calcula el importe en el servidor: el conductor no envía amount, profileId, tenantId ni status para crear una reserva. El tenant y la zona se obtienen del espacio; el perfil se obtiene de la cuenta autenticada.
- Se rechazan solapamientos del espacio o del mismo vehículo; intervalos adyacentes sí se permiten. Los bloqueos de filas serializan solicitudes concurrentes sobre el vehículo y el espacio.
- La reserva empieza PENDING_PAYMENT, payment_id NULL y lock_status ACTIVE. La duración del bloqueo usa la regla del tenant; por defecto 10 minutos. Una tarea cada 30 segundos marca los bloqueos vencidos CANCELLED/EXPIRED; un bloqueo vencido ya no impide reservar aunque todavía no se haya ejecutado esa tarea.
- Confirmar exige un pago APPROVED de operación RESERVATION, mismo perfil y mismo importe, además del bloqueo vigente. Si Billing aún no tiene digital_payments, responde 503; no se simula un pago exitoso.
- Registrar entrada exige reserva RESERVED y estar dentro de su intervalo. No permite dos sesiones abiertas para el mismo vehículo o espacio. Una reserva genera como máximo una sesión, respaldada por UNIQUE(reservation_id).
- La salida captura exit_at automáticamente. Si supera el fin más la tolerancia, la reserva queda OVERSTAYED. La generación de additional_charges corresponde a Billing y todavía está pendiente.
- Invitado: no crea cuenta, perfil, vehículo ni pago digital. La salida calcula su importe; queda CLOSED si el administrador confirma pago físico o PENDING_REVIEW si no. Puede confirmar ese pago posteriormente.
- Croquis y mapas: versión positiva y única dentro de su zona/croquis. Publicar un mapa exige croquis publicado y archiva los otros mapas del mismo croquis. El archivo debe existir previamente en su almacenamiento: este cambio no implementa carga de archivos ni conversión automática de croquis.
- Disponibilidad es una proyección, sin tabla adicional. Combina habilitación administrativa, reservas y estadías abiertas. `physicalOccupancy = UNKNOWN` hasta integrar lecturas de Monitoring; no equipara reserva con ocupación física.

#### Probar Parking en Swagger

Usa http://localhost:8080/swagger-ui/index.html. La vista predeterminada es **04-backend-completo**; **03-parking-actualizado** reúne este contexto y autenticación. Las secciones usan los nombres de los recursos, sin prefijo del bounded context.

1. Inicia sesión con `ana.admin@example.com` / `Password123!` y pega accessToken en Authorize. Consulta su tenantId con `/accounts/me/role` en la vista completa.
2. Consulta las zonas y espacios demo. Puedes registrar/actualizar recursos únicamente dentro de ese tenant.
3. Inicia sesión con `juan.driver@example.com`, reemplaza el token en Authorize y consulta `/vehicles`. Usa un vehicleId propio y un spotId para POST /reservations con fechas futuras ISO-8601, por ejemplo terminadas en Z.
4. La respuesta incluye el importe, perfil, tenant, zona y vencimiento del bloqueo. Intentar reservar el mismo intervalo ocupado responde 409; usar un vehículo ajeno responde 403.
5. Confirmar el pago depende del nuevo Billing. Mientras no esté implementado devuelve 503. Las pruebas automatizadas usan pagos de prueba en H2 para verificar también entrada y salida; no se añade esa simulación al backend de desarrollo o producción.
6. Con la cuenta de Ana puedes probar invitados directamente, sin pago digital: entrada, salida y confirmación de pago físico.

El seeder ahora carga tenants, zonas, espacios, croquis, mapas e invitados en sus tablas reales. Los registros demo de reservas que referencian pagos digitales ausentes se omiten, al igual que las sesiones que dependen de esas reservas. No se inventan pagos para insertarlos.

**Integraciones pendientes de otros contextos:** descuentos de membresías Driver y bloqueo por deudas requieren adaptar Billing; lecturas físicas y conflictos requieren adaptar Monitoring. Los endpoints de esos contextos conservan sus modelos anteriores. Sus consultas compartidas se ajustaron al nuevo Parking sin crear tablas antiguas de Parking.

**Base de datos existente:** Hibernate update no migra automáticamente IDs antiguos ni elimina tablas. En particular, la antigua tabla reservations tiene columnas y clave primaria incompatibles con este modelo. Antes de usar este cambio con esa BD hay que migrar esa tabla o recrear una BD local de pruebas. Este cambio no ejecuta DROP ni migra datos de tu PostgreSQL.

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
3. Abre http://localhost:8080/swagger-ui/index.html y selecciona **02-iam-actualizado**. **01-profiles-actualizado** reúne Profiles y autenticación; **04-backend-completo** muestra todos los contextos, incluidos los aún pendientes de actualizar.
4. Ejecuta sign-in con una cuenta demo. Copia solamente `accessToken` en **Authorize**.
5. Prueba `/accounts/me`, `/accounts/me/role` y `/sessions/me`. Con DRIVER, listar `/accounts` responde 403; con SUPER_ADMIN responde 200.
6. Para renovar, usa el `refreshToken` recibido en el cuerpo de `/authentication/refresh`. Actualiza el token de Authorize con el nuevo accessToken.

Los JWT de la implementación anterior ya no sirven: vuelve a iniciar sesión. Hibernate `update` no elimina tablas antiguas como `users` ni migra automáticamente sus cuentas a `accounts`. El seeder carga datos demo compatibles; no es una migración de usuarios reales.

En producción se usan las variables indicadas en `application-production.properties`, incluida `SPRING_DATASOURCE_PASSWORD`. Las cuentas demo son para desarrollo; el seeder actual está limitado a los perfiles locales habilitados.