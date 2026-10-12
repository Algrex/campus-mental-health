#!/usr/bin/env python3
"""Start the local Java/Vue demo with one command; Python standard library only."""
from __future__ import annotations

import argparse
import ctypes
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from urllib.error import URLError
from urllib.request import urlopen
import webbrowser

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / ".tools"
STATE = TOOLS / "demo-processes.json"
JAR = ROOT / "backend/target/campus-mental-health-backend-0.0.1-SNAPSHOT.jar"
IS_WINDOWS = os.name == "nt"
CREATE_FLAGS = subprocess.CREATE_NO_WINDOW if IS_WINDOWS else 0


def birth(pid: int) -> str:
    """Record process identity so a reused PID cannot stop an unrelated process."""
    if IS_WINDOWS:
        from ctypes import wintypes
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            raise OSError(f"Cannot inspect process {pid}")
        values = [wintypes.FILETIME() for _ in range(4)]
        try:
            if not kernel.GetProcessTimes(handle, *(ctypes.byref(value) for value in values)):
                raise OSError("GetProcessTimes failed")
            ticks = (values[0].dwHighDateTime << 32) | values[0].dwLowDateTime
            seconds, fraction = divmod(ticks - 116444736000000000, 10000000)
            stamp = datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(seconds=seconds)
            return stamp.strftime("%Y-%m-%dT%H:%M:%S") + f".{fraction:07d}Z"
        finally:
            kernel.CloseHandle(handle)
    stat = Path(f"/proc/{pid}/stat")
    if stat.exists():
        return stat.read_text().rsplit(")", 1)[1].split()[19]
    return subprocess.check_output(["ps", "-p", str(pid), "-o", "lstart="], text=True).strip()


def stop_recorded() -> None:
    if not STATE.exists():
        print("No recorded demo process.")
        return
    data = json.loads(STATE.read_text(encoding="utf-8-sig"))
    for name in ("backend", "frontend"):
        pid = data.get(name)
        if not pid:
            continue
        try:
            if birth(pid) != data.get(name + "Start"):
                print(f"{name}: PID was reused; left untouched.")
                continue
            os.kill(pid, signal.SIGTERM)
            print(f"{name}: stopped.")
        except (OSError, subprocess.CalledProcessError):
            print(f"{name}: already stopped or inaccessible.")
    time.sleep(0.5)


def java_home() -> Path:
    candidates = []
    local = TOOLS / "environment.local.json"
    if local.exists():
        candidates.append(json.loads(local.read_text(encoding="utf-8-sig")).get("jdkHome", ""))
    candidates.append(os.environ.get("JAVA_HOME", ""))
    candidates.extend(str(path) for path in (TOOLS / "jdk").glob("*"))
    compiler = shutil.which("javac")
    if compiler:
        candidates.append(str(Path(compiler).resolve().parent.parent))
    suffix = ".exe" if IS_WINDOWS else ""
    for candidate in candidates:
        home = Path(candidate) if candidate else None
        if home and (home / f"bin/java{suffix}").is_file() and (home / f"bin/javac{suffix}").is_file():
            version = subprocess.run([str(home / f"bin/java{suffix}"), "-version"], capture_output=True, text=True, creationflags=CREATE_FLAGS)
            if re.search(r'version "21[.\"]', version.stderr + version.stdout):
                return home
    raise RuntimeError("JDK 21 not found. Run scripts/setup.ps1 -JdkHome <jdk-directory> first.")


def check_port(port: int) -> bool:
    with socket.socket() as connection:
        return connection.connect_ex(("127.0.0.1", port)) == 0


def app_ready(port: int) -> bool:
    try:
        with urlopen(f"http://127.0.0.1:{port}/api/v1/system/info", timeout=1) as response:
            return json.load(response).get("name") == "campus-mental-health"
    except (OSError, URLError, ValueError):
        return False


def run(args: argparse.Namespace) -> int:
    if args.stop or args.restart:
        stop_recorded()
        if args.stop:
            return 0
    url = f"http://127.0.0.1:{args.frontend_port}"
    occupied = [check_port(port) for port in (args.backend_port, args.frontend_port)]
    if all(occupied) and all(app_ready(port) for port in (args.backend_port, args.frontend_port)):
        print(f"Demo is already running: {url}")
        if not args.no_browser:
            webbrowser.open(url)
        return 0
    if any(occupied):
        raise RuntimeError("A requested port is occupied. Use --restart for this recorded demo, or choose other ports.")
    node = shutil.which("node")
    if not node:
        raise RuntimeError("Node.js >=24.15 is required.")
    version = subprocess.check_output([node, "--version"], text=True, creationflags=CREATE_FLAGS).strip().lstrip("v")
    if tuple(map(int, version.split(".")[:2])) < (24, 15):
        raise RuntimeError(f"Node {version} is too old; >=24.15 is required.")
    home = java_home()
    env = os.environ.copy()
    env.update(JAVA_HOME=str(home), MAVEN_USER_HOME=str(TOOLS / "maven-user"), SERVER_PORT=str(args.backend_port), SERVER_ADDRESS="127.0.0.1", VITE_DEV_PORT=str(args.frontend_port), VITE_BACKEND_URL=f"http://127.0.0.1:{args.backend_port}")
    logs = TOOLS / "logs"
    logs.mkdir(parents=True, exist_ok=True)
    vite = ROOT / "frontend/node_modules/vite/bin/vite.js"
    if not vite.exists():
        print("Installing locked frontend dependencies…", flush=True)
        npm_cli = Path(node).resolve().parent / "node_modules/npm/bin/npm-cli.js"
        command = [node, str(npm_cli)] if npm_cli.exists() else [shutil.which("npm") or "npm"]
        subprocess.run(command + ["ci", "--offline=false"], cwd=ROOT / "frontend", env=env, check=True, creationflags=CREATE_FLAGS)
    sources = [ROOT / "backend/pom.xml", *[path for path in (ROOT / "backend/src/main").rglob("*") if path.is_file()]]
    if not JAR.exists() or any(path.stat().st_mtime > JAR.stat().st_mtime for path in sources):
        print("Building backend…", flush=True)
        if IS_WINDOWS:
            command = ["pwsh", "-NoProfile", "-File", str(ROOT / "scripts/build-backend.ps1")]
        else:
            command = [str(ROOT / "backend/mvnw"), f"-Dmaven.repo.local={TOOLS / 'm2'}", "-DskipTests", "package"]
        with (logs / "build.log").open("w", encoding="utf-8") as log:
            subprocess.run(command, cwd=ROOT / "backend", env=env, stdout=log, stderr=subprocess.STDOUT, check=True, creationflags=CREATE_FLAGS)
        # Maven reproducible builds may set the JAR mtime to 1980; track this local build.
        JAR.touch()
    runtime = TOOLS / "demo-run/app.jar"
    runtime.parent.mkdir(exist_ok=True)
    shutil.copy2(JAR, runtime)
    processes = []
    handles = []
    state = {}
    try:
        commands = [("backend", [str(home / ("bin/java.exe" if IS_WINDOWS else "bin/java")), "-Xmx1024m", "-jar", "../.tools/demo-run/app.jar"], ROOT / "backend"), ("frontend", [node, "node_modules/vite/bin/vite.js", "--host", "127.0.0.1"], ROOT / "frontend")]
        for name, command, directory in commands:
            handle = (logs / f"{name}.log").open("w", encoding="utf-8")
            handles.append(handle)
            process = subprocess.Popen(command, cwd=directory, env=env, stdout=handle, stderr=subprocess.STDOUT, creationflags=CREATE_FLAGS, start_new_session=not IS_WINDOWS)
            processes.append(process)
            state[name] = process.pid
            state[name + "Start"] = birth(process.pid)
        STATE.write_text(json.dumps(state, indent=2), encoding="utf-8")
        print("Starting demo and training available datasets…", flush=True)
        deadline = time.monotonic() + 120
        while not (app_ready(args.backend_port) and app_ready(args.frontend_port)):
            if any(process.poll() is not None for process in processes):
                raise RuntimeError("A service exited; see .tools/logs/backend.log and frontend.log.")
            if time.monotonic() > deadline:
                raise RuntimeError("Startup timed out; see .tools/logs for details.")
            time.sleep(0.5)
        print(f"Demo ready: {url}\nAccounts: center / counselor / student / contact\nPassword: Campus2026! (or DEMO_PASSWORD)\nCtrl+C to stop; python start.py --stop from another terminal.", flush=True)
        if not args.no_browser:
            webbrowser.open(url)
        while all(process.poll() is None for process in processes):
            time.sleep(1)
        raise RuntimeError("A service exited; see .tools/logs for details.")
    except KeyboardInterrupt:
        print("\nStopping demo…", flush=True)
        return 0
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
        for process in processes:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for handle in handles:
            handle.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-browser", action="store_true")
    parser.add_argument("--stop", action="store_true")
    parser.add_argument("--restart", action="store_true")
    parser.add_argument("--backend-port", type=int, default=8080)
    parser.add_argument("--frontend-port", type=int, default=5173)
    options = parser.parse_args()
    if not all(1 <= port <= 65535 for port in (options.backend_port, options.frontend_port)):
        parser.error("ports must be between 1 and 65535")
    try:
        sys.exit(run(options))
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Startup failed: {error}", file=sys.stderr)
        sys.exit(1)
