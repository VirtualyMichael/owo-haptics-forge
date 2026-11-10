#!/bin/sh
# copy shared core (code + web UI page) into every version project
cd "$(dirname "$0")"
for d in owo-*-*; do
  mkdir -p "$d/src/main/java/com/owohaptics" "$d/src/main/resources/owohaptics"
  rm -rf "$d/src/main/java/com/owohaptics/owo" "$d/src/main/java/com/owohaptics/script"
  cp -r core/com/owohaptics/owo core/com/owohaptics/script "$d/src/main/java/com/owohaptics/"
  cp core/resources/owohaptics/ui.html "$d/src/main/resources/owohaptics/ui.html"
done
