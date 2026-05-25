"""
Flask session secret handler — persistent across restarts.

Usage:
    from session_secret import load_or_create_secret_key
    app.secret_key = load_or_create_secret_key(Path("data/.secret_key"))
"""
import os
import secrets
from pathlib import Path
from typing import Mapping


def load_or_create_secret_key(secret_path: Path, environ: Mapping[str, str] | None = None) -> str:
    env = environ if environ is not None else os.environ
    env_secret = env.get("SECRET_KEY", "").strip()
    if env_secret:
        return env_secret

    secret_path = Path(secret_path)
    if secret_path.exists():
        existing = secret_path.read_text(encoding="utf-8").strip()
        if existing:
            return existing

    secret_path.parent.mkdir(parents=True, exist_ok=True)
    secret = secrets.token_hex(32)
    secret_path.write_text(secret + "\n", encoding="utf-8")
    return secret
