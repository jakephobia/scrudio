# -*- coding: utf-8 -*-
"""Favorites handlers.

Routes:
  ?action=fav_list                                 → list saved favorites
  ?action=fav_toggle&media_type=...&tmdb_id=...&title=...&...
                                                    → toggle membership and refresh
"""
from __future__ import annotations

import xbmc
import xbmcplugin

from .. import favorites, kodi, views


def list_(handle: int, params: dict) -> None:
    items = favorites.list_all()
    if not items:
        kodi.notify(kodi.t(30125))
        xbmcplugin.endOfDirectory(handle, succeeded=True)
        return

    for entry in items:
        result = views.listitem_from_favorite(entry)
        if not result:
            continue
        target, li = result
        kodi.add_directory_item(handle, target, li, is_folder=True)

    kodi.end_directory(handle, content='videos', succeeded=True)


def toggle(handle: int, params: dict) -> None:
    media_type = params.get('media_type')
    try:
        tmdb_id = int(params.get('tmdb_id') or 0)
    except (TypeError, ValueError):
        tmdb_id = 0
    if media_type not in ('movie', 'tv') or not tmdb_id:
        kodi.notify_error(kodi.t(30951))
        return

    entry = {
        'media_type': media_type,
        'tmdb_id':    tmdb_id,
        'title':      params.get('title') or '',
        'year':       params.get('year') or '',
        'plot':       params.get('plot') or '',
        'poster':     params.get('poster') or '',
        'fanart':     params.get('fanart') or '',
    }

    is_now_fav = favorites.toggle(entry)
    msg = kodi.t(30123) if is_now_fav else kodi.t(30124)
    kodi.notify(msg)
    # Refresh container so context menu label flips
    xbmc.executebuiltin('Container.Refresh')
