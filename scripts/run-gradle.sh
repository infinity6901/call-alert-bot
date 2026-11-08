#!/usr/bin/env bash
# Runs gradle, keeps the full output in <logfile>, and passes on gradle's exit code
# (a plain `gradle ... | tee` would hide failures behind tee's exit code).
#
# usage: scripts/run-gradle.sh <logfile> <gradle args...>
set -o pipefail

log="${1:?usage: scripts/run-gradle.sh <logfile> <gradle args...>}"
shift

gradle "$@" --console=plain 2>&1 | tee "$log"
