# -*- coding: utf-8 -*-
"""Shared list-item builders used by catalog, search, and favorites handlers.

Centralising this logic keeps URL construction and context-menu wiring in
one place so the handlers stay thin.
"""
from __future__ import annotations

from typing import Iterable, Optional, Set, Tuple

from . import favorites as fav_module
from . import kodi, tmdb


def _fallback_art() -> dict:
    return {
        'icon':   kodi.MEDIA_PATH + 'icon.png',
        'thumb':  kodi.MEDIA_PATH + 'icon.png',
        'fanart': kodi.MEDIA_PATH + 'fanart.jpg',
    }


def browse_listitem(item: dict,
                    media_type: str,
                    fav_set: Optional[Set[Tuple[str, int]]] = None
                    ) -> Optional[Tuple[str, object]]:
    """Build (target_url, ListItem) for a TMDB browse entry.

    `media_type` is 'movie' or 'tv'.
    `fav_set` may be None; if provided it's used to swap the "Add"/"Remove"
    context menu entry without re-reading the file per item.
    """
    if media_type not in ('movie', 'tv'):
        return None
    tmdb_id = item.get('id')
    if not tmdb_id:
        return None
    title = tmdb.title_of(item, media_type)
    if not title:
        return None

    year = tmdb.year_of(item, media_type)
    label = f'{title} ({year})' if year else title
    info = tmdb.to_listitem_info(item, media_type)
    art = tmdb.to_listitem_art(item) or _fallback_art()

    if media_type == 'movie':
        target = kodi.build_url(action='sources', media_type='movie',
                                tmdb_id=tmdb_id, title=title, year=year)
    else:
        target = kodi.build_url(action='seasons',
                                tmdb_id=tmdb_id, title=title, year=year)

    li = kodi.make_listitem(label, info=info, art=art)

    # Context menu — favourite toggle
    if fav_set is None:
        is_fav = fav_module.is_favorite(media_type, int(tmdb_id))
    else:
        is_fav = (media_type, int(tmdb_id)) in fav_set

    # Trim plot aggressively — long URLs (encoded ~3x) can break Kodi's URL handling.
    fav_url = kodi.build_url(
        action='fav_toggle',
        media_type=media_type,
        tmdb_id=tmdb_id,
        title=title,
        year=year,
        plot=(item.get('overview') or '')[:200],
        poster=art.get('poster', ''),
        fanart=art.get('fanart', ''),
    )
    label_id = 30122 if is_fav else 30121
    li.addContextMenuItems([(kodi.t(label_id), f'RunPlugin({fav_url})')])

    return target, li


def listitem_from_favorite(entry: dict) -> Optional[Tuple[str, object]]:
    """Build (target_url, ListItem) for a stored favorite entry."""
    media_type = entry.get('media_type')
    if media_type not in ('movie', 'tv'):
        return None
    try:
        tmdb_id = int(entry.get('tmdb_id') or 0)
    except (TypeError, ValueError):
        return None
    if not tmdb_id:
        return None

    title = entry.get('title') or '?'
    year = entry.get('year') or ''
    label = f'{title} ({year})' if year else title

    info = {
        'title':     title,
        'plot':      entry.get('plot') or '',
        'year':      year or None,
        'mediatype': 'movie' if media_type == 'movie' else 'tvshow',
    }
    art = {}
    if entry.get('poster'):
        art['poster'] = entry['poster']
        art['thumb'] = entry['poster']
        art['icon'] = entry['poster']
    if entry.get('fanart'):
        art['fanart'] = entry['fanart']
    if not art:
        art = _fallback_art()

    if media_type == 'movie':
        target = kodi.build_url(action='sources', media_type='movie',
                                tmdb_id=tmdb_id, title=title, year=year)
    else:
        target = kodi.build_url(action='seasons',
                                tmdb_id=tmdb_id, title=title, year=year)

    li = kodi.make_listitem(label, info=info, art=art)
    fav_url = kodi.build_url(
        action='fav_toggle',
        media_type=media_type,
        tmdb_id=tmdb_id,
        title=title,
        year=year,
    )
    li.addContextMenuItems([(kodi.t(30122), f'RunPlugin({fav_url})')])
    return target, li
