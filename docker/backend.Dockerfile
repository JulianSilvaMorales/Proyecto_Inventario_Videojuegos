# syntax=docker/dockerfile:1
# Backend Spring Boot. Contexto de construcción: raíz del repositorio.

# 1. Compilación con JDK 17 y el Maven Wrapper del proyecto (Maven 3.9.11).
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY backend/src src
RUN sh mvnw -B -q -DskipTests package \
    && cp target/inventario-backend-*.jar /build/app.jar

# 2. Ejecución con JRE 21, usuario sin privilegios y JVM ajustada a poca memoria.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
# Modelo resuelto que el backend usa para validar el esquema al arrancar.
COPY db/model/schema-resuelto.json db/model/schema-resuelto.json
ENV INVENTORY_MODEL_PATH=/app/db/model/schema-resuelto.json \
    SPRING_PROFILES_ACTIVE=produccion \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
