#!/usr/bin/env bash
set -e
command -v mvn >/dev/null || { echo "Maven not found. Install Maven or use your IDE."; exit 1; }
mvn spring-boot:run
