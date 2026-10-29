#!/usr/bin/env python3
import os
import sys
import time
import subprocess

WATCH_DIRS = [
    os.path.abspath("app/src"),
]

EXTENSIONS = {".kt", ".java", ".xml", ".properties", ".gradle", ".kts"}
ADB_PATH = "/home/abhishek/Android/Sdk/platform-tools/adb"
PACKAGE_NAME = "com.abhishek.ame"
MAIN_ACTIVITY = "com.abhishek.ame/.ui.MainActivity"

def get_file_mtimes():
    mtimes = {}
    for d in WATCH_DIRS:
        if not os.path.exists(d):
            continue
        for root, _, files in os.walk(d):
            for f in files:
                ext = os.path.splitext(f)[1].lower()
                if ext in EXTENSIONS:
                    full_path = os.path.join(root, f)
                    try:
                        mtimes[full_path] = os.path.getmtime(full_path)
                    except OSError:
                        pass
    return mtimes

def trigger_rebuild_and_launch(changed_file):
    rel_path = os.path.relpath(changed_file) if os.path.exists(changed_file) else changed_file
    print(f"\n⚡ [LiveSync] Change detected in: {rel_path}", flush=True)
    print("🔨 [LiveSync] Building and installing incremental update...", flush=True)
    t0 = time.time()
    
    # Run gradlew installDebug with warm daemon
    cmd = [
        "./gradlew",
        "installDebug",
        "-Dorg.gradle.daemon=true",
        "-Dkotlin.compiler.execution.strategy=daemon",
        "--offline"
    ]
    env = os.environ.copy()
    env["ANDROID_HOME"] = "/home/abhishek/Android/Sdk"
    
    res = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    elapsed = time.time() - t0
    
    if res.returncode == 0:
        print(f"✅ [LiveSync] Built and installed in {elapsed:.1f}s!", flush=True)
        # Restart activity to apply UI changes immediately
        launch_res = subprocess.run(
            [ADB_PATH, "shell", "am", "start", "-n", MAIN_ACTIVITY, "-S"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        print(f"🚀 [LiveSync] App refreshed on Pixel 10!\n", flush=True)
    else:
        print(f"❌ [LiveSync] Build failed ({elapsed:.1f}s):\n", flush=True)
        # Extract meaningful error lines
        error_lines = [
            line for line in res.stdout.splitlines() 
            if "e: " in line or "error:" in line.lower() or "FAILURE:" in line or "FAILED" in line
        ]
        if error_lines:
            print("\n".join(error_lines[:15]), flush=True)
        else:
            print(res.stdout[-1500:], flush=True)

def main():
    print("=" * 60, flush=True)
    print("  Ame LiveSync: Watching app/src for instant updates", flush=True)
    print("  Device: Pixel 10 (emulator-5554)", flush=True)
    print("=" * 60, flush=True)
    
    # Check adb device
    adb_check = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
    if "emulator-5554" not in adb_check.stdout:
        print("⚠️ Warning: emulator-5554 not found in adb devices! Waiting for device...", flush=True)
    else:
        print("📱 Device connected and ready.", flush=True)

    previous_mtimes = get_file_mtimes()
    print(f"👀 Watching {len(previous_mtimes)} source files. Make your changes!\n", flush=True)

    while True:
        try:
            time.sleep(0.5)
            current_mtimes = get_file_mtimes()
            changed_file = None
            
            for path, mtime in current_mtimes.items():
                if path not in previous_mtimes or mtime > previous_mtimes[path]:
                    changed_file = path
                    break
                    
            if not changed_file:
                for path in previous_mtimes:
                    if path not in current_mtimes:
                        changed_file = path
                        break

            if changed_file:
                # Debounce: wait 0.3s in case more files are written in a batch
                time.sleep(0.3)
                current_mtimes = get_file_mtimes()
                previous_mtimes = current_mtimes
                trigger_rebuild_and_launch(changed_file)
            else:
                previous_mtimes = current_mtimes
        except KeyboardInterrupt:
            print("\nLiveSync stopped.", flush=True)
            break
        except Exception as e:
            print(f"Error in watcher loop: {e}", flush=True)
            time.sleep(1)

if __name__ == "__main__":
    main()
