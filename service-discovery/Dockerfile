# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY service-discovery ./service-discovery
RUN mvn -f service-discovery/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache curl
COPY --from=build /build/service-discovery/target/service-discovery.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8761
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8761/actuator/health >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
