# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Cache dependency resolution first
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn -q -Dmaven.test.skip=true package && \
    cp target/product-service-2.0.0.jar target/app.jar

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user for security
RUN groupadd --system supplyprint && useradd --system --gid supplyprint supplyprint

COPY --from=builder --chown=supplyprint:supplyprint /build/target/app.jar app.jar

USER supplyprint
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

