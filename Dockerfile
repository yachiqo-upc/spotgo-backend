FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY . .
RUN chmod +x ./mvnw \
    && ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

# Render supplies PORT at runtime. Keep the production profile as the container default.
ENV SPRING_PROFILES_ACTIVE=production \
    EVIDENCE_STORAGE_DIRECTORY=/tmp/spotgo-evidence

EXPOSE 10000

COPY --from=build --chown=10001:10001 /workspace/target/spotgo-1.0.0.jar /app/app.jar

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
