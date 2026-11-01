#!/usr/bin/env bash
# Fails if a secret is tracked or sits anywhere in git history.
# Checks: key values from local.properties (tmdb.apiKey, youtube.apiKey),
# the Google API key pattern, and tracked local.properties / local-profiles.json.
# Only file names and short commit ids are printed, never a matched value.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

fail=0

# 1. Files that must never be tracked (any directory).
tracked=$(git ls-files | awk -F/ '$NF=="local.properties" || $NF=="local-profiles.json"')
if [ -n "$tracked" ]; then
  echo "$tracked" | while IFS= read -r f; do echo "secrets: FOUND tracked file $f"; done
  fail=1
fi

# 2. Key values from local.properties (kept in memory, passed by environment).
export CS_V1="" CS_V2=""
if [ -f local.properties ]; then
  CS_V1=$(sed -n 's/^[[:space:]]*tmdb\.apiKey[[:space:]]*=[[:space:]]*//p' local.properties | head -n1 | tr -d '\r')
  CS_V2=$(sed -n 's/^[[:space:]]*youtube\.apiKey[[:space:]]*=[[:space:]]*//p' local.properties | head -n1 | tr -d '\r')
fi

# Keys shorter than 8 characters would match everything, so they are skipped.
[ "${#CS_V1}" -lt 8 ] && CS_V1=""
[ "${#CS_V2}" -lt 8 ] && CS_V2=""

# 3. Current index (tracked content): values and Google key pattern.
idx=$(
  for val in "$CS_V1" "$CS_V2"; do
    [ -n "$val" ] || continue
    printf '%s\n' "$val" | git grep -I -l --cached -F -f - 2>/dev/null | while IFS= read -r f; do
      echo "secrets: FOUND a local.properties key value in tracked file $f"
    done
  done
  git grep -I -l --cached -E 'AIza[0-9A-Za-z_-]{35}' 2>/dev/null | while IFS= read -r f; do
    echo "secrets: FOUND Google API key pattern in tracked file $f"
  done
)
if [ -n "$idx" ]; then echo "$idx"; fail=1; fi

# 4. Whole history, every ref: added or removed lines in any commit.
hist=$(git log --all -m -p --no-color --format='commit %h' 2>/dev/null | awk '
  /^commit [0-9a-f]+$/ { c = $2; next }
  /^diff --git / { f = $4; sub(/^b\//, "", f); next }
  {
    hit = ""
    if (ENVIRON["CS_V1"] != "" && index($0, ENVIRON["CS_V1"])) hit = "key value"
    else if (ENVIRON["CS_V2"] != "" && index($0, ENVIRON["CS_V2"])) hit = "key value"
    else if ($0 ~ /AIza[0-9A-Za-z_-]+/ && match($0, /AIza[0-9A-Za-z_-]+/) && RLENGTH >= 39) hit = "Google API key pattern"
    if (hit != "") { k = c SUBSEP f SUBSEP hit; if (!(k in seen)) { seen[k] = 1; print "secrets: FOUND " hit " in history, commit " c ", file " f } }
  }')
if [ -n "$hist" ]; then echo "$hist"; fail=1; fi

if [ "$fail" -eq 0 ]; then echo "secrets: clean"; fi
exit "$fail"
