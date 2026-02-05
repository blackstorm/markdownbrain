FROM node:25-bookworm-slim AS app-builder
WORKDIR /app/server
COPY server/package*.json ./
RUN npm install --include=dev
COPY server/console.css server/app.css ./
COPY server/resources/templates ./resources/templates
COPY server/resources/publics/console ./resources/publics/console
COPY server/resources/publics/shared ./resources/publics/shared
RUN mkdir -p resources/publics/console/css resources/publics/app/css
RUN npm run build

FROM clojure:temurin-25-tools-deps AS backend-builder
WORKDIR /app/server
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY server/deps.edn server/build.clj ./
RUN clojure -P -T:build
COPY server/src ./src
COPY server/resources ./resources
COPY --from=app-builder /app/server/resources/publics/console/css/console.css ./resources/publics/console/css/console.css
COPY --from=app-builder /app/server/resources/publics/app/css/app.css ./resources/publics/app/css/app.css
RUN clojure -T:build uberjar

# Stage 3: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    getent group mdbrain >/dev/null || groupadd -r mdbrain; \
    id -u mdbrain >/dev/null 2>&1 || useradd -r -m -g mdbrain -s /bin/sh mdbrain; \
    mkdir -p /app/data; \
    chown -R mdbrain:mdbrain /app

COPY --from=backend-builder --chown=mdbrain:mdbrain /app/server/target/server-standalone.jar ./app.jar
COPY docker-entrypoint.sh ./docker-entrypoint.sh
COPY docker-healthcheck.sh ./healthcheck.sh
RUN chmod +x ./docker-entrypoint.sh ./healthcheck.sh

USER mdbrain

ENV APP_PORT=8080
ENV CONSOLE_PORT=9090
ENV DATA_PATH=/app/data
ENV ENVIRONMENT=production
ENV JAVA_OPTS=""
ENV MDBRAIN_LOG_LEVEL=INFO

EXPOSE 8080 9090

VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/app/healthcheck.sh"]

CMD ["./docker-entrypoint.sh"]
