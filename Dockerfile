# Multi-stage build for image-store
# Stage 1: Build the application
FROM gradle:8.14.3-jdk17 AS build
COPY . /app
WORKDIR /app

# Build the jar with Gradle Wrapper
RUN gradle clean build -x test --no-daemon

# Stage 2: Create the runtime image
FROM openjdk:17-jre-slim

# Install Tini for proper init process
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /app && chown -R appuser:appuser /app

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build --chown=appuser:appuser /app/build/libs/image-store-*.jar app.jar

# Switch to the non-root user
USER appuser

# Expose the default Spring Boot port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]