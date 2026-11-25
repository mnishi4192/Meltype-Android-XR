#!/usr/bin/env python3
"""Run synthetic conversions with neutral context on the bundled model using host CPU only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/assets/models/zenz-v3.2-small-Q5_K_M.gguf"
BUILD = ROOT / "build/host"
EXPECTED_SHA256 = "29c223d4c23327b80fd13ebb5ab2555057a46317997d5da391584ffbef0db673"
SAMPLES = ("はし", "きょうはいいてんき", "にほんごをにゅうりょくする", "はいりょがひつよう", "へんかんせいどのていか")


def katakana(reading):
    return "".join(chr(ord(c) + 0x60) if "ぁ" <= c <= "ゖ" or c in "ゝゞ" else c for c in reading)


def build():
    cmake = shutil.which("cmake")
    if cmake is None:
        raise RuntimeError("cmake is required to build the host smoke runner")
    BUILD.mkdir(parents=True, exist_ok=True)
    source_commit = json.loads((ROOT / "vendor/SOURCE.json").read_text())["llama.cpp"]["commit"]
    flags = (
        "-DCMAKE_BUILD_TYPE=Release", "-DGGML_METAL=OFF", "-DGGML_VULKAN=OFF",
        "-DGGML_NATIVE=OFF", "-DGGML_ACCELERATE=OFF", "-DGGML_BLAS=OFF",
        "-DGGML_CCACHE=OFF", "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_TOOLS=OFF",
        "-DLLAMA_BUILD_SERVER=OFF", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_COMMON=ON",
        "-DLLAMA_BUILD_EXAMPLES=ON", "-DLLAMA_OPENSSL=OFF", "-DBUILD_SHARED_LIBS=OFF",
        f"-DLLAMA_BUILD_COMMIT={source_commit}", "-DLLAMA_BUILD_NUMBER=0",
    )
    with (BUILD / "configure.log").open("w") as log:
        subprocess.run([cmake, "-S", str(ROOT / "vendor/llama.cpp"), "-B", str(BUILD), *flags],
                       check=True, stdout=log, stderr=subprocess.STDOUT)
    with (BUILD / "build.log").open("w") as log:
        subprocess.run([cmake, "--build", str(BUILD), "--target", "llama-simple", "--parallel", "4"],
                       check=True, stdout=log, stderr=subprocess.STDOUT)


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", action="store_true", help="configure and build llama-simple first")
    args = parser.parse_args()
    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if MODEL.stat().st_size != 73871936 or digest != EXPECTED_SHA256:
        raise RuntimeError("bundled model size or SHA-256 does not match the pinned model")
    if args.build:
        build()
    executable = BUILD / "bin/llama-simple"
    if not executable.is_file():
        raise RuntimeError("host runner is missing; run with --build")
    results = []
    for i, reading in enumerate(SAMPLES):
        prompt = "\uEE02。\uEE00" + katakana(reading) + "\uEE01"
        started = time.monotonic()
        completed = subprocess.run([str(executable), "-m", str(MODEL), "-ngl", "0", "-n", "128", prompt],
                                   capture_output=True, text=True, encoding="utf-8", timeout=120)
        (BUILD / f"smoke-{i}.log").write_text(completed.stderr, encoding="utf-8")
        completed.check_returncode()
        token_count = re.search(r"main: decoded (\d+) tokens", completed.stderr)
        if token_count is None or int(token_count.group(1)) >= 128:
            raise RuntimeError("generation did not finish before the token limit")
        if not completed.stdout.startswith(prompt):
            raise RuntimeError("llama-simple did not echo the expected prompt before the continuation")
        converted = completed.stdout[len(prompt):].strip()
        if not converted or len(converted) > min(128, len(reading) * 3 + 16):
            raise RuntimeError(f"empty or oversized continuation for {reading!r}: {converted!r}")
        if any(unicodedata.category(c) in ("Cc", "Cf", "Co", "Cs", "Zl", "Zp") or c in "<>\ufffd" for c in converted):
            raise RuntimeError(f"invalid continuation for {reading!r}: {converted!r}")
        results.append({"reading": reading, "output": converted, "seconds": round(time.monotonic() - started, 3)})
    report = {"environment": "host CPU; not an Android device validation", "scope": "model loading and generation, not conversion accuracy",
              "model_sha256": digest,
              "gpu_layers": 0, "samples": results}
    (BUILD / "model-smoke.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        run()
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Model smoke check failed: {error}", file=sys.stderr)
        sys.exit(1)
