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

- `application.properties` imports `.env` from the **working directory** (`spring.config.import=optional:file:.env[.properties]`), so run from `backend/nexhome/`. `.env` supplies `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (see `.env.example`); it is gitignored.
- No profile is active by default (`spring.profiles.active` is commented out). Set `SPRING_PROFILES_ACTIVE` or `-Dspring-boot.run.profiles` to pick one; `ActiveProfileLogger` logs the active profile on startup and warns when none is set.
  - `dev`: `ddl-auto=update`, SQL logging, `com.nexhome` at DEBUG.
  - `prod`: `ddl-auto=validate` (schema must already exist — no migration tool is configured yet), error stacktraces/messages hidden.
- `spring.jpa.open-in-view=false` globally — load lazy associations inside transactional service methods, not in controllers.
- Tests use `@SpringBootTest` with a real datasource, so a reachable PostgreSQL instance and a valid `.env` are required for `./mvnw test` to pass.

## Web (`web/nexhome-web-app`)

```bash
npm install
npm run dev       # Vite dev server with HMR
npm run build     # production build to dist/
npm run lint      # ESLint (flat config: js recommended + react-hooks + react-refresh)
npm run preview   # serve the production build
```

No test runner is configured for the web app yet. No dev proxy to the backend is configured in `vite.config.js`.
