# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

NexHome is a multi-client home-services marketplace in early development. Each client lives in its own self-contained sub-project with its own build tooling; there is no root-level build.

- `NexHome_App_Structure.pdf`: the target database and application design (lookups plus 9 modules, ~58 tables). The backend is being restructured to it module by module. **Done: lookups and Module 1 (identity, OTP auth, access control).** Next: Module 2 (profiles, addresses, partner onboarding).
- `backend/nexhome/`: Spring Boot 4.1 REST API (Java 21, Maven wrapper, Spring Data JPA + PostgreSQL, Flyway, Lombok, Bean Validation, Spring Security with OAuth2 resource server for JWTs). Base package `com.nexhome.app`. All implemented features live here.
- `web/nexhome-web-app/`: React 19 + Vite 8 SPA (plain JavaScript/JSX). Still the default Vite template.
- `android/`: empty placeholder for the future Partner and Customer apps.

Run commands from inside the relevant sub-project directory.

## Backend (`backend/nexhome`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run (use mvnw.cmd on Windows cmd/PowerShell)
SPRING_PROFILES_ACTIVE=dev ./mvnw clean test              # all tests (two need the dev DB)
./mvnw test -Dtest=NexHomeUserServiceImplTest             # one class; Mockito unit tests need no DB
./mvnw test -Dtest=NexHomeAuthServiceImplTest#wrongOtpCountsAttemptsAndLocks   # one method
./mvnw package                                            # build target/nexhome-0.0.1-SNAPSHOT.jar
```

Use `clean` after deleting test classes: surefire otherwise runs stale `.class` files left in `target/test-classes`.

### Configuration

- `application.properties` reads the datasource from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`. These come from `.env` (see `.env.example`; gitignored), but the import (`spring.config.import=optional:file:.env[.properties]`) is in `application-dev.properties`, so it **only happens under the `dev` profile**. Without `dev`, startup fails with `'url' must start with "jdbc"`. The path is relative to the working directory, so run from `backend/nexhome/`.
- No profile is active by default. `ActiveProfileLogger` logs the active profile and warns when none is set.
  - `dev`: loads `.env`, `ddl-auto=validate`, SQL logging, `com.nexhome` at DEBUG, and `nexhome.otp.log-plaintext=true` (generated OTPs are logged because no SMS provider exists yet).
  - `prod`: `ddl-auto=validate`, error stacktraces/messages hidden.
- **Flyway owns the schema** (`src/main/resources/db/migration`). Hibernate only validates, in every profile. Flyway refuses to start on a non-empty schema without its history table.
- `nexhome.otp.*` (length, expiry-minutes, max-attempts, resend-cooldown-seconds, log-plaintext) binds to `config/NexHomeOtpProperties` (`@ConfigurationPropertiesScan` on `NexhomeApplication`).
- `nexhome.security.*` binds to `config/NexHomeSecurityProperties`: `jwt-secret` from **`JWT_SECRET`** (Base64, ≥ 32 bytes; startup fails otherwise, so `.env` must define it), `jwt-issuer`, `access-token-minutes` (15), `refresh-token-days` (30), `cors-allowed-origins` (`CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`).
- `spring.jpa.open-in-view=false`: lazy associations must be read inside `@Transactional` service methods.
- Devtools is on the classpath. The developer usually runs the app from IntelliJ on port 8080, and it restarts whenever `target/classes` changes, including after `./mvnw compile`. For ad-hoc runs use another port: `-Dspring-boot.run.arguments=--server.port=8090`. During a restart, requests briefly get Spring Boot's default error JSON; that is not the exception handler failing.

### Tests

| Test | Kind |
|---|---|
| `service/NexHome{Lookup,User,Role,Function,Auth}ServiceImplTest` | Mockito unit tests of service rules, plus `Validator`-based checks of DTO constraints. No DB. Mock `NexHomePermissionService.currentUserId()` for the acting user. |
| `controller/NexHomeAuthFlowTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` against the dev DB, rolled back: the real security filters, sign-up, OTP login, refresh rotation and reuse detection, logout, `@PreAuthorize` role-function checks and the 401/403 JSON bodies. It sets a known OTP hash with a native update, and mints access tokens with the app's `JwtEncoder` for users that cannot receive an OTP. |
| `NexhomeApplicationTests` | context load against the dev DB: runs Flyway, `ddl-auto=validate` checks every entity against the real schema, and every Spring Data derived query name is validated. |
| `service/NexHomeIdentitySchemaTest` | `@SpringBootTest` + `@ActiveProfiles("dev")` + `@Transactional` + `@WithMockUser("1")` against the **dev database**, rolled back. Proves what mocks cannot: seed data, the partial unique indexes (one default lookup value, one pending OTP, case-insensitive email), the composite user-role key and the JSONB column. |

## Architecture and conventions

Layered packages under `com.nexhome.app` (keep this layout; do not switch to the PDF's feature packages):

| Package | Contents |
|---|---|
| `controller` | thin `@RestController`s: `@Valid @RequestBody`, then delegate |
| `service` | interface `NexHome<X>Service` + `NexHome<X>ServiceImpl` (`@Service`), **both directly in `service`**, with no `impl` subpackage. Owns `@Transactional` business logic and returns DTOs only, never entities. |
| `repository` | one `JpaRepository` per entity |
| `model` | JPA entities, `NexHomeUserRoleId` (embedded key), `NexHomeLookupCodes` (lookup codes used by code), and the non-entity `ExceptionBody` |
| `dto` | request/response **records**: `NexHome<X>CreateRequest` / `...UpdateRequest` / `...Response` (plus `NexHomeUserStatusUpdateRequest`, `NexHomeUserRoleAssignRequest`, `NexHomeAuthOtpVerifyRequest`) |
| `mapper` | hand-written `@Component` mappers, one per entity: `to<X>Entity`, `update<X>Entity`, `to<X>Dto` |
| `exception` | `GlobalExceptionHandler` + `DuplicateResourceException` (409), `InvalidRequestException` (400), `ResourceNotFoundException` (404), `TooManyRequestsException` (429), `UnauthorizedException` (401) |
| `config` | `NexHomeSecurityConfig` (filter chain, JWT encoder/decoder, CORS), `NexHomeSecurityErrorHandler` (401/403 bodies from the filters), `NexHomeSecurityProperties`, `PasswordEncoderConfig` (BCrypt bean, used for OTP hashes), `NexHomeOtpProperties`, `ActiveProfileLogger` |

- **Naming:** every domain class uses the `NexHome` prefix (capital H). The entry point is still `NexhomeApplication`. Tables are `nexhome_<pdf table name>`; columns follow the PDF exactly (`id` primary keys, `updated_date` / `updated_by`, `can_read`, `is_active`, ...). Entity fields mirror the columns (`id`, `updatedBy`); `is_*` boolean columns become fields without the prefix (`systemManaged`, `defaultValue`, `active`) so Lombok getters read well. DTOs keep descriptive ids (`userId`, `roleId`, `lookupTypeId`, ...) and expose `is*` names as in the PDF (`isActive`, `isDefault`, `isSystemManaged`).
- **Group by feature, not entity:** one controller and service per feature.
  - **Lookups:** types and values (`NexHomeLookupController`, `/api/lookups`).
  - **Auth:** OTP request/verify (`NexHomeAuthController`, `/api/auth`).
  - **Users:** users, account status and role assignment (`NexHomeUserController`, `/api/users`).
  - **Roles:** roles with their role functions (`NexHomeRoleController`, `/api/roles`).
  - **Functions:** `NexHomeFunctionController`, `/api/functions`.

  Never add a separate controller or service for a sub-entity.
- **URL style:** verb paths, not REST verbs on ids, e.g. `POST .../create` or `.../add`, `PUT .../update` (id in the body), `DELETE .../delete/{id}`, `GET` for reads. Creates return 201, updates 200, deletes 204.
- **Lookup values in business columns:** stored as the raw `value_code` string with **no foreign key** to `nexhome_lookup_values`. Services validate submitted codes with `NexHomeLookupService.validateCode(typeCode, code, "<Field>")`, which returns the trimmed code or throws 400 `"<Field> must be one of: A, B, C."`. Codes that code depends on are constants in `model/NexHomeLookupCodes`.
- **Updates rely on dirty checking:** services load the entity inside `@Transactional`, mutate it, and let the commit write it. They don't call `save()`; only creates `save()` new entities. `saveAndFlush` is used only to force statement ordering ahead of a partial unique index (see lookup defaults and OTPs) or to populate `@CreationTimestamp` on the non-IDENTITY `NexHomeUserRole` for the response.
- **Partial update semantics:**
  - A null or missing field keeps its current value.
  - Required text fields cannot be blanked: `@Pattern(regexp = "(?s).*\\S.*", message = "<Field> cannot be blank")` rejects blanks but allows null.
  - Optional text fields differ:
    - `email` and `profilePhotoUrl` (user) and `metadata` (lookup value) are null-checked: missing keeps the value, `""` clears it.
    - every `description` (role, function, lookup type, lookup value) has **no null check** and is always replaced: missing or `""` clears it.
- **Codes:** role `code`, lookup `typeCode`, `functionModule` and `functionCode` must be `^[A-Z][A-Z0-9_]*$`. Lookup `valueCode` also allows lower case, dots and hyphens (language codes like `te`).
- **Immutable identity fields:** once created these never change.
  - **Which:** role `code`; function `functionModule`/`functionCode`; lookup type `typeCode`/`isSystemManaged`; lookup value `lookupTypeId`/`valueCode`; role function `roleId`/`functionId`; user-role `userId`/`roleId` (the primary key). User `countryCode`/`mobileNumber` change **only** through a verified PHONE_CHANGE OTP.
  - **Enforced twice:**
    1. The update DTO declares the field only with `@Null(message = "<Field> cannot be updated")`, so a client sending it gets 400 instead of being silently ignored (Jackson ignores unknown properties).
    2. The entity column has `updatable = false`, so Hibernate never writes it even if code calls the setter (not for the mobile number, which the OTP flow updates).
  - Records are located by their own id, never by an immutable field.
- **Validation messages** must be full, capitalised sentences naming the field, e.g. `@Pattern(..., message = "Mobile number must be 6 to 15 digits")`. The error handler returns messages without field-name prefixes, so a default message like `must not be blank` would not say which field failed.
- **Response dates** must look like `Thu, 08 Oct 2026 14:29:20 IST`. This is done per field, deliberately with no global Jackson config. Every `Instant` / `ZonedDateTime` in a response DTO (and `ExceptionBody.timestamp`) carries:
  ```java
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
          timezone = "Asia/Kolkata")
  ```
  Without it the field comes out as ISO UTC. Entities store `Instant` (UTC, `TIMESTAMPTZ`). Jackson is version 3 (`tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation`).
- **Acting user:** services take it from `NexHomePermissionService.currentUserId()` (the access token's subject) for `created_by` / `updated_by` / `assigned_by` / `status_updated_by`. Public flows (register, OTP, refresh, logout) record the user they act on. User 1 is the **system account** seeded by V1 (`+91 0000000000`, SUPER_USER) that owns seed data; it cannot receive an OTP. The first real super user (`+91 8897182017`) is seeded by `V3__bootstrap_super_user.sql`; further admins are created through the API.
- **No hard deletes of referenced data:** users are never deleted (set `accountStatus` to `DEACTIVATED`); lookups have no delete endpoints. Roles and functions can be deleted only when unused (409 `... deactivate it instead.`), matching the `ON DELETE RESTRICT` foreign keys. Role functions can be deleted.

### Security

- **Authentication:** stateless. `POST /api/auth/otp/verify` (LOGIN or REGISTRATION) returns an **access token** (HS256 JWT, 15 min; `sub` = user id, `iss`, `jti`, informational `roles`) and a **refresh token** (32 random bytes, 30 days, stored only as SHA-256 in `nexhome_auth_refresh_tokens`). Clients send `Authorization: Bearer <accessToken>`.
- **Public endpoints** (`NexHomeSecurityConfig`): `POST /api/auth/{register, otp/request, otp/verify, token/refresh, logout}` and `GET /api/lookups/values/*`. Everything else needs a token. An invalid token is rejected even on public paths.
- **Authorization:** every admin endpoint has `@PreAuthorize("@permissions.can('<FUNCTION_CODE>', 'READ|WRITE|DELETE')")`. The `permissions` bean (`NexHomePermissionServiceImpl`) reads the database on every call: the caller's account must be ACTIVE; SUPER_USER always passes; otherwise an active user role → active role → active role function → active function must grant the flag. Revoking a role or blocking an account therefore takes effect immediately, despite unexpired tokens. `@permissions.isSelfOrCan(#userId, ...)` lets users read and update their own account. `@permissions.isCurrentUserSuperUser()` is for endpoints only super users may ever use.
- **Seeded functions** (V2, V5): `LOOKUP_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE`, `FUNCTION_MANAGE`, `STAFF_MANAGE` (listing admins and sub-admins). ADMIN: lookups and users read/write, staff read, roles and functions read only (so admins cannot grant themselves permissions). SUB_ADMIN: lookups and users read. **New admin endpoints need a function**: add it (and its role functions) in a migration and guard the controller method.
- **Escalation guards** (`NexHomeUserServiceImpl`): only a SUPER_USER can grant, activate or withdraw the SUPER_USER role or change a super user's status; nobody can change their own status.
- **Sessions:** every login is a session (`session_id`, kept across refresh rotations). Access tokens carry it as the `sid` claim, and `config/NexHomeSessionTokenValidator` (run by the JWT decoder after signature/expiry/issuer) accepts a token only while its session has an unrevoked, unexpired refresh token, so logout cuts access tokens off immediately (401 `The session has ended. Log in again.`). One indexed query per authenticated request; tokens without `sid` are rejected. Refresh rotates (old token `ROTATED`, `replaced_by_id` set). Presenting a rotated token again revokes **all** the user's sessions (`REUSE_DETECTED`). An account leaving ACTIVE revokes all sessions. `refreshTokens` is `@Transactional(noRollbackFor = UnauthorizedException.class)` so revocations survive the 401.
- 401/403 bodies are `ExceptionBody`: from the filters via `NexHomeSecurityErrorHandler`, and from `@PreAuthorize` / services via `GlobalExceptionHandler` (`AccessDeniedException` → 403, `UnauthorizedException` / `AuthenticationException` → 401). Services throw Spring's `AccessDeniedException` for permission rules.
- CSRF is disabled (bearer tokens only, no cookies); CORS allows the origin patterns in `nexhome.security.cors-allowed-origins` on `/api/**`: every `http://localhost:[*]` / `127.0.0.1` port under `dev`, `http://localhost:5173` otherwise (override with `CORS_ALLOWED_ORIGINS`). `file://` pages (origin `null`) are deliberately not allowed; open `api-docs.html` through IntelliJ.

### Error handling

Every error body is `model/ExceptionBody` `{path, method, statusCode, error, message, timestamp}`, returned as `ResponseEntity<ExceptionBody>` from `exception/GlobalExceptionHandler`. That class is a plain `@RestControllerAdvice` that deliberately does **not** extend `ResponseEntityExceptionHandler`, whose overrides must return `ResponseEntity<Object>`. It handles:

- our exceptions (409 / 400 / 404 / 429 / 401) with their client-safe message, plus Spring Security's `AccessDeniedException` (403) and `AuthenticationException` (401);
- `DataIntegrityViolationException`: 409 `The request conflicts with existing data.`;
- Bean Validation failures: 400, `message` = the first error of each invalid field joined with `", "`;
- unreadable JSON: 400 `Request body is missing or malformed.`;
- a path or query parameter of the wrong type (`MethodArgumentTypeMismatchException`): 400 `Parameter <name> has an invalid value.`;
- the `Exception` catch-all: Spring MVC errors implementing `ErrorResponse` (404 unknown path, 405, 415, ...) keep their status and detail; anything else becomes 500 with a generic message and the stack trace logged.

Never return `ProblemDetail` or Spring Boot's default error JSON.

## API

`backend/nexhome/api-docs.html` is an interactive reference of every endpoint with working example JSON; it saves the tokens from Verify OTP / Refresh and sends them. Update its `API` and `ERRORS` arrays whenever an endpoint, field or message changes.

### Lookups (`NexHomeLookupController`, `/api/lookups`)

| Endpoint | Body / rules |
|---|---|
| `POST /types/create` | `typeCode` (unique: 409), `typeName`, `description`, `activeFlag` (default true), `isSystemManaged` (default **false** for API-created types; seeded types are true) |
| `PUT /types/update` | `lookupTypeId`; `typeName`, `description`, `activeFlag`. A system-managed type cannot be deactivated (400). |
| `GET /types` | all types |
| `GET /types/values/{lookupTypeId}` | all values of a type, including inactive |
| `POST /values/add` | `lookupTypeId`, `valueCode` (unique per type: 409), `displayName`, `description`, `displayOrder` (default 0), `activeFlag`, `isDefault`, `metadata` (JSON object, default `{}`) |
| `PUT /values/update` | `lookupValueId`; display fields, `activeFlag`, `isDefault`, `metadata`. Values of a system-managed type cannot be deactivated; an inactive value cannot be the default. |
| `GET /values/{typeCode}` | dropdown options: active values of an active type in `display_order` |

**One default per type:** `isDefault: true` clears the current default with `saveAndFlush` **before** the new one is written, because the partial unique index `ux_nexhome_lookup_values_one_default` would otherwise reject it.

### Auth (`NexHomeAuthController`, `/api/auth`)

| Endpoint | Body / rules |
|---|---|
| `POST /register` (public) | `countryCode` (`+` and 1–4 digits), `mobileNumber` (6–15 digits, unused: 409), `email`, `roleCode` (`CUSTOMER` or `PARTNER`). Creates the ACTIVE, unverified account (its own `created_by`) and sends a REGISTRATION OTP. 201. |
| `POST /otp/request` (public) | `countryCode`, `mobileNumber`, `purpose` (`LOGIN`, or `REGISTRATION` while unverified). Account must exist (404) and be ACTIVE. 201 with the request state, never the OTP. |
| `POST /otp/verify` (public) | `countryCode`, `mobileNumber`, `purpose`, `otp`. Marks the number verified, sets `last_login_at`, returns the user and `tokens`. |
| `POST /token/refresh` (public) | `refreshToken` → new `tokens` (401 if unknown, expired, revoked or account not ACTIVE) |
| `POST /logout` (public) | `refreshToken`; revokes that session; 204 (also for unknown tokens) |
| `POST /logout/all` | revokes every session of the caller; 204 |
| `POST /phone-change/otp/request` | `countryCode`, `mobileNumber` (the caller's new, unused number); 201 |
| `POST /phone-change/otp/verify` | `countryCode`, `mobileNumber`, `otp`; the OTP must be the caller's; moves the account to the number |

- **OTP rules:** only a BCrypt hash is stored; 6 digits; expires after 5 minutes; 5 wrong attempts → LOCKED; 60 s resend cooldown (429). A new OTP first expires the pending one with `saveAndFlush` (partial unique index `ux_nexhome_auth_otp_requests_one_pending`). SERVICE_START / SERVICE_COMPLETION return 400 until bookings exist; PHONE_CHANGE is rejected on the public endpoints.
- `verifyOtp` / `verifyPhoneChangeOtp` are `@Transactional(noRollbackFor = InvalidRequestException.class)` so failed attempts, expiry and locking are saved even though the request fails. Keep every other state change after `checkOtp` and the purpose checks.
- **TODO:** no SMS provider (dev logs the OTP).

### Users (`NexHomeUserController`, `/api/users`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` (USER_MANAGE write) | staff accounts: `countryCode`, `mobileNumber` (unused: 409), `email` (optional, stored lower-cased, unique: 409), `profilePhotoUrl` (http/https), `roleId` (active role, else 404 `Role Not Found or Inactive`). Creates an ACTIVE, unverified account with that role. |
| `GET /me` | the caller |
| `GET /customers`, `GET /partners` (USER_MANAGE read: SUPER_USER, ADMIN, SUB_ADMIN) | page of users who currently hold the role (active assignment), newest first. Query params, all optional: `page` (0-based), `size` (1–100, default 10), `accountStatus`, `search` (part of mobile number or email). Returns `NexHomePageResponse` `{content, page, size, totalElements, totalPages}`; roles of the page's users are loaded in one query. |
| `GET /admins`, `GET /sub-admins` (STAFF_MANAGE read: SUPER_USER, ADMIN) | same, for ADMIN / SUB_ADMIN holders |
| `GET /super-users` (`@permissions.isCurrentUserSuperUser()`: ACTIVE SUPER_USER only, not grantable through role functions) | same, for SUPER_USER holders |
| `GET /{userId}` (self or USER_MANAGE read) | one user |
| `PUT /update` (self or USER_MANAGE write) | `userId`; `email`, `profilePhotoUrl`. Mobile number, status and roles are rejected (400). |
| `PUT /status/update` (USER_MANAGE write) | `userId`, `accountStatus` (ACCOUNT_STATUS code; same status again is 400; not your own). Records `status_updated_by/date`; leaving ACTIVE revokes all sessions. |
| `POST /roles/assign` (USER_MANAGE write) | `userId`, `roleId` (active role); 409 if the user already has it |
| `PUT /roles/update` (USER_MANAGE write) | `userId`, `roleId`, `isActive`; an inactive role cannot be activated |

**User identity:** one account per `(country_code, mobile_number)` (unique constraint); a user may hold several roles through `nexhome_user_roles`. Names and addresses are **not** on users: they come with the Module 2 profile tables.

### Roles and role functions (`NexHomeRoleController`, `/api/roles`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `code` (unique: 409), `name`, `description`, `activeFlag` (default true) |
| `PUT /update` | `roleId`; `name`, `description` (always replaced), `activeFlag` |
| `DELETE /delete/{roleId}` | only if no user roles or role functions reference it, else 409 |
| `POST /functions/add` | `roleId`, `functionId` (both must exist: 404), `canRead`/`canWrite`/`canDelete` (default false), `activeFlag` (default true); 409 if the pair exists |
| `PUT /functions/update` | `roleFunctionId`; `canRead`/`canWrite`/`canDelete`/`activeFlag` (null unchanged) |
| `DELETE /functions/delete/{roleFunctionId}` | just that row |

`canWrite` or `canDelete` always also grants `canRead` (`NexHomeRoleFunctionMapper`). Seeded roles: SUPER_USER, ADMIN, SUB_ADMIN, CUSTOMER, PARTNER.

### Functions (`NexHomeFunctionController`, `/api/functions`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `functionModule`, `functionCode` (unique: 409), `functionName`, `functionType` (FUNCTION_TYPE code), `description`, `activeFlag` |
| `PUT /update` | `functionId`; `functionName`, `functionType`, `description` (always replaced), `activeFlag` |
| `DELETE /delete/{functionId}` | only if no role function grants it, else 409 |

## Data model (`model` package)

| Table | Entity | Key columns / constraints |
|---|---|---|
| `nexhome_lookup_types` | `NexHomeLookupType` | `type_code` (unique), `type_name`, `description`, `active_flag`, `is_system_managed` |
| `nexhome_lookup_values` | `NexHomeLookupValue` | `lookup_type_id` → types (RESTRICT); `value_code` (unique per type); `display_name`, `display_order`, `active_flag`, `is_default` (one per type, partial index); `metadata` JSONB (mapped as a JSON `String`, converted to `Map` in the mapper) |
| `nexhome_users` | `NexHomeUser` | `country_code` + `mobile_number` (unique); `email` (unique on `lower(email)` when not null); `profile_photo_url`; `account_status`; `account_verified_date`; `status_updated_by/date`; `last_login_at` |
| `nexhome_roles` | `NexHomeRole` | `id` **SMALLINT** (`Short` in Java); `code` (unique); `name`; `description`; `active_flag` |
| `nexhome_user_roles` | `NexHomeUserRole` | PK (`user_id`, `role_id`) via `@EmbeddedId NexHomeUserRoleId` + `@MapsId`; `assigned_by`, `assigned_at`, `is_active`. Implements `Persistable` so `save()` persists instead of merging an assigned key. |
| `nexhome_functions` | `NexHomeFunction` | `function_module`, `function_code` (unique), `function_name`, `function_type`, `description`, `active_flag` |
| `nexhome_role_functions` | `NexHomeRoleFunction` | `role_id` → roles, `function_id` → functions (both RESTRICT); `can_read`, `can_write`, `can_delete`, `active_flag`; unique (`role_id`, `function_id`) |
| `nexhome_auth_refresh_tokens` (V2, rebuilt in V4; not in the PDF) | `NexHomeAuthRefreshToken` | `user_id`; `session_id` (UUID of the login, shared across rotations; partial index where not revoked); `token_hash` (SHA-256 hex, unique); `expires_at`; `last_used_at`; `revoked_at` + `revoked_reason` (both or neither: LOGOUT, LOGOUT_ALL, ROTATED, REUSE_DETECTED, ACCOUNT_STATUS_CHANGED); `replaced_by_id`; `user_agent` |
| `nexhome_auth_otp_requests` | `NexHomeAuthOtpRequest` | `user_id` (nullable), `booking_id` (no FK until bookings exist), `country_code`, `mobile_number`, `otp_hash`, `purpose`, `request_status`, `attempt_count` (≥ 0), `expires_at`, `verified_at`; one PENDING per number + purpose + booking (partial index) |

Conventions every entity follows; keep them for new entities:

- The audit columns `creation_date`, `created_by`, `updated_date`, `updated_by` and the spare columns `attribute1`–`attribute5` are declared **directly in each entity**, after the business columns. Do not extract them into a `@MappedSuperclass` / `@Embeddable`; this was tried and rejected. `created_by` / `updated_by` are foreign keys to `nexhome_users` in the DDL but plain `Long` fields in Java.
- `creation_date` / `updated_date` come from `@CreationTimestamp` / `@UpdateTimestamp` (DB default `now()` too). `@UpdateTimestamp` is set at flush, after the response DTO is built.
- Primary keys are `id` with `GENERATED BY DEFAULT AS IDENTITY` (`IDENTITY` in JPA). Associations are `LAZY` with explicit `@JoinColumn(name = ..., referencedColumnName = "id")`. Use Lombok `@Getter @Setter @NoArgsConstructor` only, never `@Data`. Map `TEXT` columns with `columnDefinition = "text"`.
- **Schema changes go through a new Flyway migration** (`V<n>__<description>.sql`), never by editing an applied one. Write columns in PDF order, add indexes/constraints there (partial indexes, checks, FKs), and keep the entity in sync: `validate` fails startup otherwise. Seed lookup values for a module in that module's migration.

## Repositories

- Derived query names follow **Java field paths**, not column names, e.g. `findByRoleIdAndFunctionId` on `NexHomeRoleFunction` resolves to `role.id` / `function.id`, and `findByUserIdOrderByRoleIdAsc` on `NexHomeUserRole` to `user.id` / `role.id`.
- Return `Optional` only when the lookup is unique (by constraint or business rule), otherwise `List`.
- **Optional filters: never `(:x is null or ...)` in JPQL.** After a few executions the PostgreSQL driver server-prepares the statement and a null parameter is then typed `bytea`, failing with `operator does not exist: character varying ~~ bytea` (500). Build the query from only the filters given with a `Specification` (`JpaSpecificationExecutor`), as `NexHomeUserRepository.findByActiveRole` does; escape `%`/`_` in LIKE input. `NexHomeAuthFlowTest.unfilteredListsKeepWorkingWhenCalledRepeatedly` guards this.

## Known gaps

- No SMS provider: OTPs are only logged, and only when `nexhome.otp.log-plaintext=true` (dev).
- No rate limit beyond the per-number OTP cooldown and attempt lock (e.g. per IP).
- An existing customer cannot add the PARTNER role to their own account yet (register returns 409); an admin can assign it.
- LOGIN/REGISTRATION OTP requests reveal whether a mobile number has an account (404).
- Users have no `public_id`, although the PDF's users constraints mention one (no column is defined for it).
- `InvalidRequestException`'s message is shown to clients, so only use client-safe text.

## Web (`web/nexhome-web-app`)

```bash
npm install
npm run dev       # Vite dev server with HMR
npm run build     # production build to dist/
npm run lint      # ESLint (flat config: js recommended + react-hooks + react-refresh)
npm run preview   # serve the production build
```

Still the default Vite template: no test runner, and no dev proxy to the backend in `vite.config.js`.
