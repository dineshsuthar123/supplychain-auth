FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dcom.sun.net.ssl.checkRevocation=false", \
    "-Dtrust_all_cert=true", \
    "-Dcom.mongodb.ssl.acceptAllCertificates=true", \
    "-jar", "app.jar"]
