# ==========================================
# Stage 1: Build the application (Maven + Eclipse Temurin 21)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package the executable JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Minimal ARM64 / Multi-Arch runtime image
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Security: Run under a dedicated non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy executable JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

# Standard listening port
EXPOSE 8080

# JVM options optimized for containers (K3s / cgroups)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
