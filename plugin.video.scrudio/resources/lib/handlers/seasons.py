# -*- coding: utf-8 -*-
"""Seasons / Episodes handler.

Routes:
  ?action=seasons&tmdb_id=...                       → list seasons
  ?action=episodes&tmdb_id=...&season=N             → list episodes of a season
"""
from __future__ import annotations

import xbmcplugin

from .. import kodi, settings, tmdb


def list_seasons(handle: int, params: dict) -> None:
    if not settings.has_tmdb():
        kodi.notify_error(kodi.t(30902))
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    try:
        tmdb_id = int(params.get('tmdb_id') or 0)
    except (TypeError, ValueError):
        tmdb_id = 0
    if not tmdb_id:
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    show = tmdb.tv_details(tmdb_id) or {}
    seasons = show.get('seasons') or []
    show_title = show.get('name') or params.get('title') or ''
    imdb_id = (show.get('external_ids') or {}).get('imdb_id') or ''

    show_fanart = tmdb.backdrop_url(show)
    show_poster = tmdb.poster_url(show)

    items_added = 0
    for s in seasons:
        if not isinstance(s, dict):
            continue
        season_number = s.get('season_number')
        if season_number is None or season_number < 1:
            # Skip "Specials" (season 0) for cleanliness; user can still find via search.
            continue

        s_name = s.get('name') or f'Season {season_number}'
        ep_count = s.get('episode_count') or 0
        label = f'{s_name}  ·  {ep_count} ep' if ep_count else s_name

        season_poster = (f'https://image.tmdb.org/t/p/w342{s["poster_path"]}'
                         if s.get('poster_path') else show_poster)
        art = {}
        if season_poster:
            art['poster'] = season_poster
            art['thumb'] = season_poster
            art['icon'] = season_poster
        if show_fanart:
            art['fanart'] = show_fanart

        info = {
            'title':     s_name,
            'plot':      s.get('overview') or show.get('overview') or '',
            'mediatype': 'season',
            'season':    season_number,
            'premiered': s.get('air_date') or None,
        }

        target = kodi.build_url(
            action='episodes',
            tmdb_id=tmdb_id,
            season=season_number,
            title=show_title,
            imdb_id=imdb_id,
        )
        li = kodi.make_listitem(label, info=info, art=art)
        kodi.add_directory_item(handle, target, li, is_folder=True)
        items_added += 1

    if items_added == 0:
        kodi.notify(kodi.t(30128))
    kodi.end_directory(handle, content='seasons', succeeded=True)


def list_episodes(handle: int, params: dict) -> None:
    if not settings.has_tmdb():
        kodi.notify_error(kodi.t(30902))
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    try:
        tmdb_id = int(params.get('tmdb_id') or 0)
        season_number = int(params.get('season') or 0)
    except (TypeError, ValueError):
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return
    if not tmdb_id or season_number < 1:
        xbmcplugin.endOfDirectory(handle, succeeded=False)
        return

    show_title = params.get('title') or ''
    imdb_id = params.get('imdb_id') or ''
    if not imdb_id:
        # Resolve lazily — episodes need IMDB id to query Torrentio later
        ext = tmdb.tv_external_ids(tmdb_id) or {}
        imdb_id = ext.get('imdb_id') or ''

    season = tmdb.tv_season(tmdb_id, season_number) or {}
    episodes = season.get('episodes') or []
    season_fanart = ''  # Season payload doesn't carry show backdrop; use poster as thumb

    items_added = 0
    for ep in episodes:
        if not isinstance(ep, dict):
            continue
        ep_number = ep.get('episode_number')
        if ep_number is None:
            continue
        ep_name = ep.get('name') or f'Episode {ep_number}'
        label = f'{ep_number:02d}. {ep_name}'

        still = ep.get('still_path')
        thumb = f'https://image.tmdb.org/t/p/w780{still}' if still else ''
        art = {}
        if thumb:
            art['thumb'] = thumb
            art['icon'] = thumb
            art['fanart'] = thumb

        info = {
            'title':     ep_name,
            'plot':      ep.get('overview') or '',
            'mediatype': 'episode',
            'season':    season_number,
            'episode':   ep_number,
            'rating':    ep.get('vote_average'),
            'votes':     ep.get('vote_count'),
            'premiered': ep.get('air_date') or None,
            'duration':  (ep.get('runtime') or 0) * 60 or None,
        }

        target = kodi.build_url(
            action='sources',
            media_type='series',
            tmdb_id=tmdb_id,
            imdb_id=imdb_id,
            season=season_number,
            episode=ep_number,
            title=show_title,
            ep_title=ep_name,
        )
        li = kodi.make_listitem(label, info=info, art=art)
        kodi.add_directory_item(handle, target, li, is_folder=True)
        items_added += 1

    if items_added == 0:
        kodi.notify(kodi.t(30129))
    kodi.end_directory(handle, content='episodes', succeeded=True)
