"""Locate the JDK and Android SDK, and fetch the one pinned helper jar."""

import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path


APKTOOL_VERSION = "3.0.3"
APKTOOL_URL = (
    "https://github.com/iBotPeaches/Apktool/releases/download/"
    "v%s/apktool_%s.jar" % (APKTOOL_VERSION, APKTOOL_VERSION)
)
APKTOOL_SHA256 = "dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423"


class ToolError(RuntimeError):
    pass


@dataclass(frozen=True)
class Jdk:
    java: Path
    javac: Path
    keytool: Path


@dataclass(frozen=True)
class Sdk:
    root: Path
    android_jar: Path
    d8: Path
    zipalign: Path
    apksigner: Path


def _version_key(path: Path) -> tuple[int, ...]:
    parts = []
    for chunk in path.name.replace("android-", "").split("."):
        try:
            parts.append(int(chunk))
        except ValueError:
            parts.append(0)
    return tuple(parts)


def find_jdk() -> Jdk:
    home = os.environ.get("JAVA_HOME")
    if home:
        base = Path(home) / "bin"
    else:
        java = shutil.which("java")
        if not java:
            raise ToolError("java was not found; install a JDK 17 or newer")
        base = Path(java).resolve().parent
    java = base / "java"
    javac = base / "javac"
    keytool = base / "keytool"
    for tool in (java, javac, keytool):
        if not tool.is_file():
            raise ToolError("%s was not found; a full JDK is required" % tool)
    return Jdk(java, javac, keytool)


def find_sdk() -> Sdk:
    candidates = []
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value:
            candidates.append(Path(value))
    candidates.extend([
        Path.home() / "android-sdk",
        Path.home() / "Android" / "Sdk",
        Path("/opt/android-sdk"),
        Path("/usr/lib/android-sdk"),
    ])
    root = next((path for path in candidates if (path / "platforms").is_dir()), None)
    if root is None:
        raise ToolError("Android SDK not found; set ANDROID_HOME")

    platforms = [path for path in (root / "platforms").iterdir()
                 if (path / "android.jar").is_file()]
    if not platforms:
        raise ToolError("no platform with android.jar under %s/platforms" % root)
    android_jar = sorted(platforms, key=_version_key)[-1] / "android.jar"

    build_tools = sorted(
        (path for path in (root / "build-tools").iterdir()
         if (path / "d8").is_file()
         and (path / "zipalign").is_file()
         and (path / "apksigner").is_file()),
        key=_version_key,
    )
    if not build_tools:
        raise ToolError("no build-tools with d8, zipalign and apksigner under %s" % root)
    chosen = build_tools[-1]
    return Sdk(root, android_jar, chosen / "d8", chosen / "zipalign", chosen / "apksigner")


def ensure_apktool(tools: Path) -> Path:
    tools.mkdir(parents=True, exist_ok=True)
    target = tools / ("apktool_%s.jar" % APKTOOL_VERSION)
    if target.is_file() and _digest(target) == APKTOOL_SHA256:
        return target
    print("Downloading apktool %s..." % APKTOOL_VERSION)
    handle, name = tempfile.mkstemp(prefix="apktool-", suffix=".jar", dir=str(tools))
    os.close(handle)
    partial = Path(name)
    try:
        request = urllib.request.Request(APKTOOL_URL, headers={"User-Agent": "OpenCloud builder"})
        with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as sink:
            shutil.copyfileobj(response, sink)
        found = _digest(partial)
        if found != APKTOOL_SHA256:
            raise ToolError("apktool checksum mismatch: expected %s, got %s"
                            % (APKTOOL_SHA256, found))
        partial.replace(target)
    except Exception:
        if partial.exists():
            partial.unlink()
        raise
    return target


def _digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def run(command: list[str], cwd: Path | None = None) -> None:
    printable = " ".join(str(part) for part in command)
    print("  %s" % printable)
    completed = subprocess.run(command, cwd=str(cwd) if cwd else None,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True)
    if completed.returncode != 0:
        if completed.stdout:
            sys.stderr.write(completed.stdout)
        raise ToolError("command failed with exit code %d: %s"
                        % (completed.returncode, printable))
