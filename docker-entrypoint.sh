#!/usr/bin/env sh
set -eu

exec java -Djava.awt.headless=true ${JAVA_OPTS:-} -jar /app/app.jar
