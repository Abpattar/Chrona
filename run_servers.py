#!/usr/bin/env python3
"""Run both backend and frontend servers persistently (Cross-platform Windows/Mac/Linux)."""
import subprocess
import sys
import os
import signal
import shutil

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_DIR = os.path.join(ROOT_DIR, 'backend')

processes = []

def get_python_exe():
    # Check for venv inside backend/
    windows_venv = os.path.join(BACKEND_DIR, 'venv', 'Scripts', 'python.exe')
    unix_venv = os.path.join(BACKEND_DIR, 'venv', 'bin', 'python')
    if os.path.exists(windows_venv):
        return windows_venv
    if os.path.exists(unix_venv):
        return unix_venv
    return sys.executable

def start_backend():
    env = os.environ.copy()
    env['PYTHONPATH'] = BACKEND_DIR
    py_exe = get_python_exe()
    
    cmd = [
        py_exe,
        '-m', 'uvicorn',
        'server:app',
        '--host', '0.0.0.0',
        '--port', '8000',
        '--reload'
    ]
    proc = subprocess.Popen(cmd, cwd=BACKEND_DIR, env=env)
    return proc

def start_frontend():
    # Use npm or npx
    is_win = os.name == 'nt'
    npm_cmd = 'npm.cmd' if is_win else 'npm'
    
    proc = subprocess.Popen([
        npm_cmd, 'run', 'dev'
    ], cwd=ROOT_DIR)
    return proc

def signal_handler(signum, frame):
    print("\nShutting down servers...")
    for p in processes:
        p.terminate()
    for p in processes:
        try:
            p.wait(timeout=5)
        except subprocess.TimeoutExpired:
            p.kill()
            p.wait()
    sys.exit(0)

if __name__ == '__main__':
    signal.signal(signal.SIGINT, signal_handler)
    if hasattr(signal, 'SIGTERM'):
        signal.signal(signal.SIGTERM, signal_handler)

    print(f"Starting Chrona Backend from: {BACKEND_DIR}")
    processes.append(start_backend())

    print(f"Starting Chrona Frontend from: {ROOT_DIR}")
    processes.append(start_frontend())

    print("\n✨ Chrona Servers running:")
    print("  Backend:  http://localhost:8000")
    print("  Frontend: http://localhost:5173")
    print("  API Docs: http://localhost:8000/docs")
    print("\nPress Ctrl+C to stop both servers\n")

    for p in processes:
        p.wait()
