FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package \
  && JAR="$(ls target/platform-*.jar | grep -v '\\.original$' | head -n 1)" \
  && mv "$JAR" /app/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV UPLOADS_DIR=/app/data/uploads

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
  && mkdir -p /app/data/uploads /app/data/private/subscription-proofs \
               /data/uploads /data/private/subscription-proofs \
  && chown -R app:app /app /data

COPY --from=build /app/app.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh && chown app:app /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q UP || exit 1

# Root entrypoint creates/chowns volume paths, then drops to app via runuser.
USER root
ENTRYPOINT ["/app/docker-entrypoint.sh"]
