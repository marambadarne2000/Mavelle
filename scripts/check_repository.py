"""Reject private runtime/configuration and generated files in publishable sources."""
from pathlib import Path, PurePosixPath
import subprocess
import sys

FORBIDDEN = {'.runtime', '.tools', 'node_modules', 'target', 'dist', 'artifacts', '.test-build', '__pycache__'}

def problem(name: str) -> str | None:
    path = PurePosixPath(name.replace('\\', '/'))
    if any(part in FORBIDDEN for part in path.parts):
        return 'private or generated directory'
    if path.name == '.env' or (path.name.startswith('.env.') and path.name != '.env.example'):
        return 'private environment file'
    if path.suffix.lower() in {'.zip', '.jar', '.class', '.pfx', '.p12', '.key'}:
        return 'binary archive or private key container'
    return None

def main() -> int:
    root = Path(__file__).resolve().parents[1]
    listing = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root, check=True, capture_output=True).stdout
    names = sorted(set(part.decode('utf-8') for part in listing.split(b'\0') if part))
    violations = [(name, reason) for name in names if (reason := problem(name))]
    if violations:
        for name, reason in violations:
            print(f'FAIL: {name}: {reason}')
        return 1
    print(f'PASS: {len(names)} publishable files checked; no private runtime or generated artifacts included.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
