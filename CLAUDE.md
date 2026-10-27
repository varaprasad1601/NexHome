# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

NexHome is a multi-client home-services marketplace in early development. Each client lives in its own self-contained sub-project with its own build tooling; there is no root-level build.

- `NexHome_App_Structure.pdf`: the target database and application design (lookups plus 9 modules, ~58 tables). The backend is being restructured to it module by module. **Done: lookups, Module 1 (identity, OTP auth, access control), Module 2 (profiles, addresses, partner onboarding) and Module 3 (service catalogue, pricing, FAQs, policies).** Next: Module 4 (partner skills). Its `partner_skills` table overlaps with Module 2's `partner_services` (both partner × service): decide whether skills extend or replace it.
- `backend/nexhome/`: Spring Boot 4.1 REST API (Java 21, Maven wrapper, Spring Data JPA + PostgreSQL, Flyway, Lombok, Bean Validation, Spring Security with OAuth2 resource server for JWTs). Base package `com.nexhome.app`. All implemented features live here.
- `web/nexhome-web-app/`: React 19 + Vite 8 SPA (plain JavaScript/JSX): the public marketing site plus the admin console (`/admin` sign-in, `/admin/dashboard`).
- `Admin Sreens/` (gitignored): design screenshots for the admin screens, reference only.
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
  - `V6__profiles_addresses_partner_onboarding`: Module 2 tables; the `nexhome_public_id(prefix, n)` function and one sequence per table for public ids; seven lookup types the PDF names but section 3 does not seed (PREFERRED_CONTACT_LANGUAGE, SKILL_CATEGORY, PREFERRED_ARRANGEMENT, PAYMENT_BASIS, WEEKEND_AVAILABILITY, EMERGENCY_CONTACT_RELATIONSHIP, and AVAILABILITY_SLOT, which is not in the PDF: the five daily slots of the weekly grid, with start/end times in `metadata`); functions `PROFILE_MANAGE`, `PARTNER_ONBOARDING_MANAGE`; ADMIN gets `STAFF_MANAGE` write (both later replaced: V13, V14).
  - `V7__service_catalogue_and_policies`: Module 3 tables; Module 2's `partner_services` and the foreign key of `partner_profiles.main_service_category_id` (now also required by `ck_nexhome_partner_profiles_submitted`); lookup REQUIREMENT_TYPE (not in the PDF; it suggests a lookup); functions `CATALOG_MANAGE`, `FAQ_MANAGE`, `POLICY_MANAGE`.
  - `V8__optional_admin_profile_address`: the admin profile's registered address becomes optional (the PDF marks it NOT NULL): all four required parts or none (`ck_nexhome_admin_profiles_address`). Written for a name-only profile from Add Admin, which was then dropped (Add Admin now uses Create user and creates no profile); kept because it was already applied.
  - `V9__staff_profiles_with_every_staff_account`: admin profile `first_name` / `last_name` become optional (filled in later under My Profile), and every existing staff account (SUPER_USER / ADMIN / SUB_ADMIN, not system user 1) without a profile gets one, oldest first.
  - `V10__sub_admin_staff_read`: SUB_ADMIN gets `STAFF_MANAGE` read (the Admins and Sub Admins lists and staff profiles, read only).
  - `V11__lookups_super_user_only`: ADMIN loses `LOOKUP_MANAGE` write, so lookups are changed by super users only (roles and functions follow their own grants, see Roles); ADMIN and SUB_ADMIN keep lookup read.
  - `V12__functions_named_after_admin_menu`: the seeded functions take the console's menu names (module = sidebar group, e.g. `SYSTEM_CONFIGURATION`; name = "Manage ..."); codes unchanged; API-created functions untouched.
  - `V13__separate_admin_and_sub_admin_functions`: `ADMIN_MANAGE` (Manage Admins) and `SUB_ADMIN_MANAGE` (Manage Sub Admins) replace `STAFF_MANAGE`, each role's grant copied to both. (Applied to the dev DB by the running app before V14 was written: never edit it.)
  - `V14__customer_and_partner_functions`: `CUSTOMER_MANAGE` (Manage Customers, also customer profiles and addresses) and `PARTNER_MANAGE` (Manage Partners) replace `USER_MANAGE` and `PROFILE_MANAGE`, grants copied (a flag from either old function kept).
  - `V16__partner_profiles_with_every_partner_account`: partner profile names become optional for a DRAFT (the submitted check now requires them, and so does `requireReadyForReview`), and every partner account without a profile gets an empty DRAFT (registration date = the account's creation day in IST), oldest first. Sign-up, Create user and assigning the PARTNER role create it too (`ensurePartnerProfile`); `POST /api/partners/profiles/create` fills it in, keeping the NHPART id.
  - `V15__customer_profiles_with_every_customer_account`: customer profile names become optional, and every customer account without a profile gets an empty one (its NHCUST customer id), oldest first. From now on sign-up (`NexHomeAuthServiceImpl.register`), Create user and assigning the CUSTOMER role create it too (`ensureCustomerProfile`).
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
- **Public endpoints** (`NexHomeSecurityConfig`): `POST /api/auth/{register, otp/request, admin/otp/request, otp/verify, token/refresh, logout}`, `GET /api/lookups/values/*`, and the `PUBLIC_GET_PATHS`: catalogue browsing (`/api/catalog/categories`, `/api/catalog/services`, `/services/*`, `/services/slug/*`), published FAQs (`/api/faqs`) and current policies (`/api/policies/current`, `/current/*`). Everything else needs a token. An invalid token is rejected even on public paths. Public endpoints that show staff more (inactive entries, commission) call `permissions.canIfSignedIn(...)`, which is `can` but false (not 401) for anonymous callers and does not log denials.
- **Login is per account, not per role:** a session carries every active role of the account, and permissions are the union of what they grant. There is no "log in as role X".
- **Authorization:** every admin endpoint has `@PreAuthorize("@permissions.can('<FUNCTION_CODE>', 'READ|WRITE|DELETE')")`. The `permissions` bean (`NexHomePermissionServiceImpl`) reads the database on every call: the caller's account must be ACTIVE; SUPER_USER always passes; otherwise an active user role → active role → active role function → active function must grant the flag. Revoking a role or blocking an account therefore takes effect immediately, despite unexpired tokens. `@permissions.isSelfOrCan(#userId, ...)` lets users read and update their own account; `@permissions.isSelfOrCanManageAccount(#userId, access)` does the same against the account's role function (see Account functions). `@permissions.isCurrentUserSuperUser()` is for endpoints only super users may ever use.
- **Seeded functions** (V2 to V14): `LOOKUP_MANAGE`, `ROLE_MANAGE`, `FUNCTION_MANAGE`, the **account functions** `ADMIN_MANAGE`, `SUB_ADMIN_MANAGE`, `CUSTOMER_MANAGE` (also customer profiles and addresses), `PARTNER_MANAGE`, then `PARTNER_ONBOARDING_MANAGE` (partner profiles, review, services, documents, bank accounts), `CATALOG_MANAGE` (catalogue changes; read: inactive entries and commission), `FAQ_MANAGE`, `POLICY_MANAGE` (versions; read: drafts and other users' acceptances). As seeded, ADMIN: the account functions read/write (customers also delete), lookups read (V11), everything of Modules 2 and 3 read/write/delete, roles and functions read only. SUB_ADMIN: read on everything except roles and functions. Super users change grants under System Configuration, so the dev DB may differ (tests that rely on ADMIN's seeded grants then fail).
- **Account functions** (`NexHomePermissionService.canManageRole` / `canManageStaff` / `canManageAccount`): accounts go by their role, ADMIN through `ADMIN_MANAGE`, SUB_ADMIN `SUB_ADMIN_MANAGE`, CUSTOMER `CUSTOMER_MANAGE`, PARTNER `PARTNER_MANAGE`; SUPER_USER, roles without a function and accounts without a role are for super users only. A staff account goes by its highest staff role, any other by any of its roles. Creating an account and giving or withdrawing a role need write on the role's function; status changes, `GET /api/users/{userId}` and `PUT /api/users/update` (besides self) and staff profiles need the account's function. The staff list and counts show only the staff roles the caller may read (`readableStaffRoles`).
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
| `POST /customers/create` (self or CUSTOMER_MANAGE write) | `userId` (must hold CUSTOMER: 400), `firstName`, `lastName`, `dateOfBirth` (past), `preferredLanguage` (PREFERRED_CONTACT_LANGUAGE), `marketingConsent`. Fills in the account's empty profile (V15), keeping its customer id; a profile that already has a name is 409 |
| `PUT /customers/update` (owner or CUSTOMER_MANAGE write) | `customerProfileId`; same fields, partial; `""` clears the language |
| `GET /customers/me`, `GET /customers/{customerProfileId}` (owner or CUSTOMER_MANAGE read), `GET /customers` (CUSTOMER_MANAGE read; `page`, `size`, `search` on names / public id) | |
| `POST /admins/create` (self or write on the owner's account function) | `userId` (must hold SUPER_USER, ADMIN or SUB_ADMIN), names, `employeeCode` (upper-cased, unique: 409), `department`, `jobTitle`, registered address (`addressLine1`, `city`, `state`, `postalCode` required; `countryCode` default IN). Self may create without employee details; those need the owner's account function write too. |
| `PUT /admins/update` (owner or the owner's account function write) | `adminProfileId`; owners change names and address; employee details need the account function write (ADMIN_MANAGE / SUB_ADMIN_MANAGE); a super user's profile needs a super user |
| `GET /admins/me`, `GET /admins/{adminProfileId}` (owner or the owner's account function read), `GET /admins` (ADMIN_MANAGE and SUB_ADMIN_MANAGE read; `search` also matches the employee code) | |

### Addresses (`NexHomeAddressController`, `/api/addresses`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` (self or CUSTOMER_MANAGE write) | `userId` (must hold CUSTOMER), `contactName`, `mobileNumber`, `addressType` (ADDRESS_TYPE), `label`, address fields, `latitude`+`longitude` (pair, rounded to 7 decimals), `isDefault` (missing: default only if the customer has none) |
| `PUT /update` (owner or CUSTOMER_MANAGE write) | `addressId`; partial; `isDefault: true` moves the default, `false` unsets it |
| `DELETE /delete/{addressId}` (owner or CUSTOMER_MANAGE delete) | hard delete (nothing references addresses yet) |
| `GET /me`, `GET /users/{userId}` (self or CUSTOMER_MANAGE read), `GET /{addressId}` | default first, then newest |

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
| `POST /register` (public) | `countryCode` (`+` and 1–4 digits), `mobileNumber` (6–15 digits, unused: 409), `email`, `roleCode` (`CUSTOMER` or `PARTNER`). Creates the ACTIVE, unverified account (its own `created_by`) with its empty customer profile (NHCUST, V15) or DRAFT partner profile (NHPART, V16), and sends a REGISTRATION OTP. 201. |
| `POST /otp/request` (public) | `countryCode`, `mobileNumber`, `purpose` (`LOGIN`, or `REGISTRATION` while unverified). Account must exist (404) and be ACTIVE. 201 with the request state, never the OTP. |
| `POST /admin/otp/request` (public) | `countryCode`, `mobileNumber`. The admin sign-in: a LOGIN OTP only for an ACTIVE account currently holding SUPER_USER, ADMIN or SUB_ADMIN; an unknown number, any other account and an inactive one all get 404 `Invalid user. This mobile number does not have admin access.` (no account enumeration). Verified through `/otp/verify` with purpose LOGIN. 201. |
| `POST /otp/verify` (public) | `countryCode`, `mobileNumber`, `purpose`, `otp`. Marks the number verified, sets `last_login_at`, returns the user and `tokens`. |
| `POST /token/refresh` (public) | `refreshToken` → new `tokens` (401 if unknown, expired, revoked or account not ACTIVE) |
| `POST /logout` (public) | `refreshToken`; revokes that session; 204 (also for unknown tokens) |
| `POST /logout/all` | revokes every session of the caller; 204 |
| `POST /phone-change/otp/request` | `countryCode`, `mobileNumber` (the caller's new, unused number); 201 |
| `POST /phone-change/otp/verify` | `countryCode`, `mobileNumber`, `otp`; the OTP must be the caller's; moves the account to the number |

- **OTP rules:** only a BCrypt hash is stored; 6 digits; expires after 5 minutes; 5 wrong attempts → LOCKED; 60 s resend cooldown (429 `Please wait <n> seconds before requesting another OTP.`, the seconds left rounded up; the admin sign-in page reads them and counts down). A new OTP first expires the pending one with `saveAndFlush` (partial unique index `ux_nexhome_auth_otp_requests_one_pending`). SERVICE_START / SERVICE_COMPLETION return 400 until bookings exist; PHONE_CHANGE is rejected on the public endpoints.
- `verifyOtp` / `verifyPhoneChangeOtp` are `@Transactional(noRollbackFor = InvalidRequestException.class)` so failed attempts, expiry and locking are saved even though the request fails. Keep every other state change after `checkOtp` and the purpose checks.
- **TODO:** no SMS provider (dev logs the OTP).

### Users (`NexHomeUserController`, `/api/users`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` (write on the role's function) | staff accounts: `countryCode`, `mobileNumber` (unused: 409), `email` (optional, stored lower-cased, unique: 409), `profilePhotoUrl` (http/https), `roleId` (active role, else 404 `Role Not Found or Inactive`). Creates an ACTIVE, unverified account with that role; a SUPER_USER / ADMIN / SUB_ADMIN account also gets its empty staff profile (NHADMI public id, no names), a CUSTOMER account its empty customer profile (NHCUST) and a PARTNER account its DRAFT partner profile (NHPART), in the same transaction, so `POST /api/profiles/admins/create` then answers 409 and the names are set with `PUT /api/profiles/admins/update`. |
| `GET /me` | the caller |
| `GET /me/permissions` (any signed-in user) | `NexHomeUserPermissionsResponse` `{isSuperUser, functions: [{functionModule, functionCode, canRead, canWrite, canDelete}]}`: the union of the caller's active grants (`NexHomeRoleFunctionRepository.findActiveGrantsOfUser`, same conditions as `hasAccess`), every active function with full access for a super user, none for an account that is not ACTIVE (`NexHomePermissionService.currentUserPermissions`) |
| `GET /customers` (CUSTOMER_MANAGE read) | `NexHomeCustomerResponse`: the user fields with roles plus the customer profile's `customerProfileId`, `publicId`, names (null until the profile exists; profiles loaded in one batch query); `search` also matches "first last" and the public id (`NexHomeUserRepository.customerSpec`). `GET /customers/stats` (same access): counts by status (`NexHomeStaffStatsResponse`) |
| `GET /partners` (PARTNER_MANAGE read) | `NexHomePartnerResponse`: the user fields with roles plus the onboarding profile's id, public id, names, city, main service category (id and name) and onboarding / verification / availability statuses (null until the profile exists; profiles with their category in one batch query). Query params: `page`, `size`, `accountStatus`, `onboardingStatus` (a PARTNER_ONBOARDING_STATUS code or `NOT_STARTED`: no profile), `search` (mobile, email, "first last", public id; `NexHomeUserRepository.partnerSpec`). `GET /partners/stats` (same access): `NexHomePartnerStatsResponse` `{total, approved, awaitingReview, notSubmitted, online}` |
| `GET /staff` (ADMIN_MANAGE or SUB_ADMIN_MANAGE read; only the readable roles are listed, another `roleCode` is 403) | the admin console's staff list: current SUPER_USER / ADMIN / SUB_ADMIN holders (active assignment of an active role), newest first, with the staff profile's names and public id and the highest staff role (`NexHomeStaffResponse`); `page`, `size`, `roleCode`, `accountStatus`, `search` (mobile, email, "first last" name, public id; `NexHomeUserRepository.staffSpec`). **Super users are listed only for super users.** |
| `GET /staff/stats` (as `/staff`) | `total`, `active`, `inactive` (DEACTIVATED), `suspended`, `blocked`, with the same visibility; optional `roleCode` |
| `GET /stats` (CUSTOMER_MANAGE or PARTNER_MANAGE read; the block without read is null) | admin dashboard counts: for `customers` and `partners`, `total` current holders (active assignment of an active role), `addedThisMonth` and `months` (the last 10, oldest first: `month` `YYYY-MM`, `added`, `total` at its end), by `assigned_at` in IST. One grouped native query per role (`NexHomeUserRoleRepository.countActiveByRoleAndMonth`). A withdrawn role leaves every month's count. |
| `GET /admins` (ADMIN_MANAGE read), `GET /sub-admins` (SUB_ADMIN_MANAGE read) | same, for ADMIN / SUB_ADMIN holders |
| `GET /super-users` (`@permissions.isCurrentUserSuperUser()`: ACTIVE SUPER_USER only, not grantable through role functions) | same, for SUPER_USER holders |
| `GET /{userId}` (self or the account's function read) | one user |
| `PUT /update` (self or the account's function write) | `userId`; `email`, `profilePhotoUrl`. Mobile number, status and roles are rejected (400). |
| `PUT /status/update` (the account's function write) | `userId`, `accountStatus` (ACCOUNT_STATUS code; same status again is 400; not your own). Records `status_updated_by/date`; leaving ACTIVE revokes all sessions. |
| `POST /roles/assign` (the role's function write) | `userId`, `roleId` (active role); 409 if the user already has it |
| `PUT /roles/update` (the role's function write) | `userId`, `roleId`, `isActive`; an inactive role cannot be activated |

**User identity:** one account per `(country_code, mobile_number)` (unique constraint); a user may hold several roles through `nexhome_user_roles`. Names and addresses are **not** on users: they come with the Module 2 profile tables.

### Roles and role functions (`NexHomeRoleController`, `/api/roles`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` (ROLE_MANAGE write, like update; delete needs delete. The SUPER_USER role and its grants are changed by super users only: `requireSuperUserForSuperUserRole`) | `code` (unique: 409), `name`, `description`, `activeFlag` (default true) |
| `PUT /update` | `roleId`; `name`, `description` (always replaced), `activeFlag` |
| `DELETE /delete/{roleId}` | only if no user roles or role functions reference it, else 409 |
| `GET /` (ROLE_MANAGE read) | every role, active or not, in id order |
| `GET /{roleId}`, `GET /functions/{roleId}` (ROLE_MANAGE read) | one role; its role functions (active or not) with the function, by module and code, in one query |
| `POST /functions/add` (ROLE_MANAGE write, like `PUT /functions/update`; `DELETE /functions/delete/{id}` needs delete) | `roleId`, `functionId` (both must exist: 404), `canRead`/`canWrite`/`canDelete` (default false), `activeFlag` (default true); 409 if the pair exists |
| `PUT /functions/update` | `roleFunctionId`; `canRead`/`canWrite`/`canDelete`/`activeFlag` (null unchanged) |
| `DELETE /functions/delete/{roleFunctionId}` | just that row |

`canWrite` or `canDelete` always also grants `canRead` (`NexHomeRoleFunctionMapper`). Seeded roles: SUPER_USER, ADMIN, SUB_ADMIN, CUSTOMER, PARTNER.

### Functions (`NexHomeFunctionController`, `/api/functions`)

| Endpoint | Body / rules |
|---|---|
| `POST /create` | `functionModule`, `functionCode` (unique: 409), `functionName`, `functionType` (FUNCTION_TYPE code), `description`, `activeFlag` |
| `PUT /update` | `functionId`; `functionName`, `functionType`, `description` (always replaced), `activeFlag` |
| `DELETE /delete/{functionId}` | only if no role function grants it, else 409 |
| `GET /` (FUNCTION_MANAGE read) | every function, active or not, by module and code |

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
| `nexhome_admin_profiles` (V6) | `NexHomeAdminProfile` | `public_id` (NHADMI); `user_id` (unique); names (optional since V9: created empty with every staff account); `employee_code` (unique when present, partial index); `department`; `job_title`; registered address (optional since V8, complete when given; `POST /api/profiles/admins/create` still requires it, `PUT .../update` checks it is complete); `country_code` (ISO, default IN) |
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
- LOGIN/REGISTRATION OTP requests reveal whether a mobile number has an account (404); the admin OTP request does not distinguish unknown from non-staff numbers.
- Roles, functions and a role's role functions are listed unpaged (`GET /api/roles`, `GET /api/functions`, `GET /api/roles/functions/{roleId}`); there is no list of every role function across roles.
- No file storage: profile photos can only be previewed in the console (`PhotoUpload`), and partner documents take a `fileStorageKey` the client got elsewhere, and the key is returned as-is to the owner and staff. A storage provider with signed upload/download URLs is still to come.
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

The public NexHome marketing site (Nuzvid household services), migrated from the AI Studio export in `NexHome-Web/` (TypeScript, one 2,900-line `App.tsx`). The UI, text and styling are unchanged: the rendered DOM of every route matches the original apart from the footer links, which now point at the real pages (`/about`) instead of `#about`, and the generated CSS is byte-identical. Plain JavaScript/JSX, Tailwind CSS 4 (`@tailwindcss/vite`, `@import "tailwindcss"` in `index.css`), React Router 7, `motion` for the launch animation. Not yet connected to the backend: enquiries and registrations open WhatsApp with a prepared message, and "Register via Web" embeds a Google Form.

- `src/App.jsx`: routes under `components/layout/Layout` (header, footer, registration modal, launch overlay): `/`, `/about`, `/services`, `/services/:slug` (unknown slug shows the first service), `/how-it-works`, `/join`, `/team`, `/faqs` (and `/faq`), `/contact` (`?service=<slug or name>` preselects the service), `/policies`, `/launch` (countdown over the home page; also `#launch` / `?launch`). Any other path shows the home page.
- `pages/`: one component per route; `components/home/`: the home page sections in order; `components/common/`: `Icon` (inline SVG paths by name), `MapCard`, `PageHeader` (inner-page banner), `ServiceCard`, `AppLink`.
- **`AppLink`** wraps the router `Link` and resets the professional registration card, as every in-app navigation did in the original; `hooks/useNavigationScroll` scrolls to the top on in-app navigation and to `#section` on load or back/forward.
- **Shared state** (`context/SiteStateProvider`, `useSiteState()`): the customer enquiry form, the worker registration form and its mode, the open FAQ and the "Register via Web" modal, so they survive navigation between the home page and inner pages, as in the original.
- Content lives in `data/siteData.js` (services, steps, FAQs, contact details); `utils/whatsapp.js` builds `wa.me` links.
- `public/assets/` holds the images; `public/_redirects` and `netlify.toml` serve `index.html` for every path on Netlify.
- **Admin sign-in** (`/admin`, `pages/admin/AdminLoginPage`, outside the site `Layout`): mobile number (+91 only), then the LOGIN OTP through `POST /api/auth/admin/otp/request` (the backend sends a code only to active staff, else 404 `Invalid user. ...`, shown on the page) and `/otp/verify`. After verifying, `utils/adminSession.isStaffUser` checks the roles again (a role withdrawn in between) and logs such a session out. The user and tokens are kept in `localStorage` (`nexhome.adminSession`) by `context/AdminAuthProvider` (`useAdminAuth()`: `session`, `signIn`, `signOut`, which calls `/api/auth/logout`, and `authRequest(path, options)`); a signed-in admin is sent from `/admin` to `/admin/dashboard`. **Signed-in API calls go through `authRequest`**: on a 401 it renews the tokens once with `/api/auth/token/refresh` and retries; only one refresh runs at a time (a refresh token works once, and reusing it ends every session); if renewal fails the admin is signed out.
- **Permission-driven console:** `AdminAuthProvider` loads `GET /api/users/me/permissions` for each signed-in user and again whenever the tab becomes visible, any request gets 403 (a grant was withdrawn meanwhile) or a screen calls `reloadPermissions()` (the role permissions page does after every change, since it may be the admin's own role); `useAdminAuth()` gives `can(functionCode, 'READ' | 'WRITE' | 'DELETE')`, `isSuperUser` and `permissionsLoaded`. **Every sidebar link names its function** (`fn` in `data/adminNavigation.js`; `superUserOnly` for Super Admins): without READ the link is hidden (`canOpen`), a group with no visible link disappears, and opening the address shows No access (`AdminLayout`, using `findNavItem`). With READ the screen shows its data; **actions follow WRITE / DELETE** (Add buttons, row menus, Edit links; the Actions column goes when no action is left). Links without `fn` (Dashboard, bookings, payments, marketing, reports, general settings) are shown to all staff: those features have no functions yet. Give a new screen's link its `fn`, and gate its actions with `can()` on the functions its endpoints check (staff pages: the role's account function, `canManageStaffRole` in `data/staffOptions.js`; Roles and role permissions: ROLE_MANAGE write / delete). Role names (`session.user.roles`) are no longer used for this.
- **Admin console** (`components/admin/`): `AdminRoot` (route `/admin`: loads the Lato font from Google Fonts and puts `nh-admin-scale` on `<html>` while mounted, imports `styles/admin.css`, and provides the session), `AdminRoute` (guard: no session → `/admin`), `AdminLayout` with `AdminSidebar` (from `data/adminNavigation.js`; the brand block is the website header's, with its tagline; one group open at a time; scrolls without a scrollbar) and `AdminTopbar` (long-short-long menu button; the account button is the "Stacked Right" design picked from `Admin Sreens/profile-samples/navbar-profile-styles.html`: the signed-in mobile number and, below, a shield icon with the role, right-aligned before a blue avatar with a white ring, a light-blue border and a green online dot; its menu has My Profile and Sign out). Every screen starts with `AdminPageHeader` (breadcrumb from Home, title, description, actions); `findNavTrail(pathname)` in `adminNavigation.js` gives the sidebar trail. Colours are the site's CSS variables from `index.css` (`var(--blue)`, `--green`, `--navy`, `--ink`, `--muted`, `--line`, `--light`).
- **Form controls:** never the browser's `<select>` or `<input type="date">` in the console. Use `components/admin/common/Select` (`value`, `onChange(value)`, `options` `[{ value, label }]`, `variant` `field` or `inline`, keyboard: arrows, Enter, Escape) and `common/DatePicker` (`value` / `onChange` as yyyy-MM-dd, `min` / `max`; the month title switches to years, then months). A value the page fixes (e.g. the role in the Add dialogs) is a locked read-only field, not a disabled dropdown.
- **Notifications** (`components/admin/NotificationsMenu`, the top bar's bell; samples 12 + 4 + 11 of `Admin Sreens/notification-samples/notification-popup-samples.html`): unread count on the bell; a plain white 32rem panel: header (title, Mark all as read · Clear all on one line, no unread line), All / Unread tabs with counts, kind chips (`NOTIFICATION_KINDS`: Customers, Partners, Bookings, Payments, System), list rows as sample 12 (unread: bold title and a blue dot on the right) with High-priority ones tagged and first (opening one marks it read and goes to its `to` page; mark read / remove on hover), empty states, and a footer with View all notifications (`/admin/notifications`, coming soon) and a settings gear (`/admin/settings`). **Sample data** (`data/adminNotifications.js`) until the backend has notifications; the state is per page load.
- **Profile photos:** `components/admin/profile/PhotoUpload` lies over the round photo of the staff and customer profiles for those who may edit them: hover or focus darkens it with a camera icon (no text; "Upload photo" / "Change photo" is the accessible label), and a picked JPEG / PNG / WebP of up to 2 MB is previewed round. **Saving is disabled**: there is no file storage yet (the user decided to build the screen only for now); wire `PhotoUpload` to an upload endpoint and set `profilePhotoUrl` once storage exists.
- **87.5% sizing:** the admin screens are sized like the browser at 87.5% zoom (the user found 100% too large and 80% a little small). `admin.css` sets `html.nh-admin-scale { font-size: 87.5% }`, so every rem shrinks; admin code therefore writes arbitrary sizes in **rem, not px** (`text-[0.8125rem]`, `w-[15.5rem]`), and lucide icon `size` values are already the scaled pixel sizes. `admin.css` also resets the public site's Manrope headings to Lato under `.nh-admin`. Scrollbars in the admin are 6 px with a transparent track and a glass-like translucent blue thumb (`::-webkit-scrollbar`; the standard `scrollbar-width` / `scrollbar-color` only under `@supports not selector(::-webkit-scrollbar)`, since Chrome ignores the pseudo-elements once those are set). It also gives enabled buttons, selects, checkboxes and `role="button"` elements the hand pointer (Tailwind 4 leaves buttons on the arrow); disabled ones keep `cursor-not-allowed`.
- **Dashboard** (`pages/admin/AdminDashboardPage`): the header and the five stat cards (`components/admin/dashboard/StatCard`), always in one row (a sideways-scrolling strip below `xl`). Customers and partners are **live** (`hooks/useUserStats` → `GET /api/users/stats`; change % = total against last month's end, trend = the monthly totals); each shown only with read on its function (`fn` in `adminDashboardData.js`); the other three are **sample data** in `data/adminDashboardData.js` (bookings, payments and ratings have no backend yet). Cards with a `source` there are the live ones. Every other sidebar link opens `AdminComingSoonPage`. Icons come from `lucide-react`.
- **Admins** (`/admin/users/admins`, `pages/admin/AdminsPage`): **ADMIN accounts only** (`roleCode=ADMIN` on `/api/users/staff` and `/staff/stats`): status tiles, search (debounced), a status filter (no Blocked: `STATUS_FILTERS`), row number and Public ID columns, the staff table (`components/admin/staff/StaffTable`: the status is a coloured badge (`statusBadgeClass` in `data/staffOptions.js`, shared with the profile: active green, inactive amber, suspended / blocked red); every text column has a width limit (Name and Email fixed, the others a maximum) and cuts longer values with "…" (full text on hover), so the table fits a 1366 px screen without a horizontal scrollbar; keep that for new columns; the mobile column shows the stored country code with the number (`+91 98765 43210`, as Customers and Partners); the row menu offers Deactivate and Suspend for an active account, Activate and Suspend for a deactivated one, and only Activate for a suspended (or blocked) one, all through `PUT /api/users/status/update`; both are off for the admin's own row), paging (`components/admin/common/Pagination`) and Add Admin (`components/admin/staff/AddAdminModal`: mobile number, optional email, the role fixed to Admin (`ADMIN_ROLE`, seeded id 2) and account status, through **Create user** `POST /api/users/create`; an inactive admin is then set to DEACTIVATED with the status update). Create user makes the staff profile with its public id; the admin fills in names and the rest under My Profile, and until then the table shows "Not added yet". Shared admin widgets: `common/Modal`, `common/Toggle`; role and status labels in `data/staffOptions.js` (DEACTIVATED is shown as "Inactive"). Row selection checkboxes have no bulk actions yet. **Without write on the page's account function the lists are read-only** (ADMIN_MANAGE for Admins, SUB_ADMIN_MANAGE for Sub Admins, super users for Super Admins): no Add button, no Actions column (`StaffTable readOnly`); on other people's profiles Edit / Add Address and the email need write on the owner's highest staff role function; one's own (My Profile) stays editable.
- **My Profile** (`/admin/profile`, from the account menu) is the same page with `self`: data from `/api/profiles/admins/me` and `/api/users/me`, breadcrumb Home > My Profile. Saves send **only the changed fields**, because employee details need write on one's own staff role function (ADMIN_MANAGE / SUB_ADMIN_MANAGE) even on one's own profile (a sub-admin may change their names and address).
- **Super Admins** (`/admin/users/super-admins`): the same `AdminsPage` with `variant="superAdmins"` (`STAFF_PAGES` holds each variant's role, titles and labels): SUPER_USER accounts, and Add Super Admin creates one with Create user (role id 1; only a super user may). The sidebar link has `superUserOnly` and is shown to super users only; others opening the address get the console's No access page (`AdminLayout`; the backend also hides super users from them). Profiles open at `/admin/users/super-admins/:id`.
- **Sub Admins** (`/admin/users/sub-admins`): `AdminsPage` with `variant="subAdmins"`: SUB_ADMIN accounts; Add Sub Admin creates one with Create user (`SUB_ADMIN_ROLE`, role id 3). Profiles open at `/admin/users/sub-admins/:id`. **Each staff list has its own details address** (`profilePath` on `StaffTable`, `list` on `AdminProfilePage`), so the sidebar, the page's access check and the breadcrumb follow the list it was opened from: User Management > Admins / Sub Admins / Super Admins > public id (nothing in the id's place, and no title, while loading).
- **Manage Partners** (`/admin/users/partners`, `pages/admin/PartnersPage`): five tiles (`/api/users/partners/stats`), search, onboarding filter (Not started + the lookup) and account status filter, the paged list (partner id link, name or "Profile not started", mobile, service, city, onboarding badge, availability dot for approved partners, account status) and, with PARTNER_MANAGE write, Add Partner (`AddAdminModal` with `PARTNER_ROLE`, id 5) and the status row menu. Every partner gets the NHPART id with the account (V16): a draft without a name shows "Not added yet" ("Profile not started" only for an account without any profile). The **partner profile** (`/admin/users/partners/:partnerProfileId`, `pages/admin/PartnerProfilePage`, read only): `CompletionAvatar` with the onboarding checks, account and onboarding badges, partner id, main service, availability; Partner Statistics (jobs, earnings, rating: dashes until those modules exist); tabs Overview (personal, work, contact, address, emergency contact (hidden until the partner allows it), onboarding), Services, Documents, Bank Accounts (with counts), Availability (weekly grid of AVAILABILITY_SLOT, service areas, travel, weekends) and Jobs / Earnings / Reviews to come. Lookup names come from `hooks/useLookupLabels`, badge colours from `data/partnerOptions.js`. Reviewing, verifying and editing partners are not in the console yet.
- **Manage Customers** (`/admin/users/customers`, `pages/admin/CustomersPage`): status tiles (`/api/users/customers/stats`), debounced search (`hooks/useDebounced`, shared with `AdminsPage`), status filter, the paged list (customer id, name or "Not added yet", email, mobile, status badge, verified, joined, last login) and, with CUSTOMER_MANAGE write, Add Customer (`AddAdminModal` with `CUSTOMER_ROLE`, seeded id 4, and its own `noun` / `note`) and the status row menu (`system/RowActions`). The customer id links (bold, no underline, as the staff table) to the **customer profile** (`/admin/users/customers/:customerProfileId`, `pages/admin/CustomerProfilePage`): the staff profile's layout (`components/admin/profile/ProfileParts`: `InfoRow`, `InfoSection`, `SectionActions`; classes in `profileStyles.js`) the summary's photo shows the **profile completion** (`components/admin/profile/CompletionAvatar`, shared with the staff profile (name, email, verified mobile, employee code, department, job title, registered address) and the partner profile; sample 4 of `Admin Sreens/profile-samples/profile-completion-samples.html`): its border is a ring filled to the share of `PROFILE_CHECKS` done (amber below 50%, blue, green at 100%), a "67% complete" tag sits across the bottom, and hovering or focusing the tag lists each detail as added or missing. Overview starts with Customer Statistics (`STAT_CARDS`, always one row like the dashboard: Total / Successful / Cancelled Bookings, Total Spend, Average Rating, each a dash with a note until bookings, payments and reviews exist), then Personal Information: name, date of birth, language, marketing consent, saved with `PUT /api/profiles/customers/update`; Contact & Account: the email with `PUT /api/users/update`; both editable with CUSTOMER_MANAGE write) Addresses (read only, `GET /api/addresses/users/{userId}`), then Bookings, Payments, Reviews (count badges, 0 until those records exist) and Activity Logs tabs (`PENDING_TABS`), empty states until those modules exist; breadcrumb User Management > Customers > customer id.
- **System Configuration** (sidebar group; replaces Settings > Roles & Permissions and takes Lookups): Manage Roles, Manage Functions and Manage Lookups under `/admin/system/*` (the Permissions item was removed: a role's permissions open from its name). Every item of User Management, Service Management and System Configuration is labelled "Manage …" in the sidebar. Roles and role permissions follow ROLE_MANAGE write / delete (the SUPER_USER role: super users only); Functions follow FUNCTION_MANAGE write / delete and Lookups LOOKUP_MANAGE. Without write the screens are view-only (a "View only" banner, no Add button, no Actions column); without read the links are hidden. Lookups is still `AdminComingSoonPage`.
  - **Roles** (`pages/admin/RolesPage`): `GET /api/roles` (`hooks/useAdminList`), stat tiles, search and status filter in the browser (the list is small), paged 10 at a time in the browser (`hooks/useClientPaging` feeding `common/Pagination`; every admin table is paged, the staff tables by the server), the row menu (`components/admin/system/RowActions`): Edit (`RoleFormModal`; the code is fixed after create), Activate / Deactivate and Delete (`ConfirmDialog`, which shows the backend's 409). The five seeded roles (`SYSTEM_ROLE_CODES`) are tagged System and offer only Edit, and their dialog keeps them active, because the code relies on them (the backend does not stop it).
  - **Role permissions** (`/admin/system/roles/:roleId`, `pages/admin/RoleDetailPage`, opened from the role name): the role summary, then every function grouped by module with Read / Write / Delete switches that save at once (first access adds the role function, changes update it with `activeFlag: true`, all off deletes it; write or delete switches read on, read off switches all off, as the backend stores them). Needs ROLE_MANAGE write (removing a grant: delete; without it, all off keeps an empty grant); SUPER_USER's own role shows everything on and locked, since it needs no grants.
  - **Functions** (`pages/admin/FunctionsPage`): `GET /api/functions`, filters by module, type (FUNCTION_TYPE lookup) and status, the same menu; `FunctionFormModal` suggests the existing modules. Toggling the status resends the description, since role and function updates always replace it.
  - Shared: `components/admin/profile/ProfileParts` also has `StatTile` and `PendingTab`; `components/admin/system/systemStyles.js` (classes, `CODE_PATTERN`, `toCode` upper-casing code inputs), `FormField`.
- **Admin profile** (`/admin/users/admins/:adminProfileId`, `pages/admin/AdminProfilePage`; the public id in the Admins table links here, a page, not a modal): one card with the summary (photo from the user's `profilePhotoUrl`, else initials; name and status badge (active green, inactive amber, suspended / blocked red); Admin ID; joined on = the user's `creationDate`), then Overview (Personal Information; Contact & Account) and Address (Registered Address) tabs of icon rows (`InfoSection` / `InfoRow`). **Each section has its own Edit, and its own Cancel / Save at its bottom**, and saves only its fields (`SECTION_FIELDS`; one section at a time): Personal Information and Contact & Account edit in place (the rows stay, values become inputs, `InlineInput`), Registered Address shows the labelled form (`EditField` grid). Personal and address go to `PUT /api/profiles/admins/update` (blank required fields left out, blank optional ones cleared); Contact & Account saves the email with `PUT /api/users/update`. The mobile number, role and status are not edited here. Save results show as the shared top-right `common/Toast` (also used by the Admins page). Data from `GET /api/profiles/admins/{id}` and `GET /api/users/{userId}`. The sidebar highlights the longest link the path is at or below (`activeLinkOf`), so Admins stays active on a profile.
- `api/client.js` (`apiRequest`, `ApiError` carrying the backend's `ExceptionBody.message`) calls `VITE_API_BASE_URL` (default `http://localhost:8080`; see `.env.example`); `api/auth.js` wraps the auth endpoints.
- The site's global `h1`–`h4` and `p { margin-top: 0 }` in `index.css` are unlayered, so they beat Tailwind's `mt-*` on those elements: space them with `mb-*` on the element above, or wrap them.
- No test runner, and no dev proxy to the backend in `vite.config.js`.
