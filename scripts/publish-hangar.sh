#!/bin/bash
# Uploads a Prost jar to Hangar (hangar.papermc.io/Kasheyy/Prost) as a new version: channel Release, platform Paper with
# every Paper version Hangar lists from 1.21.4 on (Prost refuses anything older).
# Usage: HANGAR_API_TOKEN=<api key> scripts/publish-hangar.sh <version> <Prost jar> [changelog file]
#   The key needs the create_version permission. It is read from the environment and never printed or put on a command
#   line. DRY_RUN=1 prints the version data instead of uploading (no key needed). Needs curl and python3.
set -euo pipefail
[ $# -ge 2 ] || { echo "usage: HANGAR_API_TOKEN=<api key> $0 <version> <Prost jar> [changelog file]" >&2; exit 2; }
VERSION=$1
JAR=$2
CHANGELOG=${3:-}
[ -f "$JAR" ] || { echo "$JAR is not a file" >&2; exit 2; }
[ -z "$CHANGELOG" ] || [ -f "$CHANGELOG" ] || { echo "$CHANGELOG is not a file" >&2; exit 2; }
API=https://hangar.papermc.io/api/v1
PROJECT=Prost
CHANNEL=Release
MINIMUM_PAPER=1.21.4
USER_AGENT="Kasheyy/Prost-release/$VERSION (https://hangar.papermc.io/Kasheyy/Prost)"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# set -e would end the script without a word when curl fails
curl -sSf -A "$USER_AGENT" -o "$TMP/platforms.json" "$API/platforms/PAPER/versions" \
  || { echo "Hangar did not list its Paper versions" >&2; exit 1; }
python3 - "$VERSION" "$CHANNEL" "$MINIMUM_PAPER" "$TMP/platforms.json" "$CHANGELOG" > "$TMP/version.json" <<'PY'
import json, sys
version, channel, minimum, platforms, changelog = sys.argv[1:6]

def numbers(v):
    return tuple(int(part) for part in v.split("."))

listed = []
for group in json.load(open(platforms, encoding="utf-8")):
    listed += group["subVersions"] or [group["version"]]
paper = sorted({v for v in listed if numbers(v) >= numbers(minimum)}, key=numbers)
if not paper:
    sys.exit("Hangar lists no Paper version from " + minimum + " on")
description = open(changelog, encoding="utf-8").read().strip() if changelog else ""
print(json.dumps({"version": version, "channel": channel, "description": description,
                  "files": [{"platforms": ["PAPER"]}], "platformDependencies": {"PAPER": paper}}, indent=1))
PY
if [ "${DRY_RUN:-}" = 1 ]; then
  cat "$TMP/version.json"
  exit 0
fi
[ -n "${HANGAR_API_TOKEN:-}" ] || { echo "HANGAR_API_TOKEN is not set (a Hangar API key with the create_version permission)" >&2; exit 2; }

# curl reads the key and the JWT from its config on stdin, so neither shows up in the process list
status=$(curl -sS -o "$TMP/session.json" -w '%{http_code}' -K - <<EOF
url = "$API/authenticate?apiKey=$HANGAR_API_TOKEN"
request = "POST"
user-agent = "$USER_AGENT"
EOF
) || { echo "Hangar could not be reached to log in" >&2; exit 1; }
[ "$status" = 200 ] || { echo "Hangar refused the API key (HTTP $status)" >&2; exit 1; }
JWT=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["token"])' "$TMP/session.json")

status=$(curl -sS -o "$TMP/answer.json" -w '%{http_code}' -K - <<EOF
url = "$API/projects/$PROJECT/upload"
user-agent = "$USER_AGENT"
header = "Authorization: HangarAuth $JWT"
form = "versionUpload=<$TMP/version.json;type=application/json"
form = "files=@$JAR;type=application/java-archive"
EOF
) || { echo "Hangar could not be reached for the upload" >&2; exit 1; }
if [ "$status" != 200 ]; then
  echo "Hangar did not take the upload (HTTP $status):" >&2
  cat "$TMP/answer.json" >&2
  echo >&2
  exit 1
fi
python3 -c 'import json, sys; print("uploaded to Hangar: " + json.load(open(sys.argv[1]))["url"])' "$TMP/answer.json"
