 
FROM maven:3.10.0-eclipse-temurin-25-alpine AS build

WORKDIR /workspace
COPY . .

RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests \    && mkdir -p /app \
    && cp target/*.jar /app/app.jar

FROM eclipse-temurin:25-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       tesseract-ocr tesseract-ocr-eng wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home \
       --shell /usr/sbin/nologin app

COPY --from=build --chown=10001:10001 /app/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=production \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
