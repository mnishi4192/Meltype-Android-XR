#!/usr/bin/env python3
"""Inspect the CPU-only Android APK's model, native libraries and notices."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
MODEL = "assets/models/zenz-v3.2-small-Q5_K_M.gguf"
MODEL_SHA = "29c223d4c23327b80fd13ebb5ab2555057a46317997d5da391584ffbef0db673"
SYSTEM_LIBS = {"libandroid.so", "liblog.so", "libm.so", "libdl.so", "libc.so"}
NATIVE_CLASS = "Java_local_mnishi_meltype_android_core_NativeCore_"
CORE_LIBRARY = "lib/arm64-v8a/libmeltype_core.so"
OFFICIAL_CORE = "lib/arm64-v8a/libMeltypeNative.so"
OFFICIAL_BRIDGE = "lib/arm64-v8a/libmeltype_official_jni.so"
MOZC_LIBRARY = "lib/arm64-v8a/libmozc.so"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def text(command):
    return subprocess.check_output([str(part) for part in command], text=True)


def path_label(path):
    return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)


def sdk_property():
    path = ROOT / "local.properties"
    if path.exists():
        for line in path.read_text().splitlines():
            if line.startswith("sdk.dir="):
                return line.split("=", 1)[1].replace("\\ ", " ").replace("\\:", ":").replace("\\\\", "\\")
    return None


def native_library(info, data, apk_file, archive_names, work, readelf, nm):
    local = work / Path(info.filename).name
    local.write_bytes(data)
    phoff = struct.unpack_from("<Q", data, 32)[0]
    stride, count = struct.unpack_from("<HH", data, 54)
    loads = []
    for index in range(count):
        p = struct.unpack_from("<IIQQQQQQ", data, phoff + index * stride)
        if p[0] == 1:
            loads.append({"offset": p[2], "vaddr": p[3], "alignment": p[7],
                          "compatible_16kb": p[7] >= 16384 and p[2] % 16384 == p[3] % 16384})
    needed = re.findall(r"Shared library: \[(.*?)\]", text([readelf, "-d", local]))
    apk_file.seek(info.header_offset)
    name_size, extra_size = struct.unpack_from("<HH", apk_file.read(30), 26)
    offset = info.header_offset + 30 + name_size + extra_size
    result = {
        "entry": info.filename, "sha256": sha256(data), "bytes": len(data),
        "elf64_aarch64": data[:5] == b"\x7fELF\x02" and struct.unpack_from("<H", data, 18)[0] == 183,
        "zip_stored": info.compress_type == zipfile.ZIP_STORED,
        "zip_data_offset": offset, "zip_aligned_16kb": offset % 16384 == 0,
        "load_segments": loads, "needed": needed,
        "unpackaged_non_system_dependencies": [name for name in needed
            if name not in SYSTEM_LIBS and f"lib/arm64-v8a/{name}" not in archive_names],
    }
    if info.filename == CORE_LIBRARY:
        symbols = text([nm, "-D", "--defined-only", local])
        result.update({
            "jni_entrypoints": {name: f"{NATIVE_CLASS}{name}" in symbols
                for name in ["nativeCreate", "nativeGenerate", "nativeBackend", "nativeDestroy"]},
            "cpu_backend_symbol_exported": "ggml_backend_cpu_reg" in symbols,
            "obsolete_jni_exports_absent": "Java_local_mnishi_meltype_android_NativeInference_" not in symbols,
            "gpu_backend_symbols_absent": not re.search(r"ggml_backend_(?:vk|vulkan|cuda|opencl|metal|sycl|webgpu|hexagon)_", symbols, re.IGNORECASE),
            "gpu_loader_dependencies_absent": not any(re.search(r"vulkan|opencl|cuda|metal|hexagon", name, re.IGNORECASE) for name in needed),
            "embedded_spirv_modules_magic_count": data.count(b"\x03\x02\x23\x07"),
        })
    elif info.filename == OFFICIAL_CORE:
        symbols = text([nm, "-D", "--defined-only", local])
        result["official_ime_exports"] = {name: name in symbols for name in (
            "meltype_android_init_with_zenzai", "meltype_set_conversion_ready", "meltype_ime_handle",
            "meltype_ime_create", "meltype_ime_destroy", "meltype_free")}
    return result


def audit(args, sdk, ndk):
    tools = max((sdk / "build-tools").iterdir(), key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))
    suffix = ".exe" if os.name == "nt" else ""
    aapt, zipalign = tools / ("aapt" + suffix), tools / ("zipalign" + suffix)
    host_bin = next((ndk / "toolchains/llvm/prebuilt").glob("*/bin"))
    readelf, nm = host_bin / ("llvm-readelf" + suffix), host_bin / ("llvm-nm" + suffix)
    apk = args.apk.resolve()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    work = args.output.parent / "native-apk-audit"
    work.mkdir(exist_ok=True)
    report = {"apk": path_label(apk), "apk_sha256": sha256(apk.read_bytes()), "inference_backend": "CPU",
              "apk_bytes": apk.stat().st_size, "native_libraries": [], "licenses": [], "findings": []}
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as apk_file:
        model = archive.read(MODEL)
        report["model"] = {"asset": MODEL, "bytes": len(model), "sha256": sha256(model),
            "matches_pinned_model": sha256(model) == MODEL_SHA,
            "stored_uncompressed": archive.getinfo(MODEL).compress_type == zipfile.ZIP_STORED}
        source = json.loads((ROOT / "vendor/meltype-official/SOURCE.json").read_text())
        expected = source["android_mozc"]["extracted_sha256"]
        report["official_core_commit"] = source["meltype"]["commit"]
        report["pinned_mozc_assets"] = {name: sha256(archive.read(name)) == digest for name, digest in expected.items()}
        for info in archive.infolist():
            if info.filename.startswith("assets/licenses/"):
                data = archive.read(info)
                report["licenses"].append({"asset": info.filename, "bytes": len(data), "sha256": sha256(data),
                    "matches_current_asset": data == (ROOT / "app/src/main" / info.filename).read_bytes()})
            if info.filename.startswith("lib/"):
                report["native_libraries"].append(native_library(info, archive.read(info), apk_file,
                    archive.namelist(), work, readelf, nm))
        notice = archive.read("assets/licenses/native-dependencies-NOTICE.txt").decode()
        report["cpp_runtime_notices_present"] = all(name in notice for name in [
            "The libc++ library is dual licensed", "The libc++abi library is dual licensed",
            "The LLVM Project is under the Apache License v2.0 with LLVM Exceptions:"])
    permissions = text([aapt, "dump", "permissions", apk])
    report["internet_permission_absent"] = "android.permission.INTERNET" not in permissions
    report["declared_permissions"] = permissions.strip().splitlines()[1:]
    report["zipalign_16kb_pass"] = subprocess.run([str(zipalign), "-c", "-P", "16", "4", str(apk)], capture_output=True).returncode == 0
    report["badging"] = text([aapt, "dump", "badging", apk]).splitlines()[:3]
    configs = [ROOT / name for name in ["app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts",
        "core-native/CMakeLists.txt", "vendor/meltype-official/android/mozc/build.gradle.kts"]]
    references = [{"file": path_label(path), "line": number, "text": line} for path in configs
        for number, line in enumerate(path.read_text().splitlines(), 1)
        if "/Users/" in line or "/Documents/ChatGPT/" in line]
    symlinks = [path_label(path) for base in [ROOT / "app/src", ROOT / "core-native", ROOT / "vendor"] for path in base.rglob("*") if path.is_symlink()]
    report["build_reference_audit"] = {"files": [path_label(p) for p in configs],
        "absolute_other_project_refs": references, "source_symlinks": symlinks,
        "finding": "Native source inputs resolve within this Android project; system SDK/CMake tools are separate from source inputs."}
    patch = ROOT / "vendor/patches/zenz-v3-tokenizer.patch"
    report["zenzai_tokenizer_patch"] = {"path": path_label(patch), "sha256": sha256(patch.read_bytes()),
        "source_sha256": sha256((ROOT / "vendor/llama.cpp/src/llama-vocab.cpp").read_bytes())}
    checks = {
        "pinned_model": report["model"]["matches_pinned_model"] and report["model"]["stored_uncompressed"],
        "zip_alignment": report["zipalign_16kb_pass"], "runtime_notices": report["cpp_runtime_notices_present"],
        "licenses": len(report["licenses"]) >= 5 and all(x["matches_current_asset"] for x in report["licenses"]),
        "no_internet_permission": report["internet_permission_absent"], "project_isolation": not references and not symlinks,
        "native_libraries": {lib["entry"] for lib in report["native_libraries"]} == {
            CORE_LIBRARY, OFFICIAL_CORE, OFFICIAL_BRIDGE, MOZC_LIBRARY, "lib/arm64-v8a/libc++_shared.so"},
        "pinned_mozc": all(report["pinned_mozc_assets"].values()),
        "old_native_library_absent": not any("meltype_inference" in lib["entry"] for lib in report["native_libraries"]),
    }
    for lib in report["native_libraries"]:
        checks[lib["entry"]] = lib["elf64_aarch64"] and lib["zip_stored"] and lib["zip_aligned_16kb"] and all(
            segment["compatible_16kb"] for segment in lib["load_segments"]) and not lib["unpackaged_non_system_dependencies"]
        if "jni_entrypoints" in lib:
            checks["cpu_only_backend"] = all(lib["jni_entrypoints"].values()) and lib["cpu_backend_symbol_exported"] and all(
                lib[name] for name in ["obsolete_jni_exports_absent", "gpu_backend_symbols_absent", "gpu_loader_dependencies_absent"])
        if "official_ime_exports" in lib:
            checks["official_ime_exports"] = all(lib["official_ime_exports"].values())
        if lib["entry"] == OFFICIAL_BRIDGE:
            checks["official_core_jni_linked"] = "libMeltypeNative.so" in lib["needed"]
    report["findings"] = [name for name, passed in checks.items() if not passed]
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"report": str(args.output), "apk_sha256": report["apk_sha256"],
        "apk_bytes": report["apk_bytes"], "findings": report["findings"]}, indent=2))
    return bool(report["findings"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/native-apk-audit.json")
    parser.add_argument("--sdk", type=Path, help="Android SDK; defaults to environment or local.properties")
    parser.add_argument("--ndk", type=Path, default=ROOT / ".tools/android-sdk/ndk/30.0.16248370")
    args = parser.parse_args()
    sdk = args.sdk or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or sdk_property()
    if not sdk:
        parser.error("Set --sdk, ANDROID_HOME, ANDROID_SDK_ROOT or sdk.dir in local.properties")
    return audit(args, Path(sdk), args.ndk)


if __name__ == "__main__":
    raise SystemExit(main())
