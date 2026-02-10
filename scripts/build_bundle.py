#!/usr/bin/env python3
"""
Build a standalone modelaudit binary with PyInstaller for use in the Kotlin library.

Requires: uv sync (or pip install -e .) and pyinstaller in the environment.
  uv run pip install pyinstaller
  uv run python scripts/build_bundle.py

Output: dist-bundle/modelaudit/ (onedir) is zipped to
  modelaudit-kotlin/src/main/resources/io/modelaudit/bins/<os>-<arch>/modelaudit.zip
so the Kotlin JAR can include the bundle. Uses onedir to avoid PyInstaller 32-bit archive limit on Linux.
Platform key examples: linux-x64, linux-aarch64, macos-x64, macos-aarch64, windows-x64.
"""
from __future__ import annotations

import platform
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    try:
        import PyInstaller.__main__ as pyi
    except ImportError:
        print("PyInstaller not found. Install with: uv run pip install pyinstaller", file=sys.stderr)
        return 1

    entry = REPO_ROOT / "scripts" / "standalone_entry.py"
    if not entry.exists():
        print(f"Entry script not found: {entry}", file=sys.stderr)
        return 1

    os_raw = platform.system().lower()
    os_name = "macos" if os_raw == "darwin" else os_raw
    arch = platform.machine().lower()
    if arch in ("x86_64", "amd64"):
        arch = "x64"
    elif arch in ("aarch64", "arm64"):
        arch = "aarch64"
    platform_key = f"{os_name}-{arch}"

    # Use --onedir to avoid PyInstaller 32-bit archive limit (struct.error on large bundles)
    args = [
        str(entry),
        "--onedir",
        "--name=modelaudit",
        "--clean",
        f"--distpath={REPO_ROOT / 'dist-bundle'}",
        f"--workpath={REPO_ROOT / 'build-bundle'}",
        f"--specpath={REPO_ROOT / 'build-bundle'}",
        "--collect-all=modelaudit",
        "--collect-data=yaspin",
        "--hidden-import=modelaudit",
        "--hidden-import=modelaudit.cli",
        "--hidden-import=modelaudit.core",
        "--noconfirm",
    ]

    print(f"Building standalone binary for {platform_key}...")
    sys.argv = ["pyinstaller"] + args
    pyi.run()

    import shutil

    bundle_dir = REPO_ROOT / "dist-bundle" / "modelaudit"
    exe_name = "modelaudit.exe" if os_raw == "windows" else "modelaudit"
    if not (bundle_dir / exe_name).exists():
        print(f"Expected binary not found: {bundle_dir / exe_name}", file=sys.stderr)
        return 1

    out_dir = REPO_ROOT / "modelaudit-kotlin" / "src" / "main" / "resources" / "io" / "modelaudit" / "bins" / platform_key
    out_dir.mkdir(parents=True, exist_ok=True)
    zip_path = out_dir / "modelaudit.zip"
    # Zip so that unzip gives one top-level "modelaudit/" dir with exe inside
    shutil.make_archive(str(zip_path.with_suffix("")), "zip", REPO_ROOT / "dist-bundle", "modelaudit")
    print(f"Created {zip_path}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
