#!/usr/bin/env bash
# Project-local tools for the CPU-only Android native core.
set -euo pipefail

task_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
task_ndk="$task_root/.tools/android-sdk/ndk/30.0.16248370"
if [[ "$(uname -s)" != Darwin ]]; then
  echo 'This helper installs the macOS NDK. On other hosts, install NDK 30.0.16248370 under .tools/android-sdk/ndk/.' >&2
  exit 1
fi

# Gradle's externalNativeBuild uses Ninja. Pin the official universal macOS
# release and verify the digest published in its GitHub release asset metadata.
task_ninja_archive="$task_root/.tools/downloads/ninja-mac-v1.13.2.zip"
mkdir -p -- "$task_root/.tools/bin" "$task_root/.tools/downloads"
if [[ ! -f "$task_ninja_archive" ]]; then
  curl --fail --location --retry 3 'https://github.com/ninja-build/ninja/releases/download/v1.13.2/ninja-mac.zip' --output "$task_ninja_archive"
fi
task_ninja_sha="$(shasum -a 256 "$task_ninja_archive" | cut -d ' ' -f 1)"
if [[ "$task_ninja_sha" != c99048673aa765960a99cf10c6ddb9f1fad506099ff0a0e137ad8960a88f321b ]]; then
  echo 'Ninja archive checksum mismatch. Remove .tools/downloads/ninja-mac-v1.13.2.zip and retry.' >&2
  exit 1
fi
unzip -oq "$task_ninja_archive" -d "$task_root/.tools/bin"
chmod +x "$task_root/.tools/bin/ninja"

if [[ -f "$task_ndk/source.properties" ]]; then
  echo "$task_ndk"
  exit 0
fi
task_archive="$task_root/.tools/downloads/android-ndk-r30-darwin.zip"
mkdir -p -- "$(dirname -- "$task_archive")" "$(dirname -- "$task_ndk")"
if [[ ! -f "$task_archive" ]]; then
  curl --fail --location --retry 3 'https://dl.google.com/android/repository/android-ndk-r30-darwin.zip' --output "$task_archive"
fi
task_sha="$(shasum -a 1 "$task_archive" | cut -d ' ' -f 1)"
if [[ "$task_sha" != c060be96767eefbb8e0a27796d6f43115fc1a0c4 ]]; then
  echo 'NDK archive checksum mismatch. Remove .tools/downloads/android-ndk-r30-darwin.zip and retry.' >&2
  exit 1
fi
unzip -q "$task_archive" -d "$(dirname -- "$task_ndk")"
mv -- "$(dirname -- "$task_ndk")/android-ndk-r30" "$task_ndk"
echo "$task_ndk"
