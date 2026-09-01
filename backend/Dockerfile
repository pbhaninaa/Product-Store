# syntax=docker/dockerfile:1
FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
# BuildKit cache keeps ~/.m2 across deploys so Maven does not re-download the world.
# Railway Metal builders require an explicit cache mount id.
RUN --mount=type=cache,id=mavencache,target=/root/.m2 \
    mvn -q -DskipTests package \
    && JAR="$(ls target/platform-*.jar | grep -v '\\.original$' | head -n 1)" \
    && mv "$JAR" /app/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV UPLOADS_DIR=/app/data/uploads

# No apt-get: Railway uses its own HTTP healthcheck (see railway.toml). Keeps image builds fast.
RUN groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
  && mkdir -p /app/data/uploads /app/data/private/subscription-proofs \
               /data/uploads /data/private/subscription-proofs \
  && chown -R app:app /app /data

COPY --from=build /app/app.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh && chown app:app /app/app.jar

EXPOSE 8080

# Root entrypoint creates/chowns volume paths, then drops to app via runuser.
USER root
ENTRYPOINT ["/app/docker-entrypoint.sh"]
