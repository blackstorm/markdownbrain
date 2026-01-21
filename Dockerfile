FROM node:20-alpine AS frontend-builder
WORKDIR /app/server
COPY server/package*.json ./
RUN npm ci
COPY server/console.css server/frontend.css ./
COPY server/resources/templates ./resources/templates
RUN mkdir -p resources/publics/console/css resources/publics/frontend/css
RUN npm run build

FROM clojure:temurin-21-tools-deps-alpine AS backend-builder
WORKDIR /app/server
COPY server/deps.edn server/build.clj ./
RUN clojure -P -T:build
COPY server/src ./src
COPY server/resources ./resources
COPY --from=frontend-builder /app/server/resources/publics/console/css/console.css ./resources/publics/console/css/console.css
COPY --from=frontend-builder /app/server/resources/publics/frontend/css/frontend.css ./resources/publics/frontend/css/frontend.css
RUN clojure -T:build uberjar

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 mdbrain && \
    adduser -u 1000 -G mdbrain -s /bin/sh -D mdbrain && \
    mkdir -p /app/data && \
    chown -R mdbrain:mdbrain /app

COPY --from=backend-builder --chown=mdbrain:mdbrain /app/server/target/server-standalone.jar ./app.jar

USER mdbrain

ENV FRONTEND_PORT=8080
ENV CONSOLE_PORT=9090
ENV DB_PATH=/app/data/markdownbrain.db
ENV ENVIRONMENT=production

EXPOSE 8080 9090

VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:9090/console/health || exit 1

CMD ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
