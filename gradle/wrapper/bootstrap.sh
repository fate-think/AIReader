#!/bin/sh
set -eu
version=8.11.1
base=${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$version
home=$base/gradle-$version
if [ ! -x "$home/bin/gradle" ]; then
  mkdir -p "$base"
  archive=$base/gradle-$version-bin.zip
  curl --fail --location --retry 3 --output "$archive" "https://services.gradle.org/distributions/gradle-$version-bin.zip"
  unzip -q -o "$archive" -d "$base"
  rm -f "$archive"
fi
exec "$home/bin/gradle" "$@"
