# NexHome backend (Spring Boot 4.1, Java 21).
#
# Build:  docker build -t nexhome-backend .
# Run:    docker run --env-file .env -p 8080:8080 nexhome-backend
#
# Secrets are never baked into the image (.dockerignore leaves .env and key files out): pass them at run time.
# The image runs the `dev` profile (SQL logging, OTPs logged, OTP_FIXED_CODES, CORS for every localhost port, the dev
# webhook secret). dev would import .env from the working directory, but none is in the image, so the settings come
# from the environment (--env-file .env): at least DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET and
# DATA_ENCRYPTION_KEY (see .env.example and CLAUDE.md), plus the storage / SMS / push / Cashfree settings in use.
# For production run it with -e SPRING_PROFILES_ACTIVE=prod (then also set PAYMENT_WEBHOOK_SECRET and
# CORS_ALLOWED_ORIGINS).
# A DB_URL pointing at a database on the host machine uses host.docker.internal, not localhost.

# ---- Build: compile and package the jar (tests need a database, so they run outside the image build) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# The Maven wrapper and the dependencies first, so source changes reuse this cached layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/nexhome-*.jar app.jar

# ---- Run: a slim JRE, as a non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Local file storage (STORAGE_PROVIDER=local, the default) writes here: mount a volume on /data/storage to keep files
RUN groupadd --system nexhome && useradd --system --gid nexhome --uid 10001 nexhome \
	&& mkdir -p /data/storage && chown nexhome:nexhome /data/storage

COPY --from=build /workspace/app.jar app.jar
USER nexhome

ENV SPRING_PROFILES_ACTIVE=dev \
	STORAGE_LOCAL_ROOT=/data/storage \
	TZ=Asia/Kolkata \
	JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
VOLUME ["/data/storage"]

# exec, so the JVM gets the container's stop signal and shuts down gracefully
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
