#!/usr/bin/env python3
"""Run both backend and frontend servers persistently."""
import subprocess
import sys
import os
import signal
import time

processes = []

def start_backend():
    env = os.environ.copy()
    env['PYTHONPATH'] = '/home/neo/Codes/neuravex/backend'
    proc = subprocess.Popen([
        '/home/neo/Codes/neuravex/backend/venv/bin/uvicorn',
        'server:app',
        '--host', '0.0.0.0',
        '--port', '8000',
        '--reload'
    ], cwd='/home/neo/Codes/neuravex/backend', env=env)
    return proc

def start_frontend():
    proc = subprocess.Popen([
        './node_modules/.bin/vite',
        '--host', '0.0.0.0',
        '--port', '5173'
    ], cwd='/home/neo/Codes/neuravex')
    return proc

def signal_handler(signum, frame):
    print("\nShutting down servers...")
    for p in processes:
        p.terminate()
    for p in processes:
        try:
            p.wait(timeout=10)
        except subprocess.TimeoutExpired:
            print(f"Force killing process {p.pid}...")
            p.kill()
            p.wait()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

print("Starting backend...")
processes.append(start_backend())

print("Starting frontend...")
processes.append(start_frontend())

print("Servers running:")
print("  Backend:  http://localhost:8000")
print("  Frontend: http://localhost:5173")
print("  API Docs: http://localhost:8000/docs")
print("\nPress Ctrl+C to stop")

# Wait for all processes
for p in processes:
    p.wait()