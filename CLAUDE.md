# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

NexHome is a multi-client project in early development. Each client lives in its own self-contained sub-project with its own build tooling; there is no root-level build and the root is not a git repository.

- `backend/nexhome/`: Spring Boot 4.1 REST API (Java 21, Maven wrapper, Spring Data JPA + PostgreSQL, Lombok, Bean Validation, `spring-security-crypto` for BCrypt). Base package `com.nexhome.app`. All implemented features live here.
- `web/nexhome-web-app/`: React 19 + Vite 8 SPA (plain JavaScript/JSX). Still the default Vite template.
- `android/`: empty placeholder for the future Partner and Customer Android apps.

Run commands from inside the relevant sub-project directory.

## Backend (`backend/nexhome`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run (use mvnw.cmd on Windows cmd/PowerShell)
SPRING_PROFILES_ACTIVE=dev ./mvnw test                    # all tests (several need the dev DB)
./mvnw test -Dtest=NexHomeUserServiceImplTest             # one class; Mockito unit tests need no DB
./mvnw test -Dtest=NexHomeUserServiceImplTest#createsSubmittedInactiveUserWithoutPassword   # one method
./mvnw package                                            # build target/nexhome-0.0.1-SNAPSHOT.jar
```

### Configuration

- `application.properties` reads the datasource from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`. These come from `.env` (see `.env.example`; gitignored), but the import (`spring.config.import=optional:file:.env[.properties]`) is in `application-dev.properties`, so it **only happens under the `dev` profile**. Without `dev`, startup fails with `'url' must start with "jdbc"`. The path is relative to the working directory, so run from `backend/nexhome/`.
- No profile is active by default. `ActiveProfileLogger` logs the active profile and warns when none is set.
  - `dev`: loads `.env`, `ddl-auto=update` (adds missing tables/columns, never drops, reorders or changes nullability), SQL logging, `com.nexhome` at DEBUG.
  - `prod`: `ddl-auto=validate` (schema must already exist; there is no migration tool), error stacktraces/messages hidden.
- `spring.jpa.open-in-view=false`: lazy associations must be read inside `@Transactional` service methods.
- Devtools is on the classpath. The developer usually runs the app from IntelliJ on port 8080, and it restarts whenever `target/classes` changes, including after `./mvnw compile`. With `ddl-auto=update`, such a restart creates missing tables in Hibernate's default column order. For ad-hoc runs use another port: `-Dspring-boot.run.arguments=--server.port=8090`. During a restart, requests briefly get Spring Boot's default error JSON; that is not the exception handler failing.

### Tests

| Test | Kind |
|---|---|
| `service/NexHomeUserServiceImplTest`, `NexHomeRoleServiceImplTest`, `NexHomeFunctionServiceImplTest` | Mockito unit tests of service rules, plus `Validator`-based checks of DTO constraints. No DB. |
| `NexhomeApplicationTests` | context load. It also validates every Spring Data derived query name, so it catches broken repository methods. |
| `model/NexHomeUserDetailsAddressesTest`, `service/NexHomeDeleteCascadeTest` | `@SpringBootTest` + `@ActiveProfiles("dev")` + `@Transactional` against the **dev database**, rolled back. They prove DB-level behaviour mocks cannot: lazy/read-only associations, the one-active-address index, `updatable = false` columns, flush ordering, and delete cascades. |

To check mappings without touching the schema, add `-DargLine="-Dspring.jpa.hibernate.ddl-auto=none"`.

## Architecture and conventions

Layered packages under `com.nexhome.app`:

| Package | Contents |
|---|---|
| `controller` | thin `@RestController`s: `@Valid @RequestBody`, then delegate |
| `service` | interface `NexHome<X>Service` + `NexHome<X>ServiceImpl` (`@Service`), **both directly in `service`**, with no `impl` subpackage. Owns `@Transactional` business logic and returns DTOs only, never entities. |
| `repository` | one `JpaRepository<Entity, Long>` per entity |
| `model` | JPA entities, `NexHomeUserStatus` enum, and the non-entity `ExceptionBody` |
| `dto` | request/response **records**: `NexHome<X>CreateRequest` / `...UpdateRequest` / `...Response` (the user-details and address create requests are named `NexHomeUserDetailsRequest` / `NexHomeUserAddressRequest`) |
| `mapper` | hand-written `@Component` mappers, one per entity: `to<X>Entity`, `update<X>Entity`, `to<X>Dto` |
| `exception` | `GlobalExceptionHandler` + `DuplicateResourceException` (409), `InvalidRequestException` (400), `ResourceNotFoundException` (404, reused for any missing record) |
| `config` | `PasswordEncoderConfig` (BCrypt bean), `ActiveProfileLogger` |

- **Naming:** every domain class uses the `NexHome` prefix (capital H). The entry point is still `NexhomeApplication`. Tables are lowercase `nexhome_*`.
- **Group by feature, not entity:** one controller and service per feature.
  - **Users:** users with their details, addresses, status and password (`NexHomeUserController`, `/api/users`).
  - **Roles:** roles with their role functions (`NexHomeRoleController`, `/api/roles`).
  - **Functions:** `NexHomeFunctionController`, `/api/functions`.

  Never add a separate controller or service for a sub-entity.
- **URL style:** verb paths, not REST verbs on ids, e.g. `POST .../create` or `.../add`, `PUT .../update` (id in the body), `DELETE .../delete/{id}`. Creates return 201, updates 200, deletes 204.
- **Updates rely on dirty checking:** services load the entity inside `@Transactional`, mutate it, and let the commit write it. They don't call `save()`; only creates `save()` new entities. `saveAndFlush` is used only to force statement ordering (see addresses).
- **Partial update semantics:**
  - A null or missing field keeps its current value.
  - Required text fields cannot be blanked: `@Pattern(regexp = "(?s).*\\S.*", message = "<Field> cannot be blank")` rejects blanks but allows null.
  - Optional text fields differ by mapper:
    - `email` (user) and `lastName` (details) are null-checked: missing keeps the value, `""` clears it.
    - `address3` (address) and `description` (role) have **no null check** and are always replaced: missing or `""` clears them.
- **Immutable identity fields:** once created these never change.
  - **Which:** user `phone`/`role`; role `roleName`; function `functionModule`/`functionCode`; role function `roleId`/`functionId`; the owning `user_id` of user details and user addresses.
  - **Enforced twice:**
    1. The update DTO declares the field only with `@Null(message = "<Field> cannot be updated")`, so a client sending it gets 400 instead of being silently ignored (Jackson ignores unknown properties).
    2. The entity column has `updatable = false`, so Hibernate never writes it even if code calls the setter.
  - **Password:** the same `@Null` trick blocks `password` in `NexHomeUserCreateRequest` and `NexHomeUserUpdateRequest`; it is only set through the password endpoints.
  - Records are located by their own id (`userDetailsId`, `userAddressId`, `roleFunctionId`, ...), never by an immutable field.
- **Validation messages** must be full, capitalised sentences naming the field, e.g. `@Pattern(..., message = "Phone must be exactly 10 digits")`. The error handler returns messages without field-name prefixes, so a default message like `must not be blank` would not say which field failed.
- **Response dates** must look like `Thu, 08 Oct 2026 14:29:20 IST`. This is done per field, deliberately with no global Jackson config. Every `Instant` / `ZonedDateTime` in a response DTO (and `ExceptionBody.timestamp`) carries:
  ```java
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
          timezone = "Asia/Kolkata")
  ```
  Without it the field comes out as ISO UTC. `spring.jackson.date-format` only affects `java.util.Date`. Entities store `Instant` (UTC).
- **Acting user:** there is no authentication yet. Each service impl has `DEFAULT_LOGGED_IN_USER_ID = 1L` (TODO) for `created_by` / `last_update_by`; replace it with the authenticated user once login exists. The exception is `setPassword`, which records the user themself.

### Error handling

Every error body is `model/ExceptionBody` `{path, method, statusCode, error, message, timestamp}`, returned as `ResponseEntity<ExceptionBody>` from `exception/GlobalExceptionHandler`. That class is a plain `@RestControllerAdvice` that deliberately does **not** extend `ResponseEntityExceptionHandler`, whose overrides must return `ResponseEntity<Object>`. It handles:

- our exceptions (409 / 400 / 404) with their client-safe message;
- `DataIntegrityViolationException`: 409 `The request conflicts with existing data.`;
- Bean Validation failures: 400, `message` = the first error of each invalid field joined with `", "`;
- unreadable JSON, including bad enum values: 400 `Request body is missing or malformed.`;
- the `Exception` catch-all: Spring MVC errors implementing `ErrorResponse` (404 unknown path, 405, 415, ...) keep their status and detail; anything else becomes 500 with a generic message and the stack trace logged.

Never return `ProblemDetail` or Spring Boot's default error JSON.

## API

### Users (`NexHomeUserController`, `/api/users`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `phone` (exactly 10 digits), `email` (optional, needs `@` and a dotted domain, stored lower-cased), `roleId` (must be an active role: `findByRoleIdAndActiveFlag`, else 404 `Role Not Found or Inactive`). Creates a `SUBMITTED`, inactive user with **no password**. 409 if the phone + role pair exists. |
| `PUT /update` | `userId`; updatable: `email`, `activeFlag`. `activeFlag: true` only for `ACTIVATED` users (400 otherwise); deactivating is always allowed. |
| `PUT /status/update` | admin: `userId`, `userStatus` = `APPROVED` or `REJECTED` (see lifecycle) |
| `POST /password/set` | `userId`, `password` (8–16), `confirmPassword`; only for `APPROVED` users. Activates the account. |
| `PUT /password/update` | `userId`, `currentPassword`, `newPassword` (8–16); only for `ACTIVATED` users. Current password must match (400), new must differ (400). |
| `DELETE /delete/{userId}` | cascades: addresses, details, user |
| `POST /details/add` | `userId`, `firstName` (required), `lastName`; one details row per user (409); `fullName` = first + last |
| `PUT /details/update` | `userDetailsId`; `firstName`, `lastName`; `fullName` recalculated |
| `POST /address/add` | `userId`, `address1`, `address2`, `city`, `district`, `state`, `pinCode` (6 digits) required; `address3` optional; `activeFlag` |
| `PUT /address/update` | `userAddressId`; address fields and `activeFlag` (`true` activates, `false` deactivates, null unchanged) |
| `DELETE /address/delete/{userAddressId}` | just that address; if it was active, no other address is activated |

**User identity:** a user is identified by **phone + role**. The same phone may be used for one user per role (e.g. partner and customer), checked by `findByPhoneAndRoleRoleId`, since the database has no unique constraint. Never look a user up by phone alone; `findByPhone` / `findByEmail` return `Optional` and would throw on duplicates. Email is not checked for duplicates.

**User lifecycle** (`NexHomeUserStatus`, column `user_status` with a DB check constraint):

```
create ─► SUBMITTED ─(admin)─► APPROVED ─(POST /password/set)─► ACTIVATED + active_flag = true
              └──(admin)──► REJECTED ◄─┘     (REJECTED can be APPROVED later)
```

- Allowed admin moves: SUBMITTED/REJECTED → APPROVED and SUBMITTED/APPROVED → REJECTED. Setting the same status again is 400, `ACTIVATED` can never be set by the admin, and an activated user's status cannot change.
- **TODO:** on approval, email the user a "set your password" link. Until then `POST /password/set` is authorised by `userId` alone, so anyone who knows an approved user's id can set their password. It must require a one-time token from that email.

**Active address:** only an explicit `activeFlag: true` makes an address active (on add or update). In that case the user's current active address is deactivated with `saveAndFlush` **before** the new one is inserted or activated. IDENTITY inserts run immediately, ahead of pending updates, so without the flush the one-active index rejects the change. `false`, null or missing never touches the current active address, even if the user has none.

### Roles and role functions (`NexHomeRoleController`, `/api/roles`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `roleName` (required, unique: 409), `description`, `activeFlag` (default true) |
| `PUT /update` | `roleId`; updatable: `description` (always replaced), `activeFlag` |
| `DELETE /delete/{roleId}` | cascades: addresses and details of every user with the role, **those users**, the role's role functions, the role |
| `POST /functions/add` | `roleId`, `functionId` (both must exist: 404), `read`/`write`/`delete` (default false), `activeFlag` (default true); 409 if the pair exists |
| `PUT /functions/update` | `roleFunctionId`; `read`/`write`/`delete`/`activeFlag` (null unchanged) |
| `DELETE /functions/delete/{roleFunctionId}` | just that row |

`write` or `delete` always also grants `read` (`NexHomeRoleFunctionMapper`).

### Functions (`NexHomeFunctionController`, `/api/functions`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `functionModule`, `functionCode` (unique), `functionName` (unique), `activeFlag` (default true); 409 on duplicates |
| `PUT /update` | `functionId`; updatable: `functionName` (still unique), `activeFlag` |
| `DELETE /delete/{functionId}` | cascades: every role function granting it, then the function |

### Deletes

All deletes are hard deletes. Cascades are done in the service, children first, using bulk `@Modifying(flushAutomatically = true, clearAutomatically = true)` JPQL `deleteAllBy...Id` repository methods, then `deleteById` for the parent. **Deleting a role deletes all of its users.** `service/NexHomeDeleteCascadeTest` verifies exactly the related rows go and everything else stays.

## Data model (`model` package)

| Table | Entity | Key columns / constraints |
|---|---|---|
| `nexhome_users` | `NexHomeUser` | `user_id`; `phone` (varchar 10); `password` (BCrypt hash, **null until set**); `email`; `role_id` → roles; `user_status`; `active_flag` (default **false**); `last_login` |
| `nexhome_user_details` | `NexHomeUserDetails` | `user_details_id`; `user_id` (unique) → users; `first_name` (NOT NULL), `last_name`, `full_name` |
| `nexhome_user_addresses` | `NexHomeUserAddress` | `user_address_id`; `user_id` → users; `address1`, `address2`, `city`, `district`, `state`, `pin_code` (all NOT NULL); `address3` (nullable); `active_flag` |
| `nexhome_roles` | `NexHomeRole` | `role_id`; `role_name` (unique); `description`; `active_flag` |
| `nexhome_functions` | `NexHomeFunction` | `function_id`; `function_module`; `function_code` (unique); `function_name` (unique); `active_flag` |
| `nexhome_role_functions` | `NexHomeRoleFunction` | `role_function_id`; `role_id` → roles; `function_id` → functions; `read`, `write`, `delete`; `active_flag`; unique (`role_id`, `function_id`) |

- Each user has exactly one role, through `role_id`; there is no user-role join table. There is no username.
- `NexHomeUserDetails.addresses` is a lazy `@OneToMany` joined on `user_id` that is **read-only**: `insertable = false, updatable = false`, no cascade, and `@ForeignKey(NO_CONSTRAINT)` so Hibernate does not add an address → details foreign key. Saving details never touches addresses.
- **One active address per user** is enforced only in the database, by the partial unique index `ux_nexhome_user_addresses_one_active (user_id) WHERE active_flag` (plus `ix_nexhome_user_addresses_user_id`). JPA cannot express it.

Conventions every entity follows; keep them for new entities:

- The audit columns `creation_date`, `created_by`, `last_update_date`, `last_update_by` and the spare columns `attribute1`–`attribute5` are declared **directly in each entity**, after the business columns. Do not extract them into a `@MappedSuperclass` / `@Embeddable`; this was tried and rejected.
- `creation_date` / `last_update_date` come from `@CreationTimestamp` / `@UpdateTimestamp`, which fire on entity insert/update only, not on bulk JPQL. `@UpdateTimestamp` is set at flush, after the response DTO is built.
- Primary keys are `<entity>_id` with `IDENTITY`. Associations are `LAZY` with explicit `@JoinColumn(name = ..., referencedColumnName = ...)`. Use Lombok `@Getter @Setter @NoArgsConstructor` only, never `@Data`.
- **Schema changes are partly manual.** Hibernate does not follow field order (it creates PK first, then alphabetical) and `ddl-auto=update` only appends columns, so all `nexhome_*` tables were created or rebuilt from hand-written DDL in model order. Partial indexes, check constraints and NOT NULL changes were also applied by hand. When adding or changing columns, write the DDL yourself (in model order) and keep the entity in sync; prod `validate` relies on it.

## Repositories

- Derived query names follow **Java field paths**, not column names. On an entity that holds an association, the id is reached through it, e.g. `findByRoleRoleIdAndFunctionFunctionId` or `findByUserUserId`. `findByUserId` on `NexHomeUserDetails` would fail at startup, because its field is `user`.
- `findByUserId` / `findByRoleId` / `findByFunctionId` on the owning entities are equivalent to `findById`.
- Return `Optional` only when the lookup is unique (by constraint or business rule), otherwise `List`.
- Bulk deletes use `@Query` with `@Param` and `@Modifying(flushAutomatically = true, clearAutomatically = true)`.

## Known gaps

- **No authentication or authorization:** every endpoint is open. That includes role and user deletes, status approval, and setting another user's password.
- `DEFAULT_LOGGED_IN_USER_ID = 1L` stands in for the acting user.
- No migration tool; schema changes are hand-written DDL (see above).
- `NexHomeUserPasswordSetRequest` and `NexHomeUserPasswordUpdateRequest` no longer override `toString()`, so logging either request would print plain passwords.
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
