# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

NexHome is a multi-client project in early scaffolding. Each client lives in its own self-contained sub-project with its own build tooling; there is no root-level build and the root is not a git repository.

- `backend/nexhome/` — Spring Boot 4.1 API (Java 21, Maven wrapper, JPA + PostgreSQL, Lombok, Bean Validation). Base package `com.nexhome.app`.
- `web/nexhome-web-app/` — React 19 + Vite 8 SPA (plain JavaScript/JSX, no TypeScript). Still the default Vite template.
- `android/` — empty placeholder for a future Android client.

Run commands from inside the relevant sub-project directory.

## Backend (`backend/nexhome`)

```bash
./mvnw spring-boot:run                                   # run (use mvnw.cmd on Windows cmd/PowerShell)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # run with dev profile
./mvnw test                                              # all tests
./mvnw test -Dtest=NexhomeApplicationTests               # single test class
./mvnw test -Dtest=NexhomeApplicationTests#contextLoads  # single test method
./mvnw package                                           # build target/nexhome-0.0.1-SNAPSHOT.jar
```

### Configuration model

- `application.properties` reads the datasource from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`. These come from `.env` (see `.env.example`; gitignored), but the import (`spring.config.import=optional:file:.env[.properties]`) is in `application-dev.properties`, so it only happens under the `dev` profile. Without `dev`, the placeholders stay unresolved and startup fails with `'url' must start with "jdbc"`. The path is relative to the working directory, so run from `backend/nexhome/`.
- No profile is active by default (`spring.profiles.active` is commented out). Set `SPRING_PROFILES_ACTIVE` or `-Dspring-boot.run.profiles` to pick one; `ActiveProfileLogger` logs the active profile on startup and warns when none is set.
  - `dev`: loads `.env`, `ddl-auto=update` (adds tables/columns but never drops them), SQL logging, `com.nexhome` at DEBUG.
  - `prod`: `ddl-auto=validate` (schema must already exist — no migration tool is configured yet), error stacktraces/messages hidden.
- `spring.jpa.open-in-view=false` globally — load lazy associations inside transactional service methods, not in controllers.
- `NexhomeApplicationTests` is a `@SpringBootTest` against the real datasource, so it needs PostgreSQL and the `dev` profile: `SPRING_PROFILES_ACTIVE=dev ./mvnw test`. Under `dev` it applies `ddl-auto=update` to the dev database. To check mappings without touching the schema, add `-DargLine="-Dspring.jpa.hibernate.ddl-auto=none"`. Spring Data checks derived query method names at startup, so this test catches broken repository methods. Service tests (e.g. `service/NexHomeUserServiceImplTest`) are plain Mockito unit tests and need no database: `./mvnw test -Dtest=NexHomeUserServiceImplTest`.

### Code layout

Layered packages under `com.nexhome.app`: `controller` → `service` → `repository` → `model` (JPA entities, plus the non-entity `ExceptionBody`), with `dto` (request/response records), `mapper` (hand-written `@Component` entity↔DTO mappers, no MapStruct), `exception` and `config`.

**Naming:** every domain class uses the `NexHome` prefix (capital H): `NexHomeUser`, `NexHomeUserRepository`, `NexHomeUserService` / `NexHomeUserServiceImpl`, `NexHomeUserController`, `NexHomeUserMapper`, `NexHomeUserRegisterRequest`, `NexHomeUserResponse`. The Spring Boot entry point is still `NexhomeApplication`. Tables are lowercase `nexhome_*`.

- Each service is an interface `NexHome<X>Service` plus its implementation `NexHome<X>ServiceImpl` (`@Service`), **both directly in the `service` package**. Do not create a `service.impl` subpackage. Controllers inject the interface.
- Controllers are thin and only validate (`@Valid`) and delegate. Service implementations own `@Transactional` business logic and never return entities, only DTOs built by the mapper.
- Every error response is a `ResponseEntity<ExceptionBody>`. `model/ExceptionBody` is a plain Lombok class, not a JPA entity, with `{path, method, statusCode, error, message, timestamp}`; `error` is the HTTP reason phrase and `timestamp` is a `ZonedDateTime` in `Asia/Kolkata`, serialized via `@JsonFormat` as `EEE, dd MMM yyyy HH:mm:ss z` (e.g. `Thu, 08 Oct 2026 01:42:30 IST`). The zone is fixed in the handler and the annotation, not taken from the server time zone. `exception/GlobalExceptionHandler` is a plain `@RestControllerAdvice`. It deliberately does not extend `ResponseEntityExceptionHandler`, whose overrides must return `ResponseEntity<Object>`. Every handler method returns `ResponseEntity<ExceptionBody>` via its `build(...)` helper:
  - our own exceptions: throw `DuplicateResourceException` (409) or `InvalidRequestException` (400) with a client-safe message;
  - `DataIntegrityViolationException` (409);
  - Bean Validation failures (400). `message` joins the first error of each invalid field with `", "`, without field-name prefixes. So every constraint in a DTO must set `message` to a full, capitalised sentence naming the field, e.g. `@Pattern(..., message = "Phone must be exactly 10 digits")`. The default messages (`"must not be blank"`) would not say which field failed;
  - unreadable or malformed JSON (400);
  - the `Exception` catch-all: Spring MVC errors implementing `ErrorResponse` (404, 405, 415, ...) keep their own status and detail; anything else becomes a 500 with a generic message, full stack trace logged only.

  Do not return `ProblemDetail` or Spring Boot's default error JSON.
- While devtools is restarting the app, requests briefly get Spring Boot's default error JSON (with a stack trace). That is not the handler failing.
- Passwords are hashed with the `PasswordEncoder` bean (BCrypt) from `config/PasswordEncoderConfig`. Only `spring-security-crypto` is on the classpath, not the security starter, so **no endpoint is authenticated yet**.
- `POST /api/users/register` (`NexHomeUserController` → `NexHomeUserService.register`) creates only the `NexHomeUser` login account. It takes `phone` (required, exactly 10 digits, no `+91` or spaces), `email` (optional; must contain `@` and a dotted domain), `password` (required, 8–16 characters) and `roleId` (required). User details are not part of registration; they belong to a separate user-details API. `roleId` must reference an active role. The acting user is **not** part of the request. Until a login service exists, `NexHomeUserServiceImpl` records the constant `DEFAULT_LOGGED_IN_USER_ID = 1L` (marked TODO) as `created_by` / `last_update_by`, without checking that user 1 exists. Once login is implemented, replace it with the authenticated user's id and restrict the endpoint (or which roles it may assign). A user is identified by **phone + role**: the same phone may be registered once per role (e.g. as both partner and customer). The service rejects a duplicate pair via `findByPhoneAndRoleRoleId`, because the database has no unique constraint. Since one phone can map to several users, never look a user up by phone alone. Email is deliberately not checked for duplicates. Emails are stored lower-cased.

### Data model (`model` package)

Tables are named `nexhome_*`; entities are `NexHome*`. Primary keys are `<entity>_id` (`user_id`, `role_id`, `function_id`, `role_function_id`, `user_details_id`) using `IDENTITY`.

- `NexHomeUser`: login account (phone, password hash, optional email, `last_login`). Each user has exactly one role through a required `role_id` foreign key to `NexHomeRole`; there is no user-role join table. There is no username, and phone/email are not unique constraints.
- `NexHomeUserDetails`: one-to-one profile per user (`user_id` unique): names and address fields.
- `NexHomeRole`: `role_name` unique.
- `NexHomeFunction`: a screen or feature that access can be granted to, grouped by `function_module`. `function_code` and `function_name` are both unique.
- `NexHomeRoleFunction`: a role's access to a function as `read` / `write` / `delete` booleans, unique per (`role_id`, `function_id`).

Conventions every entity follows; keep them when adding entities:

- The audit columns `creation_date`, `created_by`, `last_update_date`, `last_update_by` and the spare columns `attribute1`–`attribute5` are declared **directly in each entity**. Do not extract them into a `@MappedSuperclass` or `@Embeddable`; this was tried and rejected.
- `creation_date` / `last_update_date` are filled by Hibernate's `@CreationTimestamp` / `@UpdateTimestamp`. These run on entity insert/update only, not on bulk JPQL or native SQL. `created_by` / `last_update_by` hold a `user_id` and must be set by the caller; nothing fills them yet.
- `active_flag` is a `boolean` defaulting to `true`. Associations are `@ManyToOne`/`@OneToOne(fetch = LAZY)` with explicit `@JoinColumn(name = ..., referencedColumnName = ...)`. Lombok `@Getter @Setter @NoArgsConstructor` only, no `@Data`.
- `password` must hold a hash, never the plain password.
- Column order in the database: Hibernate does **not** follow field declaration order. Tables it creates get the PK first, then alphabetical columns, and `ddl-auto=update` appends new columns at the end. PostgreSQL cannot reorder existing columns. The current `nexhome_*` tables were recreated once from hand-checked DDL in model order. Keeping that order for new tables or columns requires writing the DDL yourself (e.g. a migration script); no custom Hibernate class is used for this. A `ColumnOrderingStrategy` only affects schema-export scripts, not `ddl-auto=update`.

### Repositories (`repository` package)

One `JpaRepository<Entity, Long>` per entity. Derived query names follow Java field paths, not column names: on entities that hold an association, the id is reached through the association, e.g. `findByRoleRoleIdAndFunctionFunctionId` on `NexHomeRoleFunction`. `findByUserId` would fail at startup on `NexHomeUserDetails`, because its field is `user`, not `userId`. `findByUserId` / `findByRoleId` / `findByFunctionId` on the owning entities are equivalent to the built-in `findById`. Return `Optional` only for columns with a unique constraint, otherwise `List`.

## Web (`web/nexhome-web-app`)

```bash
npm install
npm run dev       # Vite dev server with HMR
npm run build     # production build to dist/
npm run lint      # ESLint (flat config: js recommended + react-hooks + react-refresh)
npm run preview   # serve the production build
```

No test runner is configured for the web app yet. No dev proxy to the backend is configured in `vite.config.js`.
