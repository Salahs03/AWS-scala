#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
scala test Scala.intro --server=false
scala --power package Scala.intro --assembly --main-class Main --server=false -o app.jar --force
