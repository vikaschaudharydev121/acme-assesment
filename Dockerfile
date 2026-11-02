# syntax=docker/dockerfile:1

# ---- 1. Build the Angular UI ----------------------------------------------------------------
FROM node:24-bookworm-slim AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

# ---- 2. Build the Spring Boot jar with the UI bundled as static resources -------------------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=ui /ui/dist/frontend/browser src/main/resources/static
# Tests run in CI (and via scripts/build.sh); the image build only packages.
RUN ./mvnw -q -B -DskipTests package
# sqlite-jdbc normally extracts its native library into /tmp at runtime. The container's /tmp is
# mounted noexec, so ship the library for this platform in a read-only location instead.
RUN mkdir -p /native /tmp/sqlite && cd /tmp/sqlite \
    && jar xf "$(find /root/.m2 -name 'sqlite-jdbc-*.jar' | head -1)" "org/sqlite/native/Linux/$(uname -m)/libsqlitejdbc.so" \
    && cp "org/sqlite/native/Linux/$(uname -m)/libsqlitejdbc.so" /native/

# ---- 3. Minimal runtime ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --from=api /build/target/salary-management.jar app.jar
COPY --from=api /native/libsqlitejdbc.so /app/native/libsqlitejdbc.so
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_DB_PATH=/data/salary.db \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp -Dorg.sqlite.lib.path=/app/native -Dorg.sqlite.lib.name=libsqlitejdbc.so"
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
