#!/bin/bash
# Throwaway Paper server in Docker with Prost: starts cleanly, writes its files, registers /prost and /drinks, takes over
# an AniDrinks folder and prints the catalogue entries the version skips. With PlaceholderAPI it also checks %prost_*%.
# Usage: scripts/smoke-test.sh <minecraft version> <Prost jar> [--no-papi]      (needs docker, curl, python3, sha256sum)
# Run it as root (or uid 1000): the server directory is chowned to the container's user 1000.
set -euo pipefail
[ $# -ge 2 ] || { echo "usage: $0 <minecraft version> <Prost jar> [--no-papi]" >&2; exit 2; }
VERSION=$1
JAR=$(realpath "$2")
WITH_PAPI=true
[ "${3:-}" = "--no-papi" ] && WITH_PAPI=false
# pinned so a rerun tests the same thing; the digest is the itzg image the version list was verified with
IMAGE=itzg/minecraft-server@sha256:7fe693829f4f4ca7d806ca40f81a349f5e350f6ccb6708a0773fd345f9310a82
PAPI_VERSION=2.12.3
PAPI_SHA256=fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437
DIR=$(mktemp -d)
NAME=prost-smoke-$$
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -rf "$DIR"' EXIT
mkdir -p "$DIR/plugins/AniDrinks"
cp "$JAR" "$DIR/plugins/Prost.jar"
if $WITH_PAPI; then
  PAPI_URL=$(curl -sf "https://api.modrinth.com/v2/project/placeholderapi/version?loaders=%5B%22paper%22%5D" \
    | python3 -c 'import json,sys; print(next((v["files"][0]["url"] for v in json.load(sys.stdin) if v["version_number"] == sys.argv[1]), ""))' "$PAPI_VERSION")
  [ -n "$PAPI_URL" ] || { echo "PlaceholderAPI $PAPI_VERSION is not on Modrinth (or the API did not answer)" >&2; exit 1; }
  curl -sfL -o "$DIR/plugins/PlaceholderAPI.jar" "$PAPI_URL"
  (cd "$DIR/plugins" && echo "$PAPI_SHA256  PlaceholderAPI.jar" | sha256sum -c --quiet -) \
    || { echo "PlaceholderAPI.jar does not match the pinned sha256" >&2; exit 1; }
fi
# a fake AniDrinks 0.2.0 install: stats.db with the two tables it creates, plus an admin-edited drinks.yml
python3 - "$DIR/plugins/AniDrinks" <<'PY'
import sqlite3, sys, uuid
d = sys.argv[1]
c = sqlite3.connect(d + "/stats.db")
c.execute("CREATE TABLE stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, sips_drunk INTEGER NOT NULL DEFAULT 0, sips_water INTEGER NOT NULL DEFAULT 0, sips_given INTEGER NOT NULL DEFAULT 0, won INTEGER NOT NULL DEFAULT 0, lost INTEGER NOT NULL DEFAULT 0, roulette_hits INTEGER NOT NULL DEFAULT 0, jokers_used INTEGER NOT NULL DEFAULT 0, rules_made INTEGER NOT NULL DEFAULT 0, rounds INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0)")
c.execute("CREATE TABLE rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, host TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL, members INTEGER NOT NULL, challenges INTEGER NOT NULL)")
c.execute("INSERT INTO stats VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", (str(uuid.uuid4()), "Kasheyy", 26, 0, 12, 6, 3, 6, 1, 1, 2, 1))
c.execute("INSERT INTO stats VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", (str(uuid.uuid4()), "Alex", 27, 0, 10, 7, 5, 0, 0, 0, 2, 1))
c.commit()
open(d + "/drinks.yml", "w").write("max-players: 6\n")
PY
# Paper's 26.x builds are still on the experimental channel
CHANNEL=()
[[ $VERSION == 26.* ]] && CHANNEL=(-e PAPER_CHANNEL=experimental)
chown -R 1000:1000 "$DIR"
docker run -d --name "$NAME" -v "$DIR":/data -e EULA=TRUE -e TYPE=PAPER -e VERSION="$VERSION" ${CHANNEL[@]+"${CHANNEL[@]}"} \
  -e ONLINE_MODE=FALSE -e MEMORY=2G -e ENABLE_RCON=true -e RCON_PASSWORD=smoke "$IMAGE" >/dev/null
started=false
for _ in $(seq 1 150); do
  LOG=$(docker logs "$NAME" 2>&1)
  if [[ $LOG == *"Done ("* ]]; then started=true; break; fi
  [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" = true ] || break
  sleep 3
done
if ! $started; then
  echo "server did not finish starting; last 30 log lines:"
  docker logs --tail 30 "$NAME" 2>&1
  exit 1
fi
R() { docker exec "$NAME" rcon-cli -- "$@"; }
fail=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fail=1; fi; }
# an ERROR line from Prost, or a stack trace right after a Prost line or through Prost code (WARN lines are only printed below)
prost_errors() {
  awk '/ERROR\]: \[Prost\]/ {bad=1}
       /dev\.louismudrack\.prost/ && /^[ \t]+at |Exception/ {bad=1}
       /^\[[0-9:]+ [A-Z]+\]: \[Prost\]/ {after=1; next}
       /^\[[0-9:]+ / {after=0}
       after && /^[ \t]+at |^Caused by|Exception/ {bad=1}
       END {exit !bad}' <<<"$LOG"
}
check "server started" '[[ $LOG == *"Done ("* ]]'
# 103 challenges and 30 rules are the shipped challenges.yml and rules.yml: update them when the catalogue changes
check "Prost enabled with the full catalogue" '[[ $LOG == *"Prost enabled: 103 challenges, 30 rules, 2 languages"* ]]'
check "AniDrinks taken over" '[[ $LOG == *"AniDrinks data taken over: 2 players"* ]]'
check "no error or stack trace from Prost" '! prost_errors'
check "files written" 'test -f "$DIR/plugins/Prost/lang/de.yml" -a -f "$DIR/plugins/Prost/config.yml" -a -d "$DIR/plugins/AniDrinks.migrated"'
check "game.yml came from AniDrinks" 'grep -q "max-players: 6" "$DIR/plugins/Prost/game.yml"'
check "/prost answers the console" '[[ $(R prost admin list) == *"No round is running"* ]]'
check "/drinks is an alias" '[[ $(R drinks admin list) == *"No round is running"* ]]'
if $WITH_PAPI; then
  check "PlaceholderAPI registered the expansion" '[[ $LOG == *"registered internal expansion: prost"* ]]'
  R papi parse --null %prost_top_1_name% >/dev/null || true; sleep 2   # the first request only starts the 30 s cache
  check "%prost_top_1_name% answers the migrated leader" '[[ $(R papi parse --null %prost_top_1_name%) == *Alex* ]]'
fi
echo "--- Prost warnings:"; grep -E 'WARN\]: \[Prost\]' <<<"$LOG" || echo "(none)"
echo "--- catalogue entries $VERSION skips:"; grep -i "skipped" <<<"$LOG" || echo "(none)"
exit $fail
