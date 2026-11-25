#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Export a standalone source tree and a deterministic ZIP for GitHub."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MODEL = Path("app/src/main/assets/models/zenz-v3.2-small-Q5_K_M.gguf")
WRAPPER = Path("gradle/wrapper/gradle-wrapper.jar")
ROOT_FILES = (
    ".gitignore", "LICENSE", "README.md", "app/build.gradle.kts", "build.gradle.kts",
    "gradle.properties", "gradle/wrapper/gradle-wrapper.properties", "gradlew", "gradlew.bat", "settings.gradle.kts",
    "vendor/PATCHES.md", "vendor/SOURCE.json",
)
OPTIONAL_ROOT_FILES = ("CHANGELOG.md", "NOTICE", "NOTICE.md", "THIRD-PARTY-NOTICES.md")
SOURCE_DIRS = (
    "app/src", "core-native", "gradle/wrapper", "vendor/llama.cpp",
    "vendor/meltype-official", "vendor/patches",
)
PRIVATE_DIRS = {
    ".git", ".github", ".gradle", ".idea", ".kotlin", ".tools", ".vscode",
    ".cxx", "__pycache__", "bin", "build", "dist", "obj", "node_modules",
    "native-apk-audit",
}
PRIVATE_FILES = {
    ".DS_Store", ".env", ".git", ".netrc", ".npmrc", "compile_commands.json",
    "keystore.properties", "local.properties",
}
LOCAL_SUFFIXES = {
    ".a", ".aab", ".apk", ".class", ".dll", ".dylib", ".hprof", ".jks",
    ".keystore", ".key", ".o", ".p12", ".pem", ".pfx", ".pyc", ".so",
}


def source_tree(folder):
    if folder.is_symlink() or not folder.is_dir():
        raise ValueError(f"Required source directory is missing or linked: {folder}")
    for current, directories, names in os.walk(folder):
        directory = Path(current)
        directories[:] = sorted(name for name in directories if name not in PRIVATE_DIRS)
        for name in directories:
            if (directory / name).is_symlink():
                raise ValueError(f"Linked source directory: {directory / name}")
        for name in names:
            file = directory / name
            path = file.relative_to(ROOT)
            suffix = path.suffix.lower()
            if name in PRIVATE_FILES or name.startswith(".env.") or suffix in LOCAL_SUFFIXES:
                continue
            if suffix in {".gguf", ".jar"} and path not in {MODEL, WRAPPER}:
                continue
            yield path


def public_files(version):
    paths = {Path(file) for file in ROOT_FILES} | {MODEL, WRAPPER}
    paths.update(Path(name) for name in OPTIONAL_ROOT_FILES if (ROOT / name).exists())
    for folder in SOURCE_DIRS:
        paths.update(source_tree(ROOT / folder))
    paths.update(file.relative_to(ROOT) for file in (ROOT / "docs").glob("*.md"))
    paths.update(file.relative_to(ROOT) for file in (ROOT / "docs/validation" / version).glob("*.json"))
    paths.update(file.relative_to(ROOT) for file in (ROOT / "tools").iterdir() if file.suffix in {".py", ".sh"})

    for path in sorted(paths):
        file = ROOT / path
        if not file.is_file() or any((ROOT / item).is_symlink() for item in (path, *path.parents)):
            raise ValueError(f"Required source file is missing or linked: {path}")
        if file.stat().st_size > 100 * 1024 * 1024:
            raise ValueError(f"Source file exceeds GitHub's 100 MiB limit: {path}")
        yield path


def build_version():
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'^\s*versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"\s*$', gradle, re.MULTILINE)
    build = re.search(r"^\s*versionCode\s*=\s*([0-9]+)\s*$", gradle, re.MULTILINE)
    if version is None or build is None:
        raise ValueError("Couldn't read versionName and versionCode from app/build.gradle.kts")
    return version[1], build[1]


def export_source(output_dir):
    version, build = build_version()
    name = f"Meltype-Android-XR-{version}-build{build}-source"
    paths = list(public_files(version))
    targets = (output_dir / name, output_dir / f"{name}.zip", output_dir / f"{name}.zip.sha256")
    for target in targets:
        if target.exists() or target.is_symlink():
            raise ValueError(f"Export already exists: {target}. Choose a fresh --output-dir under dist/.")

    output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".source-export-", dir=output_dir) as stage:
        source, archive, checksum = (Path(stage) / target.name for target in targets)
        with zipfile.ZipFile(archive, "w") as package:
            for path in paths:
                original = ROOT / path
                mode = 0o755 if path == Path("gradlew") or original.stat().st_mode & stat.S_IXUSR else 0o644
                exported = source / path
                exported.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(original, exported)
                exported.chmod(mode)
                entry = zipfile.ZipInfo(f"{name}/{path.as_posix()}")
                entry.create_system = 3
                entry.external_attr = (stat.S_IFREG | mode) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                with exported.open("rb") as content, package.open(entry, "w") as zipped:
                    shutil.copyfileobj(content, zipped)
        digest = hashlib.sha256()
        with archive.open("rb") as content:
            for chunk in iter(lambda: content.read(1024 * 1024), b""):
                digest.update(chunk)
        checksum.write_text(f"{digest.hexdigest()}  {archive.name}\n", encoding="utf-8")
        for staged, target in zip((source, archive, checksum), targets):
            staged.rename(target)
    for target in targets:
        print(target.relative_to(ROOT))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=Path("dist/github-release"),
                        help="new export location under the project dist/ directory")
    args = parser.parse_args()
    output_dir = (ROOT / args.output_dir).resolve()
    dist = ROOT / "dist"
    if not output_dir.is_relative_to(dist):
        parser.error("--output-dir must be inside the project's dist/ directory")
    try:
        export_source(output_dir)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
