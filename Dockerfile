# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom files for dependency caching
COPY pom.xml .
COPY agent-core/pom.xml ./agent-core/
COPY agent-datasync/pom.xml ./agent-datasync/
COPY agent-web/pom.xml ./agent-web/

# Download dependencies for better Docker layer caching
RUN mvn dependency:go-offline -B

# Copy source code
COPY agent-core/src ./agent-core/src
COPY agent-datasync/src ./agent-datasync/src
COPY agent-web/src ./agent-web/src

# Build the application - only build agent-web which contains the main application
RUN mvn clean package -pl agent-web -am -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/agent-web/target/agent-web-*.jar app.jar

# Create logs directory
RUN mkdir -p logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check - 更宽松的健康检查配置
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080/ || exit 1

# Expose port
EXPOSE 8080

# JVM optimization for containers
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]