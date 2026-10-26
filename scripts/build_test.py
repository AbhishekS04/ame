#!/usr/bin/env python3
"""Build and optionally install Ame's local dev APK without uninstalling Ame."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.abhishek.ame"


def run(command: list[str], *, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(command), flush=True)
    return subprocess.run(command, cwd=ROOT, env=env, check=True, text=True)


def find_sdk() -> Path | None:
    candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "Android/Sdk"),
        "/opt/android-sdk",
    ]
    properties = ROOT / "local.properties"
    if properties.exists():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidates.insert(0, line.partition("=")[2])
    for candidate in candidates:
        if not candidate:
            continue
        path = Path(candidate).expanduser()
        if (path / "platforms").is_dir() or (path / "build-tools").is_dir():
            return path
    return None


def configure_sdk(sdk: Path, env: dict[str, str]) -> None:
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    properties = ROOT / "local.properties"
    if not properties.exists():
        properties.write_text(f"sdk.dir={sdk}\n", encoding="utf-8")
        return
    lines = properties.read_text(encoding="utf-8").splitlines()
    replaced = False
    updated: list[str] = []
    for line in lines:
        if line.startswith("sdk.dir="):
            updated.append(f"sdk.dir={sdk}")
            replaced = True
        else:
            updated.append(line)
    if not replaced:
        updated.insert(0, f"sdk.dir={sdk}")
    properties.write_text("\n".join(updated) + "\n", encoding="utf-8")


def connected_device(adb: str) -> str | None:
    run([adb, "start-server"])
    result = subprocess.run([adb, "devices"], cwd=ROOT, capture_output=True, text=True, check=True)
    devices = []
    for line in result.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            devices.append(fields[0])
        elif len(fields) >= 2 and fields[1] == "unauthorized":
            print("ADB device is unauthorized. Accept the USB debugging prompt on the phone.", file=sys.stderr)
    if len(devices) > 1:
        print("Multiple devices detected; using the first one:", devices[0])
    return devices[0] if devices else None


def main() -> int:
    env = os.environ.copy()
    sdk = find_sdk()
    if sdk is None:
        print("Android SDK not found. Set ANDROID_HOME or install platform-tools/SDK first.", file=sys.stderr)
        return 2
    configure_sdk(sdk, env)

    cargo_bin = Path.home() / ".cargo/bin"
    if cargo_bin.is_dir():
        env["PATH"] = f"{cargo_bin}:{env.get('PATH', '')}"

    run(["./gradlew", "--no-daemon", ":app:assembleDev"], env=env)

    adb = shutil.which("adb", path=env["PATH"]) or str(sdk / "platform-tools/adb")
    serial = connected_device(adb)
    if serial is None:
        print("Build succeeded, but no authorized ADB device was detected.")
        print("Connect the phone, enable USB debugging, accept the prompt, then run this command again:")
        print("  python3 scripts/build_test.py")
        return 0

    abi = subprocess.run(
        [adb, "-s", serial, "shell", "getprop", "ro.product.cpu.abi"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    apk_name = "app-arm64-v8a-dev.apk" if abi.startswith("arm64") else "app-universal-dev.apk"
    apk = ROOT / "app/build/outputs/apk/dev" / apk_name
    if not apk.is_file():
        raise FileNotFoundError(apk)

    # -r replaces the APK while preserving the existing GitHub installation's
    # app data. There is deliberately no uninstall fallback here.
    run([adb, "-s", serial, "install", "-r", str(apk)])
    run([adb, "-s", serial, "shell", "monkey", "-p", PACKAGE, "1"])
    print(f"Installed and launched {apk.name} on {serial}; existing app data was preserved.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
