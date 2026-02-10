"""Entry point for PyInstaller standalone binary: delegates to modelaudit CLI."""
import io
import sys

if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

from modelaudit.cli import main

if __name__ == "__main__":
    main()
