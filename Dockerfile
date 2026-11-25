# syntax=docker/dockerfile:1.7
# ==========================================
# Stage 1: Build the application (Maven + Eclipse Temurin 21)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:resolve-plugins dependency:resolve

# Compile and package only main sources, then extract Spring Boot layers
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B -o package -Dmaven.test.skip=true \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ==========================================
# Stage 2: Minimal ARM64 / Multi-Arch runtime image
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Security: Run under a dedicated non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy extracted Spring Boot layers from least-frequently to most-frequently changed
COPY --from=builder --chown=appuser:appgroup /build/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/application/ ./

USER appuser

# Standard listening port
EXPOSE 8080

# JVM options natively picked up by OpenJDK without shell wrapper (PID 1 = java)
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=25s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
