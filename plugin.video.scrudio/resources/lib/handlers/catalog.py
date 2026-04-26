# -*- coding: utf-8 -*-
"""Catalogue listing handler.

Routes:
  ?action=catalog&kind=trending|popular_movies|popular_tv|top_rated_movies|top_rated_tv&page=N

For each TMDB result we emit a folder ListItem that points to either:
  - sources (movies)         → ?action=sources&media_type=movie&tmdb_id=...
  - seasons (tv)             → ?action=seasons&tmdb_id=...
"""
from __future__ import annotations

import xbmcplugin

from .. import favorites, kodi, settings, tmdb, views


def list_(handle: int, params: dict) -> None:
    if not settings.has_tmdb():
        kodi.notify_error(kodi.t(30902))
        settings.open_addon_settings()
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    kind = params.get('kind') or 'trending'
    try:
        page = max(1, int(params.get('page') or 1))
    except (TypeError, ValueError):
        page = 1

    fetcher = tmdb.kind_to_caller(kind)
    if fetcher is None:
        kodi.log_warning(f'Unknown catalog kind: {kind!r}')
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    data = fetcher(page) or {}
    results = data.get('results') or []
    total_pages = int(data.get('total_pages') or 1)

    # Movie / TV bias for endpoints whose items don't carry media_type
    forced_media = None
    if kind in ('popular_movies', 'top_rated_movies'):
        forced_media = 'movie'
    elif kind in ('popular_tv', 'top_rated_tv'):
        forced_media = 'tv'

    fav_set = favorites.ids_set()
    items_added = 0
    for item in results:
        if not isinstance(item, dict):
            continue
        media_type = forced_media or tmdb.detect_media_type(item)
        result = views.browse_listitem(item, media_type, fav_set=fav_set)
        if not result:
            continue
        target, li = result
        kodi.add_directory_item(handle, target, li, is_folder=True)
        items_added += 1

    # ── Pagination ───────────────────────────────────────────────────────────
    if page < total_pages and items_added > 0:
        next_url = kodi.build_url(action='catalog', kind=kind, page=page + 1)
        next_li = kodi.make_listitem(f'> Page {page + 1} / {total_pages}')
        kodi.add_directory_item(handle, next_url, next_li, is_folder=True)

    if items_added == 0:
        kodi.notify(kodi.t(30951))

    content_type = 'movies' if forced_media == 'movie' else (
        'tvshows' if forced_media == 'tv' else 'videos')
    kodi.end_directory(handle, content=content_type, succeeded=True)
