# Use a lightweight JDK base image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dcom.sun.net.ssl.checkRevocation=false", \
    "-Dtrust_all_cert=true", \
    "-Dcom.mongodb.ssl.acceptAllCertificates=true", \
    "-jar", "app.jar"]
