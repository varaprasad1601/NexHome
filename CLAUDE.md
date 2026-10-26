# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

NexHome is a multi-client home-services marketplace in early development. Each client lives in its own self-contained sub-project with its own build tooling; there is no root-level build.

- `NexHome_App_Structure.pdf`: the target database and application design (lookups plus 9 modules, ~58 tables). The backend is being restructured to it module by module. **Done: lookups, Module 1 (identity, OTP auth, access control), Module 2 (profiles, addresses, partner onboarding) and Module 3 (service catalogue, pricing, FAQs, policies).** Next: Module 4 (partner skills). Its `partner_skills` table overlaps with Module 2's `partner_services` (both partner × service): decide whether skills extend or replace it.
- `backend/nexhome/`: Spring Boot 4.1 REST API (Java 21, Maven wrapper, Spring Data JPA + PostgreSQL, Flyway, Lombok, Bean Validation, Spring Security with OAuth2 resource server for JWTs). Base package `com.nexhome.app`. All implemented features live here.
- `web/nexhome-web-app/`: React 19 + Vite 8 SPA (plain JavaScript/JSX). Still the default Vite template.
- `android/`: empty placeholder for the future Partner and Customer apps.

Run commands from inside the relevant sub-project directory.

## Backend (`backend/nexhome`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run (use mvnw.cmd on Windows cmd/PowerShell)
SPRING_PROFILES_ACTIVE=dev ./mvnw clean test              # all tests (six classes need the dev DB)
./mvnw test -Dtest=NexHomeUserServiceImplTest             # one class; Mockito unit tests need no DB
./mvnw test -Dtest=NexHomeAuthServiceImplTest#wrongOtpCountsAttemptsAndLocks   # one method
./mvnw package                                            # build target/nexhome-0.0.1-SNAPSHOT.jar
```

Use `clean` after deleting test classes: surefire otherwise runs stale `.class` files left in `target/test-classes`.

### Configuration

- `application.properties` reads the datasource from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, and `DATA_ENCRYPTION_KEY` (below). These come from `.env` (see `.env.example`; gitignored), but the import (`spring.config.import=optional:file:.env[.properties]`) is in `application-dev.properties`, so it **only happens under the `dev` profile**. Without `dev`, startup fails with `'url' must start with "jdbc"`. The path is relative to the working directory, so run from `backend/nexhome/`.
- No profile is active by default. `ActiveProfileLogger` logs the active profile and warns when none is set.
  - `dev`: loads `.env`, `ddl-auto=validate`, SQL logging, `com.nexhome` at DEBUG, `nexhome.otp.log-plaintext=true` (generated OTPs are logged as `OTP <id> for +91 <number> (<purpose>): <otp>` because no SMS provider exists yet), and CORS for every local port.
  - `prod`: `ddl-auto=validate`, error stacktraces/messages hidden.
- **Flyway owns the schema** (`src/main/resources/db/migration`). Hibernate only validates, in every profile. Flyway refuses to start on a non-empty schema without its history table. Migrations so far:
  - `V1__lookups_and_identity`: lookup and Module 1 tables; seeds every lookup type/value of PDF section 3, the 5 roles, and system user 1.
  - `V2__auth_refresh_tokens_and_admin_functions`: session table; functions `LOOKUP_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE`, `FUNCTION_MANAGE` with ADMIN / SUB_ADMIN grants.
  - `V3__bootstrap_super_user`: the first real SUPER_USER, `+91 8897182017`.
  - `V4__auth_session_ids`: rebuilds the session table with `session_id` (logged everyone out once).
  - `V5__staff_manage_function`: `STAFF_MANAGE`, read granted to ADMIN.
  - `V6__profiles_addresses_partner_onboarding`: Module 2 tables; the `nexhome_public_id(prefix, n)` function and one sequence per table for public ids; seven lookup types the PDF names but section 3 does not seed (PREFERRED_CONTACT_LANGUAGE, SKILL_CATEGORY, PREFERRED_ARRANGEMENT, PAYMENT_BASIS, WEEKEND_AVAILABILITY, EMERGENCY_CONTACT_RELATIONSHIP, and AVAILABILITY_SLOT, which is not in the PDF: the five daily slots of the weekly grid, with start/end times in `metadata`); functions `PROFILE_MANAGE`, `PARTNER_ONBOARDING_MANAGE`; ADMIN gets `STAFF_MANAGE` write.
  - `V7__service_catalogue_and_policies`: Module 3 tables; Module 2's `partner_services` and the foreign key of `partner_profiles.main_service_category_id` (now also required by `ck_nexhome_partner_profiles_submitted`); lookup REQUIREMENT_TYPE (not in the PDF; it suggests a lookup); functions `CATALOG_MANAGE`, `FAQ_MANAGE`, `POLICY_MANAGE`.
- **Trying out a migration:** validate a new migration in a throwaway schema before it reaches the dev schema (which cannot take an edited migration): `DB_URL="jdbc:postgresql://localhost:5432/nexhome?currentSchema=scratch" SPRING_FLYWAY_SCHEMAS=scratch SPRING_PROFILES_ACTIVE=dev ./mvnw test -Dtest=NexhomeApplicationTests`, then `DROP SCHEMA scratch CASCADE`. The OS environment beats the `.env` import.
- `nexhome.otp.*` (length, expiry-minutes, max-attempts, resend-cooldown-seconds, log-plaintext) binds to `config/NexHomeOtpProperties` (`@ConfigurationPropertiesScan` on `NexhomeApplication`).
- `nexhome.security.*` binds to `config/NexHomeSecurityProperties`: `jwt-secret` from **`JWT_SECRET`** (Base64, ≥ 32 bytes; startup fails otherwise, so `.env` must define it), `jwt-issuer`, `access-token-minutes` (15), `refresh-token-days` (30), `cors-allowed-origins` (`CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`), `data-encryption-key` from **`DATA_ENCRYPTION_KEY`** (Base64 of **exactly** 32 bytes, `openssl rand -base64 32`; startup fails otherwise). It encrypts the `*_encrypted` columns; changing it makes stored Aadhaar, document and bank account numbers unreadable.
- `spring.jpa.open-in-view=false`: lazy associations must be read inside `@Transactional` service methods.
- Devtools is on the classpath. The developer usually runs the app from IntelliJ on port 8080, and it restarts whenever `target/classes` changes, including after `./mvnw compile`. For ad-hoc runs use another port: `-Dspring-boot.run.arguments=--server.port=8090`. During a restart, requests briefly get Spring Boot's default error JSON; that is not the exception handler failing.

### Tests

| Test | Kind |
|---|---|
| `service/NexHome{Lookup,User,Role,Function,Auth,Profile,Address,PartnerOnboarding,Catalog,Faq,Policy}ServiceImplTest` | Mockito unit tests of service rules, plus `Validator`-based checks of DTO constraints. No DB. Mock `NexHomePermissionService.currentUserId()` for the acting user. |
| `controller/NexHomeAuthFlowTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` against the dev DB, rolled back: the real security filters, sign-up, OTP login, refresh rotation and reuse detection, logout / logout-all / blocking ending access tokens at once, `@PreAuthorize` checks of every user list (who gets 200 vs 403), paging and filter errors, and the 401/403 JSON bodies. It creates its own users (`newUser(roleCode)`) instead of relying on seeded accounts, sets a known OTP hash with a native update, and for users that cannot receive an OTP `accessToken(userId)` stores a session row and mints a JWT with its `sid` using the app's `JwtEncoder`. |
| `NexhomeApplicationTests` | context load against the dev DB: runs Flyway, `ddl-auto=validate` checks every entity against the real schema, and every Spring Data derived query name is validated. |
| `service/NexHomeIdentitySchemaTest` | `@SpringBootTest` + `@ActiveProfiles("dev")` + `@Transactional` + `@WithMockUser("1")` against the **dev database**, rolled back. Proves what mocks cannot: seed data, the partial unique indexes (one default lookup value, one pending OTP, case-insensitive email), the composite user-role key and the JSONB column. |
| `controller/NexHomeProfileFlowTest` | Module 2 end to end like `NexHomeAuthFlowTest` (same `newUser` / `accessToken` helpers): customer and staff profiles, addresses and default switching, the partner lifecycle (draft, submit checks, review transitions, availability), documents and bank accounts, who gets 403, emergency-contact hiding, and that only ciphertext is stored. |
| `controller/NexHomeCatalogFlowTest` | Module 3 end to end: anonymous versus CATALOG_MANAGE views of the catalogue (inactive entries, commission), pricing rules and quotes for each pricing type, FAQ visibility, policy versions (publish, future versions, current version, acceptance with the client IP, pending list per role), and partner services with the main category. Slugs and policy keys get a random suffix so existing data never collides. |
| `service/NexHomeProfileSchemaTest` | dev DB, rolled back: generated public ids, one default address and one primary bank account (partial indexes), `ck_nexhome_partner_profiles_submitted`, and the V6 seed. |
| `config/NexHomeFieldEncryptorTest` | AES-GCM round trip, random IV, wrong key / tampered value, key size checks, masking. No DB. |

## Architecture and conventions

Layered packages under `com.nexhome.app` (keep this layout; do not switch to the PDF's feature packages):

| Package | Contents |
|---|---|
| `controller` | thin `@RestController`s: `@Valid @RequestBody`, then delegate |
| `service` | interface `NexHome<X>Service` + `NexHome<X>ServiceImpl` (`@Service`), **both directly in `service`**, with no `impl` subpackage. Owns `@Transactional` business logic and returns DTOs only, never entities. `NexHomePermissionService` (bean name `permissions`) is the one service without a controller: current user and permission checks. |
| `repository` | one `JpaRepository` per entity (`NexHomeUserRepository` also `JpaSpecificationExecutor` for filtered lists) |
| `model` | JPA entities, `NexHomeUserRoleId` (embedded key), `NexHomeLookupCodes` (lookup codes used by code), `NexHomeAccess` (READ/WRITE/DELETE), role code constants on `NexHomeRole`, and the non-entity `ExceptionBody` |
| shared helpers | `service/NexHomePaging` (page/size rules, newest-first `PageRequest`, `NexHomePageResponse`, search text), `mapper/NexHomeText` (`trimToNull`, `collapseWhitespace` for names, `upperCase` for codes), `repository/NexHomeSearch` (escaped LIKE patterns, name / public id search) |
| `dto` | request/response **records**: `NexHome<X>CreateRequest` / `...UpdateRequest` / `...Response`, plus action requests (`NexHomeUserStatusUpdateRequest`, `NexHomeUserRoleAssignRequest`, `NexHomeAuth{Register,OtpVerify,PhoneChange,RefreshToken}Request`, ...) and the generic `NexHomePageResponse<T>`. Requests and responses holding OTPs or tokens override `toString()` to mask them. |
| `mapper` | hand-written `@Component` mappers, one per entity: `to<X>Entity`, `update<X>Entity`, `to<X>Dto` |
| `exception` | `GlobalExceptionHandler` + `DuplicateResourceException` (409), `InvalidRequestException` (400), `ResourceNotFoundException` (404), `TooManyRequestsException` (429), `UnauthorizedException` (401) |
| `config` | `NexHomeSecurityConfig` (filter chain, JWT encoder/decoder, CORS), `NexHomeSessionTokenValidator` (rejects access tokens of ended sessions), `NexHomeSecurityErrorHandler` (401/403 bodies from the filters), `NexHomeSecurityProperties`, `PasswordEncoderConfig` (BCrypt bean, used for OTP hashes), `NexHomeFieldEncryptor` (AES-256-GCM for `*_encrypted` columns, `mask()`), `NexHomeOtpProperties`, `ActiveProfileLogger` |

- **Naming:** every domain class uses the `NexHome` prefix (capital H). The entry point is still `NexhomeApplication`. Tables are `nexhome_<pdf table name>`; columns follow the PDF exactly (`id` primary keys, `updated_date` / `updated_by`, `can_read`, `is_active`, ...). Entity fields mirror the columns (`id`, `updatedBy`); `is_*` boolean columns become fields without the prefix (`systemManaged`, `defaultValue`, `active`) so Lombok getters read well. DTOs keep descriptive ids (`userId`, `roleId`, `lookupTypeId`, ...) and expose `is*` names as in the PDF (`isActive`, `isDefault`, `isSystemManaged`).
- **Group by feature, not entity:** one controller and service per feature.
  - **Lookups:** types and values (`NexHomeLookupController`, `/api/lookups`).
  - **Auth:** sign-up, OTP login, sessions and tokens, logout, phone change (`NexHomeAuthController`, `/api/auth`).
  - **Users:** users, user lists by role, account status and role assignment (`NexHomeUserController`, `/api/users`).
  - **Roles:** roles with their role functions (`NexHomeRoleController`, `/api/roles`).
  - **Functions:** `NexHomeFunctionController`, `/api/functions`.
  - **Profiles:** customer and staff (admin) profiles (`NexHomeProfileController`, `/api/profiles`).
  - **Addresses:** customer service addresses (`NexHomeAddressController`, `/api/addresses`).
  - **Partners:** partner onboarding: profile, review, services offered, documents, bank accounts (`NexHomePartnerController`, `/api/partners`; service `NexHomePartnerOnboardingService`, named after the PDF's PartnerOnboardingService because the `nexhome_partner_services` entity is `NexHomePartnerService`).
  - **Catalogue:** categories, services and their variants, media and requirements, and price quotes (`NexHomeCatalogController`, `/api/catalog`). The entity of `nexhome_services` is `NexHomeService`.
  - **FAQs:** `NexHomeFaqController`, `/api/faqs`.
  - **Policies:** versioned policy documents and acceptances (`NexHomePolicyController`, `/api/policies`).

  Never add a separate controller or service for a sub-entity.
- **URL style:** verb paths, not REST verbs on ids, e.g. `POST .../create` or `.../add`, `PUT .../update` (id in the body), `DELETE .../delete/{id}`, `GET` for reads. Creates return 201, updates 200, deletes 204. A literal `GET` path (e.g. `/api/users/customers`) must not collide with a `/{id}` one; Spring prefers the literal.
- **List endpoints:** paged with optional query params `page` (0-based, default 0) and `size` (1–100, **default 10**), validated in the service (400 `Page must be 0 or more.` / `Size must be between 1 and 100.`), plus endpoint-specific filters. Return `NexHomePageResponse<T>` `{content, page, size, totalElements, totalPages}`, newest first (`creationDate` desc, `id` desc). Load each page's associations in one batch query (e.g. `findWithRoleByUserIdIn`), never one query per row. Build optional filters with a `Specification` (see Repositories).
- **Lookup values in business columns:** stored as the raw `value_code` string with **no foreign key** to `nexhome_lookup_values`. Services validate submitted codes with `NexHomeLookupService.validateCode(typeCode, code, "<Field>")`, which returns the trimmed code or throws 400 `"<Field> must be one of: A, B, C."`. Codes that code depends on are constants in `model/NexHomeLookupCodes`.
- **Updates rely on dirty checking:** services load the entity inside `@Transactional`, mutate it, and let the commit write it. They don't call `save()`; only creates `save()` new entities. `saveAndFlush` is used only to force statement ordering ahead of a partial unique index (see lookup defaults and OTPs) or to populate `@CreationTimestamp` on the non-IDENTITY `NexHomeUserRole` for the response.
- **Partial update semantics:**
  - A null or missing field keeps its current value. Numbers therefore cannot be cleared once set.
  - Required text fields cannot be blanked: `@Pattern(regexp = "(?s).*\\S.*", message = "<Field> cannot be blank")` rejects blanks but allows null.
  - Optional text fields differ:
    - `email` and `profilePhotoUrl` (user) and `metadata` (lookup value) are null-checked: missing keeps the value, `""` clears it.
    - every `description` (role, function, lookup type, lookup value) has **no null check** and is always replaced: missing or `""` clears it. Module 3 descriptions (category, service, variant) are null-checked instead (long texts an admin should not have to resend), except that a variant's `priceOverride` and `durationMinutes` are always replaced, so leaving them out makes the service's price and duration apply again.
    - Module 2's optional text fields (label, address line 2, landmark, middle name, employee code, ...) are null-checked like `email`; lists (`serviceAreas`, `additionalSkills`) and the `weeklyAvailability` map are replaced when sent, `[]` / `{}` clears them.
  - **Validate before mutating:** run every check that queries (uniqueness, lookup codes) before applying the update to the managed entity. A query auto-flushes dirty entities, so a half-applied change would hit a unique index or check constraint first and come back as the generic 409.
- **Codes:** role `code`, lookup `typeCode`, `functionModule` and `functionCode` must be `^[A-Z][A-Z0-9_]*$`. Lookup `valueCode` also allows lower case, dots and hyphens (language codes like `te`).
- **Immutable identity fields:** once created these never change.
  - **Which:** role `code`; function `functionModule`/`functionCode`; lookup type `typeCode`/`isSystemManaged`; lookup value `lookupTypeId`/`valueCode`; role function `roleId`/`functionId`; user-role `userId`/`roleId` (the primary key); every profile's and address's `userId` and `publicId`; a document's partner and `documentType`; a bank account's partner, account number and IFSC. User `countryCode`/`mobileNumber` change **only** through a verified PHONE_CHANGE OTP.
  - **Enforced twice:**
    1. The update DTO declares the field only with `@Null(message = "<Field> cannot be updated")`, so a client sending it gets 400 instead of being silently ignored (Jackson ignores unknown properties).
    2. The entity column has `updatable = false`, so Hibernate never writes it even if code calls the setter (not for the mobile number, which the OTP flow updates).
  - Records are located by their own id, never by an immutable field.
- **Names** (profile first/middle/last names) accept letters of any script plus spaces, dots, apostrophes and hyphens (`^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$`) and are stored whitespace-collapsed. Postal codes are 3–12 characters, and a 6-digit PIN (`^[1-9][0-9]{5}$`) when the ISO `countryCode` is `IN` (the default). Profile/address `countryCode` is the ISO country (`IN`), not the phone prefix (`+91`).
- **Sensitive numbers** (Aadhaar, document and bank account numbers) are encrypted by the mapper with `NexHomeFieldEncryptor` (`v1:` + Base64 of IV and ciphertext; random IV, so equal numbers never match in SQL), returned only masked (`XXXXXXXX0123`) or as `account_number_last4`, and masked in the request records' `toString()`.
- **Public ids** (`NHCUST0001`, `NHADMI0001`, `NHPART0001`, `NHADDR0001`, `NHCATG0001`, `NHSERV0001`, `NHVARI0001`) are column defaults: entities map `public_id` with `@Generated` and `insertable = false, updatable = false`, so Hibernate reads it back after the insert.
- **Validation messages** must be full, capitalised sentences naming the field, e.g. `@Pattern(..., message = "Mobile number must be 6 to 15 digits")`. The error handler returns messages without field-name prefixes, so a default message like `must not be blank` would not say which field failed.
- **Response dates** must look like `Thu, 08 Oct 2026 14:29:20 IST`. This is done per field, deliberately with no global Jackson config. Every `Instant` / `ZonedDateTime` in a response DTO (and `ExceptionBody.timestamp`) carries:
  ```java
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
          timezone = "Asia/Kolkata")
  ```
  Without it the field comes out as ISO UTC. Entities store `Instant` (UTC, `TIMESTAMPTZ`). Jackson is version 3 (`tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation`).
- **Acting user:** services take it from `NexHomePermissionService.currentUserId()` (the access token's subject) for `created_by` / `updated_by` / `assigned_by` / `status_updated_by`. Public flows (register, OTP, refresh, logout) record the user they act on. User 1 is the **system account** seeded by V1 (`+91 0000000000`, SUPER_USER) that owns seed data; it cannot receive an OTP. Nothing may rely on user 1 holding SUPER_USER (in the dev DB its role was removed); tests create their own users. The first real super user (`+91 8897182017`) is seeded by `V3__bootstrap_super_user.sql`; further admins are created through the API.
- **No hard deletes of referenced data:** users are never deleted (set `accountStatus` to `DEACTIVATED`); lookups have no delete endpoints. Roles and functions can be deleted only when unused (409 `... deactivate it instead.`), matching the `ON DELETE RESTRICT` foreign keys. Role functions can be deleted.

### Security

- **Authentication:** stateless. `POST /api/auth/otp/verify` (LOGIN or REGISTRATION) returns an **access token** (HS256 JWT, 15 min; `sub` = user id, `sid` = session id, `iss`, `jti`, informational `roles`) and a **refresh token** (32 random bytes, 30 days, stored only as SHA-256 in `nexhome_auth_refresh_tokens`). Clients send `Authorization: Bearer <accessToken>`.
- **Public endpoints** (`NexHomeSecurityConfig`): `POST /api/auth/{register, otp/request, otp/verify, token/refresh, logout}`, `GET /api/lookups/values/*`, and the `PUBLIC_GET_PATHS`: catalogue browsing (`/api/catalog/categories`, `/api/catalog/services`, `/services/*`, `/services/slug/*`), published FAQs (`/api/faqs`) and current policies (`/api/policies/current`, `/current/*`). Everything else needs a token. An invalid token is rejected even on public paths. Public endpoints that show staff more (inactive entries, commission) call `permissions.canIfSignedIn(...)`, which is `can` but false (not 401) for anonymous callers and does not log denials.
- **Login is per account, not per role:** a session carries every active role of the account, and permissions are the union of what they grant. There is no "log in as role X".
- **Authorization:** every admin endpoint has `@PreAuthorize("@permissions.can('<FUNCTION_CODE>', 'READ|WRITE|DELETE')")`. The `permissions` bean (`NexHomePermissionServiceImpl`) reads the database on every call: the caller's account must be ACTIVE; SUPER_USER always passes; otherwise an active user role → active role → active role function → active function must grant the flag. Revoking a role or blocking an account therefore takes effect immediately, despite unexpired tokens. `@permissions.isSelfOrCan(#userId, ...)` lets users read and update their own account. `@permissions.isCurrentUserSuperUser()` is for endpoints only super users may ever use.
- **Seeded functions** (V2, V5, V6, V7): `LOOKUP_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE`, `FUNCTION_MANAGE`, `STAFF_MANAGE` (admin lists, staff profiles), `PROFILE_MANAGE` (customer profiles and addresses), `PARTNER_ONBOARDING_MANAGE` (partner profiles, review, services, documents, bank accounts), `CATALOG_MANAGE` (catalogue changes; read: inactive entries and commission), `FAQ_MANAGE`, `POLICY_MANAGE` (versions; read: drafts and other users' acceptances). ADMIN: lookups, users and staff read/write, everything of Modules 2 and 3 read/write/delete, roles and functions read only (so admins cannot grant themselves permissions). SUB_ADMIN: read on everything except staff, roles and functions.
- **Owner checks:** Module 2 records belong to a user. Endpoints whose body carries `userId` use `@permissions.isSelfOrCan(#request.userId(), ...)`; endpoints taking a record id load it and call `permissionService.isSelfOrCan(ownerId, function, access)` in the service, throwing `AccessDeniedException` (so an unknown id is 404 before 403). Owners never act on their own review or verification (403). **New admin endpoints need a function**: add it (and its role functions) in a migration and guard the controller method.
- **Escalation guards** (`NexHomeUserServiceImpl`): only a SUPER_USER can grant, activate or withdraw the SUPER_USER role or change a super user's status; nobody can change their own status. Likewise only a super user changes another super user's staff profile (`NexHomeProfileServiceImpl`).
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

`backend/nexhome/api-docs.html` is an interactive reference of every endpoint with working example JSON; it saves the tokens from Verify OTP / Refresh and sends them, and has inputs for path and query parameters. Its Server field defaults to `http://localhost:8080` (open the page through IntelliJ, port 63342, not as a file). Update its `API` and `ERRORS` arrays whenever an endpoint, field or message changes; params use `R`/`O`/`N`/`P`/`Q` (required, optional, not allowed, path, query); 401 and 403 rows are added from each endpoint's `access`. The "Who can access" box on each endpoint is derived from `access` too, using the `GRANTS` map (seeded role functions), `OWNERS` (who "self"/"owner" is, by URL) and `WHO_NOTES` (extra rules per endpoint id): update `GRANTS` whenever a migration changes role functions.

### Profiles (`NexHomeProfileController`, `/api/profiles`)

| Endpoint | Body / rules |
|---|---|
| `POST /customers/create` (self or PROFILE_MANAGE write) | `userId` (must hold CUSTOMER: 400), `firstName`, `lastName`, `dateOfBirth` (past), `preferredLanguage` (PREFERRED_CONTACT_LANGUAGE), `marketingConsent`; one per account (409) |
| `PUT /customers/update` (owner or PROFILE_MANAGE write) | `customerProfileId`; same fields, partial; `""` clears the language |
| `GET /customers/me`, `GET /customers/{customerProfileId}` (owner or PROFILE_MANAGE read), `GET /customers` (PROFILE_MANAGE read; `page`, `size`, `search` on names / public id) | |
| `POST /admins/create` (self or STAFF_MANAGE write) | `userId` (must hold SUPER_USER, ADMIN or SUB_ADMIN), names, `employeeCode` (upper-cased, unique: 409), `department`, `jobTitle`, registered address (`addressLine1`, `city`, `state`, `postalCode` required; `countryCode` default IN). Self may create without employee details; those need STAFF_MANAGE write. |
| `PUT /admins/update` (owner or STAFF_MANAGE write) | `adminProfileId`; owners change names and address; employee details need STAFF_MANAGE write; another super user's profile needs a super user |
| `GET /admins/me`, `GET /admins/{adminProfileId}` (owner or STAFF_MANAGE read), `GET /admins` (STAFF_MANAGE read; `search` also matches the employee code) | |

### Addresses (`NexHomeAddressController`, `/api/addresses`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` (self or PROFILE_MANAGE write) | `userId` (must hold CUSTOMER), `contactName`, `mobileNumber`, `addressType` (ADDRESS_TYPE), `label`, address fields, `latitude`+`longitude` (pair, rounded to 7 decimals), `isDefault` (missing: default only if the customer has none) |
| `PUT /update` (owner or PROFILE_MANAGE write) | `addressId`; partial; `isDefault: true` moves the default, `false` unsets it |
| `DELETE /delete/{addressId}` (owner or PROFILE_MANAGE delete) | hard delete (nothing references addresses yet) |
| `GET /me`, `GET /users/{userId}` (self or PROFILE_MANAGE read), `GET /{addressId}` | default first, then newest |

**One default per customer:** like lookup defaults, the current default is cleared with `saveAndFlush` first (`ux_nexhome_addresses_one_default`). Bank accounts do the same for `is_primary`.

### Partners (`NexHomePartnerController`, `/api/partners`)

| Endpoint | Body / rules |
|---|---|
| `POST /profiles/create` (self or PARTNER_ONBOARDING_MANAGE write) | `userId` (must hold PARTNER), `firstName`, `lastName` required; any other profile field optional. Starts DRAFT / PENDING / OFFLINE; `registeredBy` = acting user; `registrationDate` today unless staff send one (partners: 403). |
| `PUT /profiles/update` (owner while DRAFT or PROFILE_INCOMPLETE, else staff) | `partnerProfileId`; partial (see `NexHomePartnerProfileUpdateRequest`); a non-DRAFT profile must stay complete |
| `PUT /profiles/submit` (owner or staff write) | DRAFT / PROFILE_INCOMPLETE → UNDER_REVIEW (verification IN_REVIEW). Needs every field the PDF marks NN (message lists the missing ones), ≥ 1 service area and slot, `isAdult: true`, and `weekendAvailability` consistent with the grid (SATURDAY: Saturday slots, no Sunday; SUNDAY: reverse; NEITHER: none; VARIES: any). |
| `PUT /profiles/review` (PARTNER_ONBOARDING_MANAGE write, not own) | `onboardingStatus`, `rejectionReason`. Transitions in `REVIEW_TRANSITIONS`: UNDER_REVIEW → APPROVED / REJECTED / PROFILE_INCOMPLETE / TRAINING_REQUIRED; TRAINING_REQUIRED → APPROVED / REJECTED / PROFILE_INCOMPLETE; APPROVED ↔ SUSPENDED; REJECTED → PROFILE_INCOMPLETE. Reason required for REJECTED, PROFILE_INCOMPLETE, SUSPENDED. APPROVED needs `isAdult`, sets `approvedBy/At`, verification VERIFIED; every other target sets OFFLINE. |
| `PUT /profiles/availability/update` (owner or staff write) | ONLINE / OFFLINE / BUSY, APPROVED profiles only |
| `GET /profiles/me`, `GET /profiles/{id}` (owner or read), `GET /profiles` (read; `page`, `size`, `onboardingStatus`, `verificationStatus`, `availabilityStatus`, `city`, `search`) | the emergency contact is null for anyone but the partner until `emergencyContactPermission` is true |
| `GET /profiles/documents/{partnerProfileId}`, `GET /profiles/bank-accounts/{partnerProfileId}` (owner or read) | |
| `POST /documents/add`, `PUT /documents/update` (owner or write) | `documentType` (DOCUMENT_TYPE, fixed), `documentNumber` (encrypted, masked), `fileStorageKey`, `expiryDate` (not past). A VERIFIED document cannot be updated or deleted; an update resets it to PENDING. |
| `PUT /documents/verify` (write, not own) | IN_REVIEW / VERIFIED / REJECTED (reason required) / EXPIRED; not the current one; an expired document cannot be VERIFIED; decisions record `verifiedBy/At` |
| `DELETE /documents/delete/{id}` (owner or delete) | |
| `POST /bank-accounts/add`, `PUT /bank-accounts/update` (owner or write) | `accountHolderName`, `accountNumber` (9–18 digits, encrypted, never returned), `ifscCode` (upper-cased), `bankName`, `isPrimary` (first account is primary). Same number + IFSC twice: 409 (found by decrypting the partner's accounts). Number and IFSC are fixed; a new holder name resets verification to PENDING. |
| `PUT /bank-accounts/verify` (write, not own), `DELETE /bank-accounts/delete/{id}` (owner or delete) | |

- **Drafts vs NOT NULL:** the PDF marks most partner columns NN but asks for draft saves. Those columns are nullable, and `ck_nexhome_partner_profiles_submitted` requires them whenever `onboarding_status <> 'DRAFT'`; the service's `requireReadyForReview` gives the readable 400 first. Keep the two lists in sync.
- **JSONB fields:** `service_areas` and `additional_skills` are arrays of strings, trimmed and de-duplicated case-insensitively; `weekly_availability` is `{"MONDAY": ["MORNING", ...]}` with days in week order and AVAILABILITY_SLOT codes in slot order (normalized in `NexHomePartnerOnboardingServiceImpl`).
- `country_code` is always IN for partners (Aadhaar, PIN, IFSC). `mainServiceCategoryId` must be an active category and is required to submit.
- **Services offered** (`nexhome_partner_services`): `GET /profiles/services/{partnerProfileId}` (owner or read); `POST /services/add` (`partnerProfileId`, `serviceId` (active, once per partner: 409), `experienceYears`, `isPrimaryService`: the first one is primary), `PUT /services/update` (`partnerServiceId`; `experienceYears`, `isPrimaryService`, `isActive`), `DELETE /services/delete/{id}`. Like profile fields, partners change them only while DRAFT or PROFILE_INCOMPLETE; staff any time. One primary per partner (partial index, cleared with `saveAndFlush` first).

### Catalogue (`NexHomeCatalogController`, `/api/catalog`)

| Endpoint | Body / rules |
|---|---|
| `GET /categories` (public; `includeInactive=true` needs CATALOG_MANAGE read) | display order, then name |
| `POST /categories/create`, `PUT /categories/update` (CATALOG_MANAGE write) | `name`, `slug` (defaults to the name: accents stripped, lower case, hyphens; unique: 409), `description`, `iconUrl`, `displayOrder`, `isActive`. Renaming keeps the slug. |
| `DELETE /categories/delete/{id}` (delete) | only if no service, FAQ or partner profile uses it, else 409 |
| `GET /services` (public; `page`, `size`, `categoryId`, `pricingType`, `search`, `includeInactive`) | by name. The public sees active services of active categories; commission fields are null unless CATALOG_MANAGE read |
| `GET /services/{serviceId}`, `GET /services/slug/{slug}` (public) | `NexHomeServiceDetailResponse` {service, variants, media, requirements}; 404 and only active children for the public |
| `GET /services/quote?serviceId&serviceVariantId&minutes&quantity` (public) | unit price = variant override, else base price. FIXED / INSPECTION: once; PER_UNIT: × quantity (1–1000, default 1); HOURLY: per hour for `minutes` (default: variant or service duration; at most 10080) raised to `minimum_billable_minutes` and rounded up to `billing_increment_minutes` (`billableMinutes`). Total ≥ minimum charge. The variant must belong to the service (400). |
| `POST /services/create`, `PUT /services/update` (write) | `categoryId` (active), `name`, `slug`, descriptions, `pricingType` (PRICING_TYPE), `basePrice`, `minimumCharge`, durations, `platformCommissionType` (COMMISSION_TYPE), `platformCommissionValue`, booking flags, `isActive`. Rules (also check constraints): HOURLY needs the minimum and increment; PERCENTAGE ≤ 100; at least one booking type. |
| `DELETE /services/delete/{id}` (delete) | only without variants, media, requirements, FAQs or partner services, else 409 |
| `POST /variants/add`, `PUT /variants/update`, `DELETE /variants/delete/{id}` | `variantCode` (`^[A-Z][A-Z0-9_]*$`, unique per service, fixed), `name`, `priceOverride`, `durationMinutes`, `displayOrder`, `isActive` |
| `POST /media/add`, `PUT /media/update`, `DELETE /media/delete/{id}` | `mediaType` (MEDIA_TYPE), `storageKey`, `displayOrder`, `isActive` |
| `POST /requirements/add`, `PUT /requirements/update`, `DELETE /requirements/delete/{id}` | `requirementType` (REQUIREMENT_TYPE), `description`, `isMandatory` (default true), `displayOrder` |

### FAQs (`NexHomeFaqController`, `/api/faqs`)

| Endpoint | Body / rules |
|---|---|
| `GET /` (public) | published, display order, not of an inactive category or service; `audience` (CUSTOMER or PARTNER also return BOTH), `categoryId`, `serviceId` |
| `GET /all` (FAQ_MANAGE read) | paged, newest first; `audience` (exact), `categoryId`, `serviceId`, `isPublished`, `search` (question) |
| `POST /create`, `PUT /update`, `DELETE /delete/{faqId}` | `question`, `answer`, `categoryId` or `serviceId` (not both: 400; fixed after create), `audience` (FAQ_AUDIENCE), `displayOrder`, `isPublished` (default false) |

### Policies (`NexHomePolicyController`, `/api/policies`)

| Endpoint | Body / rules |
|---|---|
| `GET /current` (public; `audience`), `GET /current/{policyKey}` (public) | the **current version** of each key: published, `effective_from` ≤ now, latest `effective_from` (then latest `published_at`) |
| `GET /pending` (signed in) | current policies for the caller's CUSTOMER / PARTNER roles plus BOTH, not yet accepted; staff-only accounts get none |
| `POST /accept` (signed in) | `policyDocumentId` (must be the current version: 400), `acceptanceMethod` (ACCEPTANCE_METHOD); stores `request.getRemoteAddr()` as INET (literal addresses only, no DNS); once per user and version (409) |
| `GET /acceptances/me`, `GET /acceptances/users/{userId}` (self or POLICY_MANAGE read) | newest first |
| `GET /all` (POLICY_MANAGE read; `policyKey`, `audience`, `isPublished`), `GET /{id}` (read) | drafts included |
| `POST /create` (write) | `policyKey` (`^[A-Z][A-Z0-9_]*$`), `title`, `audience` (POLICY_AUDIENCE), `version`, `content`, `effectiveFrom` (ISO-8601 instant in the request, IST string in responses); unpublished; (key, version) unique: 409 |
| `PUT /update` (write), `DELETE /delete/{id}` (delete) | unpublished versions only (400); key and version fixed |
| `PUT /publish` (write) | sets `is_published` and `published_at` (check constraint keeps them together); cannot be undone; a published version never changes, a new version is a new row |

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
| `nexhome_customer_profiles` (V6) | `NexHomeCustomerProfile` | `public_id` (NHCUST, unique); `user_id` (unique); names; `date_of_birth`; `preferred_language`; `marketing_consent` |
| `nexhome_admin_profiles` (V6) | `NexHomeAdminProfile` | `public_id` (NHADMI); `user_id` (unique); names; `employee_code` (unique when present, partial index); `department`; `job_title`; registered address; `country_code` (ISO, default IN) |
| `nexhome_addresses` (V6) | `NexHomeAddress` | `public_id` (NHADDR); `user_id` (indexed); contact; `address_type`; `label`; address; `latitude`/`longitude` (NUMERIC(10,7), both or neither); `is_default` (one per user, partial index) |
| `nexhome_partner_profiles` (V6) | `NexHomePartnerProfile` | `public_id` (NHPART); `user_id` (unique); names; `aadhaar_number_encrypted`; address with `postal_code` CHAR(6) (`@JdbcTypeCode(SqlTypes.CHAR)`); `registration_date`, `registered_by`; work preferences; JSONB `additional_skills`, `service_areas`, `weekly_availability`; emergency contact; the three status codes; `approved_by/at` (check: at needs by); checks for ranges, adult-before-APPROVED and completeness after DRAFT |
| `nexhome_partner_documents` (V6) | `NexHomePartnerDocument` | `partner_id` → partner profiles (RESTRICT); `document_type`; `document_number_encrypted`; `file_storage_key`; `verification_status`; `verified_by/at`; `expiry_date`; `rejection_reason` |
| `nexhome_partner_bank_accounts` (V6) | `NexHomePartnerBankAccount` | `partner_id`; `account_holder_name`; `account_number_encrypted` + `account_number_last4`; `ifsc_code` (checked pattern); `bank_name`; `verification_status`; `gateway_beneficiary_id` (Module 7); `is_primary` (one per partner, partial index) |
| `nexhome_service_categories` (V7) | `NexHomeServiceCategory` | `public_id` (NHCATG); `name`; `slug` (unique); `description`; `icon_url`; `display_order`; `is_active` (index on order, name where active) |
| `nexhome_services` (V7) | `NexHomeService` | `public_id` (NHSERV); `category_id` → categories (RESTRICT); `name`; `slug` (unique); descriptions; `pricing_type`; `base_price`, `minimum_charge` (NUMERIC(12,2) ≥ 0); duration minutes (> 0); `platform_commission_type`/`_value`; booking flags; `is_active`; checks for hourly, percentage ≤ 100, one booking type |
| `nexhome_service_variants` (V7) | `NexHomeServiceVariant` | `public_id` (NHVARI); `service_id`; `variant_code` (unique per service); `name`; `price_override`; `duration_minutes`; `display_order`; `is_active` |
| `nexhome_service_media` (V7) | `NexHomeServiceMedia` | `service_id`; `media_type`; `storage_key`; `display_order`; `is_active` |
| `nexhome_service_requirements` (V7) | `NexHomeServiceRequirement` | `service_id`; `requirement_type`; `description`; `is_mandatory`; `display_order` |
| `nexhome_faqs` (V7) | `NexHomeFaq` | `question`; `answer`; `category_id` / `service_id` (at most one, check); `audience`; `display_order`; `is_published` |
| `nexhome_policy_documents` (V7) | `NexHomePolicyDocument` | `policy_key` + `version` (unique, fixed); `title`; `audience`; `content`; `effective_from`; `published_at` and `is_published` (both or neither) |
| `nexhome_policy_acceptances` (V7) | `NexHomePolicyAcceptance` | `user_id`, `policy_id` (unique together); `accepted_at`; `acceptance_method`; `ip_address` INET (`InetAddress` with `@JdbcTypeCode(SqlTypes.INET)`) |
| `nexhome_partner_services` (V7, Module 2) | `NexHomePartnerService` | `partner_id`, `service_id` (unique together); `experience_years` (0–60); `is_primary_service` (one per partner, partial index); `is_active`; common audit columns instead of the PDF's `created_at`/`updated_at` |
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
- `GET /api/users/{userId}` needs only USER_MANAGE read, so a SUB_ADMIN can open any single user, including admins and super users, although the admin / super-user lists are closed to them.
- No list endpoints yet for roles, functions or role functions.
- No file storage: partner documents take a `fileStorageKey` the client got elsewhere, and the key is returned as-is to the owner and staff. A storage provider with signed upload/download URLs is still to come.
- `additionalSkills` are free strings, not checked against the catalogue or (later) partner skills.
- Addresses, bank accounts, service variants and partner services are hard-deleted; once bookings (Module 6), payouts (Module 7) or partner skills (Module 4) reference them, deletion must change (409 or deactivation).
- `service_availability` and pricing rules (lookup PRICING_RULE_TYPE: CITY_PRICE, SURGE, TIME_SLOT) appear in the PDF's relationship map but have no table definition, so neither exists; quotes use only the service and variant prices. `commission_rules` comes with Module 7; until then the service's own commission is the only one.
- Service media and policy texts have no upload or rich-text handling: media take a storage key, policies plain text.
- Aadhaar numbers are format-checked only (no Verhoeff checksum), and the PDF's legal-basis / consent / retention conditions for collecting them are not modelled.
- Users have no `public_id`, although the PDF's users constraints mention one (no column is defined for it); the Module 2 profile and address tables have theirs.
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
