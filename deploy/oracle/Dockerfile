# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
WORKDIR /build
COPY backend/product-service/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY backend/product-service/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package
# Extract the exact pinned ARM64 JNI binaries at build time. Runtime /tmp stays noexec.
RUN --mount=type=cache,target=/root/.m2 mkdir /build/native && cd /build/native \
    && jar xf /root/.m2/repository/com/microsoft/onnxruntime/onnxruntime/1.17.3/onnxruntime-1.17.3.jar \
        ai/onnxruntime/native/linux-aarch64/libonnxruntime.so \
        ai/onnxruntime/native/linux-aarch64/libonnxruntime4j_jni.so

FROM eclipse-temurin:17.0.21_9-jre-jammy@sha256:aa6381384f8db99762fa7d0d3c2b5de58874e5f12c96df4c13a1455376ae0ed1
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 supplyprint && useradd -u 10001 -g supplyprint -M supplyprint
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/product-service-2.0.0.jar /app/app.jar
COPY --from=build /build/native/ai/onnxruntime/native/linux-aarch64/ /opt/onnx/
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=15 -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Donnxruntime.native.path=/opt/onnx"
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=150s --retries=4 CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
