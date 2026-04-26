# -*- coding: utf-8 -*-
"""Favorites — JSON-backed list of (media_type, tmdb_id) pairs.

Stored at `special://profile/addon_data/plugin.video.scrudio/favorites.json`.

Each entry is a dict carrying the minimum metadata needed to re-render the
favorite as a browse item (so we don't need to hit TMDB to display the list):
    {
      "media_type":  "movie" | "tv",
      "tmdb_id":     int,
      "imdb_id":     str | "",
      "title":       str,
      "year":        str,
      "plot":        str,
      "poster":      str (URL),
      "fanart":      str (URL),
      "added_at":    float (unix seconds)
    }
"""
from __future__ import annotations

import json
import os
import time
from typing import List, Optional, Set, Tuple

from .kodi import PROFILE_PATH, log_error, log_warning

FAV_FILE = os.path.join(PROFILE_PATH, 'favorites.json')

FavId = Tuple[str, int]  # (media_type, tmdb_id)


def _load() -> List[dict]:
    if not os.path.exists(FAV_FILE):
        return []
    try:
        with open(FAV_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except (OSError, ValueError) as e:
        log_warning(f'Failed to read favorites: {e}')
        return []


def _save(items: List[dict]) -> None:
    try:
        os.makedirs(PROFILE_PATH, exist_ok=True)
        tmp = FAV_FILE + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(items, f, ensure_ascii=False)
        os.replace(tmp, FAV_FILE)
    except OSError as e:
        log_error(f'Failed to save favorites: {e}')


def _key(item: dict) -> Optional[FavId]:
    mt = item.get('media_type')
    try:
        tid = int(item.get('tmdb_id') or 0)
    except (TypeError, ValueError):
        return None
    if mt not in ('movie', 'tv') or not tid:
        return None
    return (mt, tid)


# ── Public API ───────────────────────────────────────────────────────────────
def list_all() -> List[dict]:
    """Return a list of favorite entries, newest first."""
    items = _load()
    items.sort(key=lambda x: x.get('added_at') or 0.0, reverse=True)
    return items


def ids_set() -> Set[FavId]:
    out: Set[FavId] = set()
    for it in _load():
        k = _key(it)
        if k:
            out.add(k)
    return out


def is_favorite(media_type: str, tmdb_id: int) -> bool:
    return (media_type, int(tmdb_id)) in ids_set()


def add(entry: dict) -> bool:
    """Add (or update) a favorite. Returns True if it was newly added."""
    k = _key(entry)
    if not k:
        return False
    items = _load()
    for i, it in enumerate(items):
        if _key(it) == k:
            entry.setdefault('added_at', it.get('added_at') or time.time())
            items[i] = entry
            _save(items)
            return False
    entry.setdefault('added_at', time.time())
    items.append(entry)
    _save(items)
    return True


def remove(media_type: str, tmdb_id: int) -> bool:
    """Remove a favorite. Returns True if something was removed."""
    target = (media_type, int(tmdb_id))
    items = _load()
    new = [it for it in items if _key(it) != target]
    if len(new) == len(items):
        return False
    _save(new)
    return True


def toggle(entry: dict) -> bool:
    """Add if absent, remove if present. Returns the resulting state (True=is fav)."""
    k = _key(entry)
    if not k:
        return False
    if is_favorite(*k):
        remove(*k)
        return False
    add(entry)
    return True
