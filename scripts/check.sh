#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# GitHub installs scala-cli; local installations may expose scala instead.
if command -v scala-cli >/dev/null 2>&1; then
  scala_command=scala-cli
elif command -v scala >/dev/null 2>&1; then
  scala_command=scala
else
  echo "Scala CLI is required: install scala-cli before running checks." >&2
  exit 127
fi
"$scala_command" test Scala.intro --server=false
"$scala_command" --power package Scala.intro --assembly --main-class Main --server=false -o app.jar --force
