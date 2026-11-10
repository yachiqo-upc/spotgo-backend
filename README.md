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
├── monitoring/     # Sensors, readings, health, conflicts, alerts, snapshots and reports
├── billing/        # Payments, tokens, attempts, subscriptions, charges, debts, refunds and documents
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

Parking Infrastructure implementa las ocho tablas del README DIAGRAM.md, con UUID nativos. Se retiraron las entidades y APIs antiguas `parkings`, `blueprints`, `detectedSpots` y `clientReports` del código de Parking. No se crea `parking_facilities` ni `parking_rules`.

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

Las referencias internas y las de Profiles son FK físicas. IAM y driver_classifications también referencian tenants. `reservations.payment_id` es UUID nullable y UNIQUE, con FK física a `digital_payments.payment_id`. Es NULL mientras no se haya creado la operación de pago. No se crea una tabla de pagos alternativa.

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
- Confirmar exige un pago APPROVED de operación RESERVATION, mismo perfil y mismo importe, además del bloqueo vigente. El proveedor financiero no configurado responde 503 al procesar el cobro; no se simula un pago exitoso.
- Registrar entrada exige reserva RESERVED y estar dentro de su intervalo. No permite dos sesiones abiertas para el mismo vehículo o espacio. Una reserva genera como máximo una sesión, respaldada por UNIQUE(reservation_id).
- La salida captura exit_at automáticamente. Si supera el fin más la tolerancia, la reserva queda OVERSTAYED. Billing genera el cargo adicional de sobretiempo y su deuda al finalizar la estadía.
- Invitado: no crea cuenta, perfil, vehículo ni pago digital. La salida calcula su importe; queda CLOSED si el administrador confirma pago físico o PENDING_REVIEW si no. Puede confirmar ese pago posteriormente.
- Croquis y mapas: versión positiva y única dentro de su zona/croquis. Publicar un mapa exige croquis publicado y archiva los otros mapas del mismo croquis. El archivo debe existir previamente en su almacenamiento: este cambio no implementa carga de archivos ni conversión automática de croquis.
- Disponibilidad es una proyección, sin tabla adicional. Combina habilitación administrativa, reservas y estadías abiertas. physicalOccupancy proviene de Monitoring (AVAILABLE, OCCUPIED o UNAVAILABLE). Una reserva inmediata exige lectura confiable libre; para una reserva futura se valida el intervalo sin prometer su estado físico futuro.

#### Probar Parking en Swagger

Usa http://localhost:8080/swagger-ui/index.html. La vista predeterminada es **04-backend-completo**; **03-parking-actualizado** reúne este contexto y autenticación. Las secciones usan los nombres de los recursos, sin prefijo del bounded context.

1. Inicia sesión con `ana.admin@example.com` / `Password123!` y pega accessToken en Authorize. Consulta su tenantId con `/accounts/me/role` en la vista completa.
2. Consulta las zonas y espacios demo. Puedes registrar/actualizar recursos únicamente dentro de ese tenant.
3. Inicia sesión con `juan.driver@example.com`, reemplaza el token en Authorize y consulta `/vehicles`. Usa un vehicleId propio y un spotId para POST /reservations con fechas futuras ISO-8601, por ejemplo terminadas en Z.
4. La respuesta incluye el importe, perfil, tenant, zona y vencimiento del bloqueo. Intentar reservar el mismo intervalo ocupado responde 409; usar un vehículo ajeno responde 403.
5. Para pagar, registra un payment_token y crea la operación mediante POST /digital-payments/reservations/{reservationId}. Al procesarse y aprobarse se confirma automáticamente la reserva. Si el proveedor no está configurado, el procesamiento responde 503; no se aprueba un cobro ficticio.
6. Con la cuenta de Ana puedes probar invitados directamente, sin pago digital: entrada, salida y confirmación de pago físico.

El seeder ahora carga tenants, zonas, espacios, croquis, mapas e invitados en sus tablas reales. Con Billing implementado también carga los pagos demo y las reservas, sesiones, cargos y deudas que los referencian. Son registros ficticios de desarrollo; el seeder no realiza cobros.

**Integración actual:** Billing verifica pagos, aplica descuentos de membresías vigentes, bloquea nuevas reservas con deudas y registra sobretiempo al finalizar una estadía. Monitoring integra lecturas, salud, conflictos y avisos con Parking, respetando el límite de sensores de la suscripción empresarial. Los cinco contextos utilizan el modelo actualizado.

**Base de datos existente:** Hibernate update no migra automáticamente IDs antiguos ni elimina tablas. En particular, la antigua tabla reservations tiene columnas y clave primaria incompatibles con este modelo. Antes de usar este cambio con esa BD hay que migrar esa tabla o recrear una BD local de pruebas. No se ejecutó una migración ni un arranque contra tu PostgreSQL durante estos cambios. El próximo arranque con dev sí borrará y recreará las tablas mapeadas; úsalo únicamente con una BD local descartable.

### Payments & Billing

Este contexto implementa `payment_tokens`, `digital_payments`, `payment_attempts`, `subscriptions`, `additional_charges`, `outstanding_balances`, `refunds` y `billing_documents`. Las entidades Java utilizan esos mismos nombres en singular y las PK/FK son UUID nativos. Se retiraron ClientPlan, Receipt y sus controladores antiguos.

| Tabla | Qué hace |
|---|---|
| payment_tokens | Guarda la referencia tokenizada y datos resumidos verificados por el proveedor. No almacena PAN completo ni CVV. |
| digital_payments | Conserva la operación, importe, resultado, perfil pagador, referencia del proveedor y clave idempotente. |
| payment_attempts | Historial numerado de intentos y resultados; no se crea manualmente desde el cliente. |
| subscriptions | Un periodo por fila y un pago en payment_id; titular Driver o Tenant, nunca ambos. |
| additional_charges | Varios cargos posibles por reserva; cada cargo conserva su operación de pago independiente. |
| outstanding_balances | Una deuda como máximo por cargo impago; un perfil puede acumular varias. |
| refunds | Solicitudes y resultados de devolución sobre el pago original, sin superar el saldo elegible. |
| billing_documents | Comprobante principal de un pago aprobado, único por pago y por serie/número. |

#### Endpoints actuales

| Método | Ruta | Uso |
|---|---|---|
| GET / POST | `/api/v1/payment-tokens` | Consultar mis medios o registrar una referencia que verifica el proveedor. |
| DELETE | `/api/v1/payment-tokens/{tokenId}` | Revocar un medio propio, conservando historial. |
| GET | `/api/v1/digital-payments` y `/{paymentId}` | Consultar pagos dentro del alcance autorizado. |
| POST | `/api/v1/digital-payments/reservations/{reservationId}` | Crear la operación de la reserva con tokenId e idempotencyKey. El importe procede de la reserva. |
| POST | `/api/v1/digital-payments/subscriptions` | Crear una operación para planCode con tokenId e idempotencyKey; aún no crea la suscripción. El precio procede del catálogo del servidor. |
| POST | `/api/v1/digital-payments/additional-charges/{chargeId}` | Crear el pago separado de un cargo propio. |
| POST | `/api/v1/digital-payments/{paymentId}/process` | Procesar la operación; el cliente no elige status ni approvedAt. |
| POST | `/api/v1/digital-payments/{paymentId}/retry` | Nuevo intento de la misma operación tras fallo conocido y tiempo de espera. |
| POST | `/api/v1/digital-payments/{paymentId}/reconcile` | Consultar una operación pendiente/desconocida sin generar otro débito. |
| GET | `/api/v1/payment-attempts?paymentId=UUID` | Ver intentos de un pago autorizado. |
| GET | `/api/v1/subscriptions` | Consultar periodos ya pagados; se crean automáticamente al aprobar su pago. |
| GET | `/api/v1/subscriptions/plans` | Ver el catálogo configurado por el servidor. |
| GET | `/api/v1/subscriptions/{subscriptionId}` | Consultar una contratación autorizada. |
| POST | `/api/v1/subscriptions/{subscriptionId}/cancel` | Cerrar la fila y capturar endAt. |
| GET / POST | `/api/v1/additional-charges` | Consultar cargos; PARKING_ADMIN registra un cargo justificado en su tenant. |
| POST | `/api/v1/additional-charges/{chargeId}/mark-unpaid` | Registrar una deuda si el cargo está impago. No trata un resultado desconocido como rechazo. |
| GET | `/api/v1/outstanding-balances` y `/total` | Consultar deudas y suma pendiente sin duplicar los importes de cargos. |
| GET / POST | `/api/v1/refunds` | Consultar por paymentId o solicitar un reembolso autorizado. |
| POST | `/api/v1/refunds/{refundId}/process` | Solicitar la devolución al proveedor con refundId idempotente. |
| GET / POST | `/api/v1/billing-documents` | Consultar por paymentId o registrar un comprobante del emisor autorizado. |
| POST | `/api/v1/billing-documents/{documentId}/void` | Registrar su anulación local. |

#### Flujos integrados

1. DRIVER crea su reserva en Parking. Se calcula tarifa en PEN y descuento de su membresía vigente; una deuda OPEN impide nuevas reservas.
2. Crea el pago de esa reserva usando su token y una clave idempotente. El backend asocia reservations.payment_id y crea el primer intento. Todavía no está pagado.
3. Procesa el pago. APPROVED confirma la reserva si el bloqueo sigue vigente. Una aprobación tardía conserva el pago y solicita refund, sin apropiarse de un espacio cuyo bloqueo venció.
4. El administrador registra entrada y salida. Si hay sobretiempo más allá de la tolerancia, se crea un cargo OVERSTAY asociado a la reserva y su deuda impaga. No se modifica el pago inicial ni se cobra automáticamente una tarjeta sin intervención del pagador.
5. DRIVER crea/procesa el pago del cargo. Al confirmarse se actualizan coherentemente charge PAID y balance REGULARIZED, amount 0 y regularizedAt. La fila de deuda se conserva.
6. Cancelar una reserva pagada solicita devolver el saldo elegible; no devuelve dos veces el importe. Procesar el reembolso requiere el proveedor. Las solicitudes repetidas conservan la misma operación.

Una deuda no deshabilita la cuenta ni impide iniciar sesión, consultar deudas o pagarlas. El bloqueo se limita a crear nuevas reservas. Reservas que ya existían pueden originar más cargos y deudas.

#### Suscripciones y catálogo

Se respeta la última decisión: subscriptions.payment_id, un pago por fila. No existe digital_payments.subscription_id. Antes de aprobarse solo existe digital_payments. Al confirmarse APPROVED se crea subscriptions ACTIVE con payment_id NOT NULL y UNIQUE, startAt de aprobación y endAt NULL. La duración se calcula con plan.billing_period_months. billing_period_months=1 significa un mes calendario (no un valor implícito). Al cancelar se captura el cierre y al vencer se completa endAt con el límite calculado; otro periodo utiliza otra fila. Los reintentos no crean otra contratación ni otro pago.

El cliente solo envía planCode: no puede establecer su descuento o precio. `BILLING_PLANS_JSON` configura el catálogo del servidor. En dev/local, si no se configura uno, se toman los dos planes ficticios de db.json: DRIVER_DEMO y TENANT_DEMO. Sus precios de S/ 10 y S/ 50 son datos demo, no seis tarifas comerciales acordadas. En producción no se toma el catálogo demo automáticamente: hay que configurar el catálogo definitivo.

Ejemplo de configuración de catálogo, con importes ficticios:

```json
[
  {"code":"DRIVER_DEMO","audience":"DRIVER","price":10,"billing_period_months":1,"discount_percent":10},
  {"code":"TENANT_DEMO","audience":"PARKING_ADMIN","price":50,"billing_period_months":1,"included_sensors":20}
]
```

Las contrataciones empresariales pertenecen al Tenant; el pago identifica además el perfil administrador pagador. Cambiar de administrador no cambia al titular de la empresa. La cantidad física y asignación de sensores corresponde a Monitoring.

#### Idempotencia, fallos y configuración

- Misma clave y mismos datos: misma operación. Misma clave con otro contenido: 409. La clave es UNIQUE y el contenido se verifica con SHA-256.
- Cada cobro está vinculado a su reserva, suscripción o cargo antes de procesarse. No permite usar un pago ajeno o de otra finalidad aunque coincidan importes.
- Un pago APPROVED no se vuelve a cobrar. PENDING o RECONCILIATION_REQUIRED se concilian; no se reintentan a ciegas.
- Rechazo conocido: puede reintentarse sobre el mismo paymentId. Se agrega payment_attempt y se respeta app.billing.retry-delay-seconds, inicialmente 60.
- La suma de reembolsos completados y comprometidos no puede exceder el pago original. El proveedor debe garantizar idempotencia por paymentId/attemptNumber y por refundId.
- La fecha límite de una deuda usa app.billing.debt-due-hours, inicialmente 24 horas. No se añade una penalidad por falta de fondos.
- El sobretiempo utiliza app.billing.overstay-charge-basis: AFTER_TOLERANCE (valor inicial: cobra el tiempo posterior a la tolerancia) o TOTAL_EXCESS (cobra todo el exceso cuando supera la tolerancia). El descuento de membresía se aplica al precio base de la reserva; no se añade a cargos extraordinarios.

#### Proveedor y comprobantes

No hay proveedor financiero real configurado en este repositorio. La implementación predeterminada responde 503 al tokenizar, cobrar, conciliar o devolver dinero. Nunca acepta que el cliente marque un pago como aprobado. Las pruebas H2 sustituyen el puerto PaymentProvider por un proveedor controlado únicamente dentro de las pruebas.

Registrar datos de una boleta/factura no equivale a enviarla a SUNAT ni a recibir autorización tributaria. El emisor autorizado debe proporcionar serie, número y datos. Esta integración externa no se simula como si fuese real.

Swagger en http://localhost:8080/swagger-ui/index.html muestra los cinco contextos en 04-backend-completo. Monitoring también está en monitoring-actualizado. Se retiraron los endpoints antiguos de empleados, occupancyByHour, weeklyTrends y analytics, además de Receipts y ClientPlans.

**BD existente:** subscriptions y reservations del backend anterior pueden ser incompatibles con este esquema. Hibernate update no convierte IDs ni borra tablas antiguas. No se ejecutó ninguna migración ni borrado en tu PostgreSQL durante la configuración. En el próximo arranque dev se recrearán las tablas mapeadas.

### Monitoring

Implementa occupancy_sensors, occupancy_readings, sensor_health_events, occupancy_conflicts, occupancy_alerts, capacity_snapshots y occupancy_reports. Las PK/FK son UUID nativos. No agrega columnas al diagrama ni una tabla de snapshots por reporte. Se retiraron Employee, OccupancyByHour, WeeklyTrend y sus servicios/controladores; no se borró ninguna tabla de tu PostgreSQL.

| Método | Ruta | Función |
| --- | --- | --- |
| GET / POST | `/api/v1/occupancy-sensors` | Listar o registrar un sensor con primera lectura real. |
| GET | `/api/v1/occupancy-sensors/{sensorId}` | Consultar dispositivo autorizado. |
| POST | `/api/v1/occupancy-sensors/{sensorId}/deactivate` | Desactivar conservando historial; permite reemplazarlo. |
| GET | `/api/v1/occupancy-sensors/spots/{spotId}/occupancy` | Consultar última ocupación física confiable. |
| GET / POST | `/api/v1/occupancy-readings` | Historial por sensorId o recepción autenticada de lectura. |
| GET / POST | `/api/v1/sensor-health-events` | Historial por sensorId o diagnóstico de salud. |
| GET | `/api/v1/sensor-health-events/{healthEventId}` | Consultar episodio concreto. |
| POST | `/api/v1/sensor-health-events/{healthEventId}/recover` | Registrar recuperación del episodio. |
| GET | `/api/v1/occupancy-conflicts` | Listar incidentes del Tenant; SUPER_ADMIN puede consultar todos. |
| GET | `/api/v1/occupancy-conflicts/{conflictId}` | Consultar incidente y su lectura de respaldo. |
| POST | `/api/v1/occupancy-conflicts/{conflictId}/acknowledge` | Reconocer incidente. |
| POST | `/api/v1/occupancy-conflicts/{conflictId}/resolve` | Resolver incidente y cerrar sus avisos. |
| GET | `/api/v1/occupancy-alerts` | Consultar avisos del Tenant. |
| POST | `/api/v1/occupancy-alerts/{alertId}/acknowledge` | Reconocer aviso. |
| PUT | `/api/v1/occupancy-alerts/{alertId}/evidence` | Subir archivo JPEG/PNG en image (multipart/form-data). evidence_ref es NULL hasta la subida. |
| GET | `/api/v1/occupancy-alerts/{alertId}/evidence` | Obtener la foto actual usando solo alertId; 404 si no tiene evidencia. |
| POST | `/api/v1/occupancy-alerts/{alertId}/close` | Cerrar aviso de incidente resuelto o recuperado. |
| GET / POST | `/api/v1/capacity-snapshots` | Consultar o calcular resumen confiable de zona o Tenant. |
| GET / POST | `/api/v1/occupancy-reports` | Consultar o generar reporte de zona en [inicio,fin). |

Los listados admiten tenantId opcional: PARKING_ADMIN solo puede usar el suyo; SUPER_ADMIN puede consultar todos. Lecturas y eventos de salud requieren sensorId para consultar historial. DRIVER no administra sensores, diagnósticos ni alertas; consulta disponibilidad mediante Parking. Modificar Monitoring exige PARKING_ADMIN del Tenant.

Registro de sensor: enviar spotId, protocol, configuration, installedAt y firstReading con occupancyStatus, occurredAt y quality. El servidor deriva tenant_id y zone_id desde el spot, captura last_seen_at/received_at y calcula payload_hash. No se registra un primer contacto ficticio. MQTT exige configuration.topic; la ingesta es HTTP autenticada y un broker externo puede adaptarla a esa ruta. No hay broker MQTT ni claves de dispositivo inventados.

La suscripción empresarial pagada y vigente define plan.included_sensors. Se cuenta solo sensores ACTIVE y se impide registrar dos activos para el mismo espacio. Sin licencia vigente, la ocupación consultada pasa a UNAVAILABLE; no se inventa una lectura libre ni se pierde el historial físico.

Política configurable: quality 0..100 con dos decimales, mínimo 80 y frescura de 300 segundos. Configurar MONITORING_MINIMUM_QUALITY y MONITORING_FRESHNESS_SECONDS si el hardware requiere otros valores. La tarea de salud se ejecuta cada 60 segundos. Una falta de comunicación genera un episodio de salud y su aviso; no genera conflicto. Un mensaje repetido no crea otra lectura ni mantiene vivo un sensor; los mensajes tardíos no reemplazan los recientes.

Conflictos automáticos: UNEXPECTED_OCCUPANCY sin operación válida, SPACE_NOT_AVAILABLE si afecta una reserva confirmada sin estadía registrada, EXPECTED_OCCUPANCY_MISSING si una estadía válida aparece libre y OVERSTAY si sigue ocupada después del fin reservado y su tolerancia. Una estadía válida de invitado no se confunde con ocupación indebida. El incidente no identifica por sí solo al conductor que físicamente ocupó el espacio ni genera una multa automática. Billing calcula sobretiempo al cerrar la estadía.

Las alertas usan exactamente uno de health_event_id o conflict_id; la BD exige esa exclusión. evidence_ref guarda una referencia interna a la foto subida al backend y admite NULL hasta adjuntarla. La foto se almacena como archivo privado, no en el JSON del episodio. Reconocer un aviso captura acknowledged_at. Cerrar un aviso exige que el origen esté resuelto o recuperado y captura closed_at.

Los snapshots cuentan espacios operativos con última lectura confiable. Si alguno es UNAVAILABLE, la petición responde 409 y no guarda porcentajes parciales. Con cero espacios guarda 0.00 sin dividir por cero. Se intenta capturar zona y Tenant al recibir lecturas confiables. Los reportes requieren zona e intervalo [inicio,fin), como máximo 366 días, sin futuro, y al menos una muestra. totals guarda máximo, media simple de muestras, cantidad de snapshots y alertas creadas; no es una media ponderada por tiempo.

Prueba manual: iniciar sesión como Parking Admin, obtener accessToken y autorizar Swagger; tener un periodo empresarial pagado; registrar sensores con lecturas actuales; enviar una lectura distinta con occurredAt posterior; consultar conflictos y alertas; reconocerlos, enviar lectura recuperada o registrar recuperación de salud; calcular snapshot cuando todos los espacios estén cubiertos y luego generar reporte. Los datos demo fechados conservan historia: no se interpretan como lectura actual cuando ya vencieron.

El procesamiento financiero real sigue requiriendo PaymentProvider. Las pruebas automatizadas usan un proveedor controlado exclusivamente en H2; el backend no aprueba cobros reales sin una pasarela configurada.

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
# dev resets mapped tables and loads db.json on each startup
./mvnw spring-boot:run
```

Se utilizan solo application.properties (base/local) y application-production.properties (sobrescrituras del servidor). El perfil predeterminado es dev; en el servidor definir SPRING_PROFILES_ACTIVE=production. ddl-auto=create reinicia las tablas mapeadas al arrancar; no se usa create-drop. Los archivos de fotos en storage/evidence no se borran automáticamente, aunque se pierdan sus referencias al reiniciar la BD local. Producción mantiene su configuración independiente, sin cambios en esta actualización.

The API starts on `http://localhost:8080`. Swagger UI is available at `/swagger-ui/index.html`.

### Probar IAM en Swagger

1. Configura `PASSWORD` y `JWT_SECRET` en el entorno local y deja activo `dev`. JWT_SECRET debe tener al menos 32 bytes. No publiques sus valores reales.
2. Con PostgreSQL y la base `spotgo` disponibles, inicia el backend. La configuracion local de application.properties recrea las tablas mapeadas en cada arranque (ddl-auto=create) y después carga db.json. Se pierden los cambios locales de la ejecución anterior. Esto también ocurre con los reinicios de DevTools.
3. Abre http://localhost:8080/swagger-ui/index.html y selecciona **02-iam-actualizado**. **01-profiles-actualizado** reúne Profiles y autenticación; **04-backend-completo** muestra todos los contextos, todos ya adaptados al modelo actual.
4. Ejecuta sign-in con una cuenta demo. Copia solamente `accessToken` en **Authorize**.
5. Prueba `/accounts/me`, `/accounts/me/role` y `/sessions/me`. Con DRIVER, listar `/accounts` responde 403; con SUPER_ADMIN responde 200.
6. Para renovar, usa el `refreshToken` recibido en el cuerpo de `/authentication/refresh`. Actualiza el token de Authorize con el nuevo accessToken.

Los JWT de la implementación anterior ya no sirven: vuelve a iniciar sesión. Hibernate `update` no elimina tablas antiguas como `users` ni migra automáticamente sus cuentas a `accounts`. El seeder carga datos demo compatibles; no es una migración de usuarios reales. En dev se recrean primero las tablas mapeadas; no conectes ese perfil a una BD que quieras conservar.

En producción se usan las variables indicadas en `application-production.properties`, incluida `SPRING_DATASOURCE_PASSWORD`. Las cuentas demo son para desarrollo; el seeder actual está limitado a los perfiles locales habilitados.
El pago de un plan se solicita con POST /api/v1/digital-payments/subscriptions (planCode, tokenId, idempotencyKey) y se procesa por su paymentId. No existe POST /api/v1/subscriptions para crear filas sin pago. result_reference conserva temporalmente el plan seleccionado y el titular (máximo 8192 caracteres), y después identifica la suscripción creada. No se añade subscription_id a digital_payments.

Estado verificado del seeder: las 31 tablas de db.json están implementadas, con 79 registros y 123 referencias no nulas sin referencias rotas. El seeder inserta filas compatibles que no existan; no actualiza ni borra registros. En dev es Hibernate quien recrea primero las tablas mapeadas; después el seeder carga los datos iniciales. No se ejecutó este reinicio contra tu PostgreSQL durante la configuración.

Producción: ddl-auto=update conserva las tablas existentes y app.seeder.enabled=false desactiva los datos demo. No convierte esquemas antiguos incompatibles ni elimina tablas retiradas. Activar el perfil production mediante SPRING_PROFILES_ACTIVE=production.

Validación de esta adaptación: 155 pruebas sin fallos en H2, incluidas 34 de integración de Monitoring y 4 de almacenamiento de imágenes, permisos HTTP, deduplicación, concurrencia, salud/alertas, snapshots/reportes y el flujo entre los cinco contextos. Se verificaron las columnas y tipos UUID de las 31 tablas del JSON. Ejecutado con JDK 21 mediante override local de Maven, sin cambiar la versión Java 26 del pom. No se ejecutó contra PostgreSQL ni contra hardware o proveedores financieros reales.

Evidencia de alertas: PUT /api/v1/occupancy-alerts/{alertId}/evidence recibe multipart/form-data con campo image (archivo), no un JSON con evidenceRef. JPEG o PNG real, máximo 5 MB y 20 millones de píxeles. El servidor guarda la foto y genera la referencia; no consulta ninguna API externa. GET /api/v1/occupancy-alerts/{alertId}/evidence devuelve los bytes de la foto y exige JWT del administrador del Tenant o SUPER_ADMIN.

Si occupancy_alerts ya existe en PostgreSQL con evidence_ref NOT NULL, el cambio requiere permitir NULL en esa columna. No se ejecutó este SQL contra tu BD:

```sql
ALTER TABLE occupancy_alerts ALTER COLUMN evidence_ref DROP NOT NULL;
-- Retirar solo las rutas JSON generadas por la implementación anterior, conservando URLs de fotos.
UPDATE occupancy_alerts
SET evidence_ref = NULL
WHERE evidence_ref LIKE '/api/v1/occupancy-conflicts/%'
   OR evidence_ref LIKE '/api/v1/sensor-health-events/%';
```

En dev, los cambios de db.json se cargan en el siguiente arranque porque Hibernate recrea las tablas antes de ejecutar el seeder. En otros perfiles, ejecutar solo el seeder no sobrescribe registros existentes. Las alertas demo tienen evidence_ref NULL: no se inventan fotos.

Subir una foto desde Swagger: iniciar sesión y Authorize; abrir PUT /api/v1/occupancy-alerts/{alertId}/evidence; pulsar Try it out; indicar alertId; seleccionar el archivo en image; ejecutar. Consultar GET /api/v1/occupancy-alerts/{alertId}/evidence para ver o descargar la foto. Una petición JSON antigua responde 415. Archivo vacío responde 400; formato no admitido 415; tamaño excesivo 413; almacenamiento no disponible 503. No se inventa una referencia si falla la subida.

Los bytes se guardan en storage/evidence (ignorado por Git); app.monitoring.evidence-directory se configura con EVIDENCE_STORAGE_DIRECTORY. En producción debe apuntar a un disco persistente que conserve archivos entre despliegues. No usar almacenamiento temporal del contenedor para fotos que deban conservarse. No se han subido fotos ni configurado un disco en tu servidor de producción desde esta sesión.

La consulta de la foto solo pide alertId. El backend busca evidence_ref en esa alerta y devuelve el archivo actual; no se introduce fileName. evidence_ref conserva la referencia interna del archivo para localizarlo y reemplazarlo, no es una URL que el usuario tenga que escribir. Si todavía no hay foto, la consulta responde 404.
