#!/usr/bin/env python3
"""
Build a standalone modelaudit binary with PyInstaller for use in the Kotlin library.

Requires: uv sync (or pip install -e .) and pyinstaller in the environment.
  uv run pip install pyinstaller
  uv run python scripts/build_bundle.py

Output: dist/modelaudit (or modelaudit.exe on Windows). Copy to
  modelaudit-kotlin/src/main/resources/io/modelaudit/bins/<os>-<arch>/
so the Kotlin JAR can include the bundled binary. Platform key examples:
  linux-x64, linux-aarch64, macos-x64, macos-aarch64, windows-x64.
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
    if arch == "x86_64":
        arch = "x64"
    elif arch in ("aarch64", "arm64"):
        arch = "aarch64"
    platform_key = f"{os_name}-{arch}"

    args = [
        str(entry),
        "--onefile",
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

    out_dir = REPO_ROOT / "modelaudit-kotlin" / "src" / "main" / "resources" / "io" / "modelaudit" / "bins" / platform_key
    out_dir.mkdir(parents=True, exist_ok=True)
    exe_name = "modelaudit.exe" if os_raw == "windows" else "modelaudit"
    src = REPO_ROOT / "dist-bundle" / exe_name
    dst = out_dir / exe_name
    if src.exists():
        import shutil
        shutil.copy2(src, dst)
        print(f"Copied to {dst}")
    else:
        print(f"Expected binary not found: {src}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
