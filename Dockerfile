# ---------- Stage 1: build ----------
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app

# Leverage Docker layer cache: copy only files that affect dependency resolution first
COPY settings.gradle* build.gradle* gradle.properties* ./
COPY gradle ./gradle
COPY gradlew ./

# Warm the Gradle cache (won't run tests or compile your code yet)
RUN ./gradlew --version && ./gradlew dependencies --no-daemon || true

# Now copy the rest (source, resources, etc.)
COPY . .

# Build the jar
RUN ./gradlew clean build -x test --no-daemon

# ---------- Stage 2: runtime ----------
# Use Temurin JRE — replaces removed 'openjdk:17-jre-slim'
FROM eclipse-temurin:17-jre-jammy

# Install curl for the HEALTHCHECK
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /app && chown -R appuser:appuser /app
WORKDIR /app

# Copy jar from build stage
COPY --from=build --chown=appuser:appuser /app/build/libs/image-store-*.jar /app/app.jar

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
