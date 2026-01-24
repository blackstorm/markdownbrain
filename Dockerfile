FROM node:25-bookworm-slim AS frontend-builder
WORKDIR /app/server
COPY server/package*.json ./
RUN npm ci
COPY server/console.css server/frontend.css ./
COPY server/resources/templates ./resources/templates
RUN mkdir -p resources/publics/console/css resources/publics/frontend/css
RUN npm run build

FROM clojure:temurin-25-tools-deps AS backend-builder
WORKDIR /app/server
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY server/deps.edn server/build.clj ./
RUN clojure -P -T:build
COPY server/src ./src
COPY server/resources ./resources
COPY --from=frontend-builder /app/server/resources/publics/console/css/console.css ./resources/publics/console/css/console.css
COPY --from=frontend-builder /app/server/resources/publics/frontend/css/frontend.css ./resources/publics/frontend/css/frontend.css
RUN clojure -T:build uberjar

# Stage 3: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1000 markdownbrain && \
    useradd -m -u 1000 -g 1000 -s /bin/sh markdownbrain && \
    mkdir -p /app/data && \
    chown -R markdownbrain:markdownbrain /app

COPY --from=backend-builder --chown=markdownbrain:markdownbrain /app/server/target/server-standalone.jar ./app.jar
COPY docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh

USER markdownbrain

ENV FRONTEND_PORT=8080
ENV CONSOLE_PORT=9090
ENV DB_PATH=/app/data/markdownbrain.db
ENV ENVIRONMENT=production
ENV JAVA_OPTS=""

EXPOSE 8080 9090

VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -fsS http://localhost:9090/console/health >/dev/null || exit 1

CMD ["./docker-entrypoint.sh"]
