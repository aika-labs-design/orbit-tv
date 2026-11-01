#!/bin/sh
# Renders the 72 Atlas globe frames into app/src/main/assets/web/globe (run from the repo root; needs Google Chrome and ImageMagick).
set -e
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT=app/src/main/assets/web/globe; TMP=$(mktemp -d)
for i in $(seq 0 71); do
  n=$(printf %02d $i)
  "$CH" --headless=new --use-angle=swiftshader --enable-unsafe-swiftshader --hide-scrollbars --window-size=900,900 --virtual-time-budget=6000 --allow-file-access-from-files --screenshot="$TMP/f$n.png" "file://$PWD/tools/globe/render.html#$i" 2>/dev/null
  magick "$TMP/f$n.png" -quality 82 "$OUT/f$n.jpg"
done
rm -rf "$TMP"
