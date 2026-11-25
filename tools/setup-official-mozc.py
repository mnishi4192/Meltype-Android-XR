#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Import the official Android arm64 Mozc build at the v1.7.1 Mozc pin."""

import hashlib
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS = ROOT / ".tools/downloads"
ARTIFACTS = ROOT / ".tools/official-meltype-artifacts"
APK = DOWNLOADS / "meltype-official-android-preview.1.apk"
APK_URL = "https://github.com/yksr-melt/Meltype/releases/download/android-v0.1.0-preview.1/Meltype.apk"
APK_SHA256 = "a4e46a6cbae6d0e70cd1867e2964cee48876813e01d97b1c9b9a38ee6ee5fe73"
# The signed Android preview source (d5c8e4d89a65fce9c980dec235001e00fb7fbf30)
# and v1.7.1 specify this same revision in android/mozc/MOZC_COMMIT.
MOZC_COMMIT = "a069a88d4cb5c011de0f9aebb6c149a1c808d904"
ENTRIES = {
    "lib/arm64-v8a/libmozc.so": "libmozc.so",
    "assets/mozc.data": "mozc.data",
    "assets/MOZC_NOTICES.txt": "MOZC_NOTICES.txt",
}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as data:
        for block in iter(lambda: data.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def apk_matches(path):
    return path.is_file() and sha256(path) == APK_SHA256


def download_apk():
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    if apk_matches(APK):
        return
    with tempfile.TemporaryDirectory(dir=DOWNLOADS) as work:
        downloaded = Path(work) / APK.name
        subprocess.run(["curl", "-fLSs", "--retry", "2", "--max-time", "180",
                        APK_URL, "-o", downloaded], check=True)
        if not apk_matches(downloaded):
            raise ValueError("The downloaded official APK does not match the pinned SHA-256")
        os.replace(downloaded, APK)


def verify_arm64_alignment(data):
    if data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError("Expected an arm64 ELF library")
    start = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    loads = []
    for index in range(count):
        kind, _, offset, address, _, _, _, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, start + index * entry_size)
        if kind == 1:
            loads.append(alignment >= 16384 and offset % 16384 == address % 16384)
    if not loads or not all(loads):
        raise ValueError("Mozc ELF PT_LOAD segments require 16 KiB alignment")


def main():
    download_apk()
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(APK) as archive, tempfile.TemporaryDirectory(dir=ARTIFACTS) as work:
        work = Path(work)
        for entry, name in ENTRIES.items():
            data = archive.read(entry)
            if not data:
                raise ValueError(f"Missing data in {entry}")
            if name == "libmozc.so":
                verify_arm64_alignment(data)
            (work / name).write_bytes(data)
        for name in ENTRIES.values():
            os.replace(work / name, ARTIFACTS / name)
            print(f"{sha256(ARTIFACTS / name)}  {ARTIFACTS / name}")
    print(f"Mozc source: {MOZC_COMMIT}")


if __name__ == "__main__":
    main()
