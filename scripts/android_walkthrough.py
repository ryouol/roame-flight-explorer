#!/usr/bin/env python3
"""Run the online/offline/cold-start acceptance flow on one disposable emulator.

Build first: ./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
Stop any existing server on port 8080. Add --record for actual device screen videos.
"""
import argparse
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "travel.roame.takehome.android"
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
ADB = str(SDK / "platform-tools/adb")


def adb(*args, timeout=180):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True, timeout=timeout).stdout


def phase(name, recording, output):
    adb("shell", "am", "force-stop", PACKAGE)
    recorder = None
    remote = f"/sdcard/flight-{name}.mp4"
    try:
        if recording:
            recorder = subprocess.Popen([ADB, "shell", "screenrecord", "--bit-rate", "3000000", "--time-limit", "120", remote])
            time.sleep(1)
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                     f"{PACKAGE}.OfflineWalkthroughTest", "-e", "offlinePhase", name,
                     "-e", "recording", str(recording).lower(),
                     f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner")
        (output / f"android-{name}.txt").write_text(result)
        if "OK (1 test)" not in result:
            raise RuntimeError(f"{name} failed; see {output / f'android-{name}.txt'}")
        print(f"{name}: passed", flush=True)
    finally:
        if recorder:
            pids = adb("shell", "pidof", "screenrecord").strip().split() if recorder.poll() is None else []
            for pid in pids:
                adb("shell", "kill", "-2", pid)
            recorder.wait(timeout=15)
            adb("pull", remote, str(output / f"android-{name}.mp4"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", action="store_true")
    args = parser.parse_args()
    # Refuse to stop an unrelated server; this script owns only the one it starts.
    with socket.socket() as port:
        port.bind(("127.0.0.1", 8080))
    output = ROOT / "work/acceptance"
    output.mkdir(parents=True, exist_ok=True)
    adb("install", "-r", str(ROOT / "androidApp/build/outputs/apk/debug/androidApp-debug.apk"))
    adb("install", "-r", str(ROOT / "androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"))
    with (output / "api.log").open("w") as log:
        server = subprocess.Popen([sys.executable, str(ROOT / "server/server.py")], stdout=log, stderr=log)
        try:
            for _ in range(30):
                try:
                    with urllib.request.urlopen("http://127.0.0.1:8080/flights", timeout=1) as response:
                        if response.status == 200:
                            break
                except OSError:
                    time.sleep(0.1)
            else:
                raise RuntimeError("Local API did not start")
            phase("online", args.record, output)
            server.send_signal(signal.SIGINT)
            server.wait(timeout=10)
            phase("offline", args.record, output)
            phase("cold", args.record, output)
        finally:
            if server.poll() is None:
                server.terminate()
                server.wait(timeout=10)
    print(f"Evidence: {output}")


if __name__ == "__main__":
    main()
