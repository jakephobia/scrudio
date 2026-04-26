# -*- coding: utf-8 -*-
"""Search handler — TMDB multi-search with on-disk history.

Routes:
  ?action=search                       → keyboard prompt, then list results
  ?action=search_run&q=...&page=1      → list results for query
"""
from __future__ import annotations

import json
import os
from typing import List

import xbmcplugin

from .. import favorites, kodi, settings, tmdb, views

HISTORY_FILE = os.path.join(kodi.PROFILE_PATH, 'search_history.json')
HISTORY_MAX = 20


# ── Persistent history ───────────────────────────────────────────────────────
def _load_history() -> List[str]:
    try:
        if not os.path.exists(HISTORY_FILE):
            return []
        with open(HISTORY_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
        if isinstance(data, list):
            return [str(x) for x in data][:HISTORY_MAX]
    except (OSError, ValueError):
        pass
    return []


def _save_history(items: List[str]) -> None:
    try:
        os.makedirs(kodi.PROFILE_PATH, exist_ok=True)
        with open(HISTORY_FILE, 'w', encoding='utf-8') as f:
            json.dump(items[:HISTORY_MAX], f, ensure_ascii=False)
    except OSError as e:
        kodi.log_warning(f'Failed to save search history: {e}')


def _push_history(query: str) -> None:
    q = (query or '').strip()
    if not q:
        return
    hist = _load_history()
    # Move/insert to top, dedup case-insensitive
    hist = [h for h in hist if h.lower() != q.lower()]
    hist.insert(0, q)
    _save_history(hist)


# ── Handlers ─────────────────────────────────────────────────────────────────
def prompt(handle: int, params: dict) -> None:
    """Show keyboard, then dispatch into search_run."""
    if not settings.has_tmdb():
        kodi.notify_error(kodi.t(30902))
        settings.open_addon_settings()
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    # If a history entry was clicked, params carries q already → run directly.
    q = params.get('q')
    if not q:
        q = kodi.keyboard(kodi.t(30110))
        if not q:
            xbmcplugin.endOfDirectory(handle, succeeded=False)
            return

    _push_history(q)
    run(handle, {'q': q, 'page': '1'})


def run(handle: int, params: dict) -> None:
    """List results for a given query."""
    if not settings.has_tmdb():
        kodi.notify_error(kodi.t(30902))
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    q = (params.get('q') or '').strip()
    if not q:
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    try:
        page = max(1, int(params.get('page') or 1))
    except (TypeError, ValueError):
        page = 1

    data = tmdb.search_multi(q, page=page) or {}
    results = data.get('results') or []
    total_pages = int(data.get('total_pages') or 1)

    fav_set = favorites.ids_set()
    items_added = 0
    for item in results:
        if not isinstance(item, dict):
            continue
        media_type = tmdb.detect_media_type(item)
        result = views.browse_listitem(item, media_type, fav_set=fav_set)
        if not result:
            continue
        target, li = result
        kodi.add_directory_item(handle, target, li, is_folder=True)
        items_added += 1

    if page < total_pages and items_added > 0:
        next_url = kodi.build_url(action='search_run', q=q, page=page + 1)
        next_li = kodi.make_listitem(f'> Page {page + 1} / {total_pages}')
        kodi.add_directory_item(handle, next_url, next_li, is_folder=True)

    if items_added == 0:
        kodi.notify(f'No results for "{q}"')

    kodi.end_directory(handle, content='videos', succeeded=True)
