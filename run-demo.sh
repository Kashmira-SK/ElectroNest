#!/usr/bin/env sh
set -eu

./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
