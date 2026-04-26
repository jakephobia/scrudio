# -*- coding: utf-8 -*-
"""Sources handler — list Torrentio streams for a given title/episode.

Routes:
  ?action=sources&media_type=movie&tmdb_id=...&imdb_id=...&title=...&year=...
  ?action=sources&media_type=series&tmdb_id=...&imdb_id=...&season=N&episode=M&title=...
"""
from __future__ import annotations

import xbmcgui
import xbmcplugin

from .. import kodi, settings, tmdb, torrentio


def _ensure_imdb_id(media_type: str, params: dict) -> str:
    imdb_id = (params.get('imdb_id') or '').strip()
    if imdb_id:
        return imdb_id

    try:
        tmdb_id = int(params.get('tmdb_id') or 0)
    except (TypeError, ValueError):
        tmdb_id = 0
    if not tmdb_id:
        return ''

    if media_type == 'movie':
        details = tmdb.movie_details(tmdb_id) or {}
    else:
        details = tmdb.tv_details(tmdb_id) or {}
    ext = details.get('external_ids') or {}
    return ext.get('imdb_id') or ''


def list_(handle: int, params: dict) -> None:
    if not settings.has_rd():
        kodi.notify_error(kodi.t(30903))
        settings.open_addon_settings()
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    media_type = params.get('media_type') or 'movie'
    if media_type not in ('movie', 'series'):
        media_type = 'movie'

    title = params.get('title') or ''
    ep_title = params.get('ep_title') or ''
    try:
        season = int(params.get('season') or 0)
        episode = int(params.get('episode') or 0)
    except (TypeError, ValueError):
        season = episode = 0

    imdb_id = _ensure_imdb_id(media_type, params)
    if not imdb_id:
        kodi.notify_error('IMDB id not available for this title')
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    # ── Fetch ────────────────────────────────────────────────────────────────
    progress = xbmcgui.DialogProgressBG()
    try:
        progress.create(kodi.ADDON_NAME, 'Searching sources…')
        sources = torrentio.get_streams(imdb_id, media_type,
                                        season=season, episode=episode)
    finally:
        try:
            progress.close()
        except Exception:
            pass

    if not sources:
        kodi.notify('No playable sources found')
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    # ── Render ───────────────────────────────────────────────────────────────
    play_title = title
    if media_type == 'series' and season and episode:
        play_title = f'{title} S{season:02d}E{episode:02d}'
        if ep_title:
            play_title = f'{play_title} — {ep_title}'

    fanart = kodi.MEDIA_PATH + 'fanart.jpg'
    icon = kodi.MEDIA_PATH + 'icon.png'

    for src in sources:
        label = torrentio.format_label(src)
        info = {
            'title':     play_title,
            'plot':      src.get('release') or '',
            'mediatype': 'movie' if media_type == 'movie' else 'episode',
        }
        if media_type == 'series' and season and episode:
            info['season'] = season
            info['episode'] = episode

        li = kodi.make_listitem(label, info=info,
                                art={'icon': icon, 'thumb': icon, 'fanart': fanart},
                                is_playable=True)
        play_url = kodi.build_url(action='play',
                                  url=src['url'],
                                  title=play_title)
        kodi.add_directory_item(handle, play_url, li, is_folder=False)

    kodi.end_directory(handle, content='videos', succeeded=True)
