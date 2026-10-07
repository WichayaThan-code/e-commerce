#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -d "$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" ]]; then
  export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -f .env.local ]]; then
  set -a
  source .env.local
  set +a
fi
exec bash ./mvnw spring-boot:run
