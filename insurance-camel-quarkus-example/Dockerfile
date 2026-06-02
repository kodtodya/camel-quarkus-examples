# ============================================================
# Dockerfile — Insurance Policy Camel POC
# Multi-stage build: Maven build → JVM runtime
# ============================================================

# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache Maven dependencies first
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B --no-transfer-progress

# Copy source and build (skip tests for Docker build; run tests in CI)
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Insurance Engineering <insurance-eng@company.com>"
LABEL description="Insurance Policy Camel Bulk Processing POC"
LABEL version="1.0.0"

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Quarkus fast-jar layout
COPY --from=builder /build/target/quarkus-app/lib/                ./lib/
COPY --from=builder /build/target/quarkus-app/*.jar               ./
COPY --from=builder /build/target/quarkus-app/app/                ./app/
COPY --from=builder /build/target/quarkus-app/quarkus/            ./quarkus/

# Create spool directory for Camel stream caching
RUN mkdir -p /tmp/camel-spool && chown -R appuser:appgroup /tmp/camel-spool /app

USER appuser

# ---- Ports ----
# 8080  — Quarkus HTTP (REST, Swagger, H2 Console via Quarkus, Prometheus, Health)
# 8082  — H2 Web Console (standalone)
# 9092  — H2 TCP Server (JDBC)
EXPOSE 8080 8082 9092

# ---- JVM tuning for bulk processing ----
ENV JAVA_OPTS="\
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.net.preferIPv4Stack=true \
  -Dfile.encoding=UTF-8"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/q/health/live || exit 1

ENTRYPOINT ["sh", "-c", \
  "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
