# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

ARG MODULE
COPY ${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=10 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080 8081 8082
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
