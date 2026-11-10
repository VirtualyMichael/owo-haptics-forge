#!/bin/sh
# copy shared core into every version project
cd "$(dirname "$0")"
for d in owo-*-*; do
  mkdir -p "$d/src/main/java/com/owohaptics"
  rm -rf "$d/src/main/java/com/owohaptics/owo" "$d/src/main/java/com/owohaptics/script"
  cp -r core/com/owohaptics/owo core/com/owohaptics/script "$d/src/main/java/com/owohaptics/"
done
