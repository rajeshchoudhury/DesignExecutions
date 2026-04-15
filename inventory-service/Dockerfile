# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY common-lib ./common-lib
COPY inventory-service ./inventory-service
RUN mvn -f common-lib/pom.xml install -DskipTests && \
    mvn -f inventory-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache curl
COPY --from=build /build/inventory-service/target/inventory-service.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8083
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8083/actuator/health >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
