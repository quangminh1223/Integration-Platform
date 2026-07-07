# Multi-stage build for Integration Platform
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .

RUN ./mvnw clean package -DskipTests -pl integration-app -am

# Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built jar
COPY --from=builder /app/integration-app/target/*.jar app.jar

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
