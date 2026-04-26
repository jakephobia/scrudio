# -*- coding: utf-8 -*-
"""Simple JSON-on-disk cache with TTL.

Files live in `special://profile/addon_data/plugin.video.scrudio/cache/`.
Each cache file is named after the SHA1 of the cache key, contents:
{"ts": <unix_seconds>, "data": <json_payload>}.

Designed for TMDB responses. Not safe for binary blobs.
"""
from __future__ import annotations

import hashlib
import json
import os
import time
from typing import Any, Optional

import xbmcvfs

from .kodi import PROFILE_PATH, log_error, log_warning

CACHE_DIR = os.path.join(PROFILE_PATH, 'cache')


def _ensure_dir() -> None:
    if not xbmcvfs.exists(CACHE_DIR):
        # xbmcvfs.mkdirs handles nested creation across special:// paths
        xbmcvfs.mkdirs(CACHE_DIR)


def _key_to_path(key: str) -> str:
    h = hashlib.sha1(key.encode('utf-8')).hexdigest()
    return os.path.join(CACHE_DIR, f'{h}.json')


def get(key: str, ttl_seconds: int) -> Optional[Any]:
    """Return cached value if present and not expired, else None."""
    if ttl_seconds <= 0:
        return None
    path = _key_to_path(key)
    if not os.path.exists(path):
        return None
    try:
        with open(path, 'r', encoding='utf-8') as f:
            wrapped = json.load(f)
        if not isinstance(wrapped, dict) or 'ts' not in wrapped or 'data' not in wrapped:
            return None
        if time.time() - float(wrapped['ts']) > ttl_seconds:
            return None
        return wrapped['data']
    except (OSError, ValueError) as e:
        log_warning(f'Cache read failed for {key!r}: {e}')
        return None


def set(key: str, data: Any) -> None:  # noqa: A001 — shadow ok in module scope
    """Persist a JSON-serializable value under the given key."""
    try:
        _ensure_dir()
        path = _key_to_path(key)
        # Atomic-ish write: write to .tmp then rename.
        tmp = path + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump({'ts': time.time(), 'data': data}, f, ensure_ascii=False)
        os.replace(tmp, path)
    except (OSError, TypeError, ValueError) as e:
        log_error(f'Cache write failed for {key!r}: {e}')


def delete(key: str) -> None:
    path = _key_to_path(key)
    try:
        if os.path.exists(path):
            os.remove(path)
    except OSError as e:
        log_warning(f'Cache delete failed for {key!r}: {e}')


def clear() -> int:
    """Remove every cache file. Returns the number of files removed."""
    if not os.path.exists(CACHE_DIR):
        return 0
    removed = 0
    try:
        for name in os.listdir(CACHE_DIR):
            full = os.path.join(CACHE_DIR, name)
            try:
                os.remove(full)
                removed += 1
            except OSError:
                pass
    except OSError as e:
        log_error(f'Cache clear failed: {e}')
    return removed


def size_bytes() -> int:
    if not os.path.exists(CACHE_DIR):
        return 0
    total = 0
    for name in os.listdir(CACHE_DIR):
        full = os.path.join(CACHE_DIR, name)
        try:
            total += os.path.getsize(full)
        except OSError:
            pass
    return total
