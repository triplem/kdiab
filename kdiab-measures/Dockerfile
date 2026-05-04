# Stage 1: Build the JAR with Gradle
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and version catalog first for dependency caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# Copy build scripts
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Copy source
COPY src/ src/
COPY openapi-templates/ openapi-templates/
COPY api/openapi.yaml api/openapi.yaml

# Build the fat JAR, skipping tests (run separately in CI)
RUN ./gradlew build -x check --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S ktor && adduser -S ktor -G ktor

USER ktor
WORKDIR /app

COPY --from=builder --chown=ktor:ktor /app/build/libs/kdiab-measures-all.jar /app/kdiab-measures-backend.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/healthz || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-measures-backend.jar"]
