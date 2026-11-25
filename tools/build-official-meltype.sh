#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Build the official shared C# core as an Android arm64 NativeAOT library.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${1:-$root/vendor/meltype-official}"
artifacts="${2:-$root/.tools/official-meltype-artifacts}"
project="$source_root/src/Meltype.Core.CApi/Meltype.Core.CApi.csproj"
if [[ ! -f "$project" ]]; then
    printf 'Meltype Core project not found: %s\n' "$project" >&2
    exit 1
fi

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64)
        sdk_host=osx-arm64
        sdk_hash=1c9a13f625d0c08f08ef5a53597bababe3d2473527e98db2199210940c69dd5fc599a39a4d7c30ebf297ca2334a81b2f64b9bf05ff99585de9af4a5d85215e96
        ndk_host=darwin-x86_64
        ;;
    Linux-x86_64)
        sdk_host=linux-x64
        sdk_hash=577d0eb17fec33d029104c5c63a1ab0accc3e28776274abef2218dbac5af18d2109b5406d26b31d76f5c245215e3ba0926a94c20246c672c41847c9e1eb1dc50
        ndk_host=linux-x86_64
        ;;
    *) printf 'Unsupported NativeAOT build host\n' >&2; exit 1 ;;
esac

ndk="${ANDROID_NDK_HOME:-$root/.tools/android-sdk/ndk/30.0.16248370}"
toolchain="$ndk/toolchains/llvm/prebuilt/$ndk_host/bin"
if [[ ! -x "$toolchain/clang" || ! -x "$toolchain/llvm-readelf" ]]; then
    printf 'Set ANDROID_NDK_HOME to a usable Android NDK directory: %s\n' "$ndk" >&2
    exit 1
fi

build="$root/.tools/official-native"
downloads="$root/.tools/downloads"
mkdir -p "$build/nuget" "$downloads" "$artifacts"
export DOTNET_CLI_HOME="$build/dotnet-home"
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
export DOTNET_GENERATE_ASPNET_CERTIFICATE=false
sdk_version=10.0.404
sdk_archive="dotnet-sdk-$sdk_version-$sdk_host.tar.gz"
sdk_dir="$build/dotnet"
dotnet="$sdk_dir/dotnet"
if [[ ! -x "$dotnet" || "$("$dotnet" --version)" != "$sdk_version" ]]; then
    archive="$downloads/$sdk_archive"
    verify_sdk() {
        printf '%s  %s\n' "$sdk_hash" "$1" | shasum -a 512 -c -
    }
    if [[ ! -f "$archive" ]] || ! verify_sdk "$archive"; then
        curl -fLSs --retry 2 --max-time 240 \
            "https://builds.dotnet.microsoft.com/dotnet/Sdk/$sdk_version/$sdk_archive" \
            -o "$archive.download"
        verify_sdk "$archive.download"
        mv -f "$archive.download" "$archive"
    fi
    mkdir -p "$sdk_dir"
    tar -xzf "$archive" -C "$sdk_dir"
fi

# The .NET 10 linker target emits its static archive paths unquoted. Keep
# the cache in this repository and give it a temporary alias without spaces.
alias_dir="$(mktemp -d /tmp/meltype-android-aot.XXXXXXXX)"
remove_alias() {
    rm -f "$alias_dir/nuget"
    rmdir "$alias_dir"
}
trap remove_alias EXIT
ln -s "$build/nuget" "$alias_dir/nuget"

export NUGET_PACKAGES="$alias_dir/nuget"
export PATH="$toolchain:$PATH"
rid=linux-bionic-arm64
"$dotnet" publish "$project" --configuration Release --runtime "$rid" \
    -p:PublishAot=true -p:NativeLib=Shared -p:DisableUnsupportedError=true \
    -p:PublishAotUsingRuntimePack=true -p:CrossCompileAbi=android29 \
    -p:RestoreSources=https://api.nuget.org/v3/index.json

library="$source_root/src/Meltype.Core.CApi/bin/Release/net10.0/$rid/publish/MeltypeNative.so"
metadata="$("$toolchain/llvm-readelf" -h -dW "$library")"
if ! grep -Eq 'Machine:[[:space:]]+AArch64' <<< "$metadata" ||
    ! grep -Fq 'Library soname: [libMeltypeNative.so]' <<< "$metadata"; then
    printf 'NativeAOT output has the wrong ABI or SONAME\n' >&2
    exit 1
fi
segments="$("$toolchain/llvm-readelf" -lW "$library")"
if ! awk '$1 == "LOAD" { count++; if ($NF != "0x4000") invalid=1 } END { exit !count || invalid }' <<< "$segments"; then
    printf 'NativeAOT ELF requires 16 KiB PT_LOAD alignment\n' >&2
    exit 1
fi
cp "$library" "$artifacts/libMeltypeNative.so.tmp"
mv -f "$artifacts/libMeltypeNative.so.tmp" "$artifacts/libMeltypeNative.so"
shasum -a 256 "$artifacts/libMeltypeNative.so"
