# -*- coding: utf-8 -*-
"""TMDB API v3 client with on-disk cache.

Image URLs are pre-built using w342 for posters and w780 for backdrops to
keep bandwidth and Kodi's texture cache happy.
"""
from __future__ import annotations

from typing import Optional

from . import cache, http, settings

BASE = 'https://api.themoviedb.org/3'
IMG_POSTER = 'https://image.tmdb.org/t/p/w342'
IMG_BACKDROP = 'https://image.tmdb.org/t/p/w780'

# Cache TTLs by category (caller may override)
TTL_LIST_HOURS = None      # uses settings.cache_ttl_hours()
TTL_DETAILS_HOURS = 24
TTL_SEARCH_HOURS = 1


# ── Internals ────────────────────────────────────────────────────────────────
def _params(extra: Optional[dict] = None) -> dict:
    p = {
        'api_key': settings.tmdb_key(),
        'language': settings.language(),
    }
    if extra:
        p.update({k: v for k, v in extra.items() if v is not None})
    return p


def _cache_key(path: str, params: dict) -> str:
    # Exclude api_key from the cache key (it's an auth secret, not part of the resource id)
    relevant = {k: v for k, v in params.items() if k != 'api_key'}
    items = sorted(relevant.items())
    return f'tmdb:{path}:{items}'


def _get(path: str, params: dict, ttl_hours: Optional[int] = None):
    ttl_h = ttl_hours if ttl_hours is not None else settings.cache_ttl_hours()
    ttl = ttl_h * 3600
    key = _cache_key(path, params)

    hit = cache.get(key, ttl)
    if hit is not None:
        return hit

    data = http.get_json(f'{BASE}{path}', params=params)
    if data is not None:
        cache.set(key, data)
    return data


# ── Catalogue endpoints ──────────────────────────────────────────────────────
def trending(media_type: str = 'all', window: str = 'week', page: int = 1):
    """media_type: 'all' | 'movie' | 'tv'  ·  window: 'day' | 'week'."""
    return _get(f'/trending/{media_type}/{window}', _params({'page': page}))


def popular_movies(page: int = 1):
    return _get('/movie/popular', _params({'page': page}))


def popular_tv(page: int = 1):
    return _get('/tv/popular', _params({'page': page}))


def top_rated_movies(page: int = 1):
    return _get('/movie/top_rated', _params({'page': page}))


def top_rated_tv(page: int = 1):
    return _get('/tv/top_rated', _params({'page': page}))


def search_multi(query: str, page: int = 1):
    if not query:
        return None
    return _get('/search/multi',
                _params({'query': query, 'page': page, 'include_adult': 'false'}),
                ttl_hours=TTL_SEARCH_HOURS)


# ── Detail endpoints ─────────────────────────────────────────────────────────
def movie_details(tmdb_id: int):
    return _get(f'/movie/{tmdb_id}',
                _params({'append_to_response': 'external_ids,credits,videos'}),
                ttl_hours=TTL_DETAILS_HOURS)


def tv_details(tmdb_id: int):
    return _get(f'/tv/{tmdb_id}',
                _params({'append_to_response': 'external_ids,credits,videos'}),
                ttl_hours=TTL_DETAILS_HOURS)


def tv_season(tmdb_id: int, season_number: int):
    return _get(f'/tv/{tmdb_id}/season/{season_number}',
                _params(), ttl_hours=TTL_DETAILS_HOURS)


def tv_external_ids(tmdb_id: int):
    """Lightweight call to fetch IMDB id for a TV show (cached 24h)."""
    return _get(f'/tv/{tmdb_id}/external_ids', _params(), ttl_hours=TTL_DETAILS_HOURS)


def movie_external_ids(tmdb_id: int):
    """Lightweight call to fetch IMDB id for a movie (~500 B vs ~5 KB for full details)."""
    return _get(f'/movie/{tmdb_id}/external_ids', _params(), ttl_hours=TTL_DETAILS_HOURS)


# ── Helpers ──────────────────────────────────────────────────────────────────
def kind_to_caller(kind: str):
    """Map a 'kind' string (used in routing) to the TMDB function."""
    mapping = {
        'trending':           lambda page: trending('all', 'week', page),
        'popular_movies':     popular_movies,
        'popular_tv':         popular_tv,
        'top_rated_movies':   top_rated_movies,
        'top_rated_tv':       top_rated_tv,
    }
    return mapping.get(kind)


def detect_media_type(item: dict) -> str:
    """Pick a media type from a /trending or /search/multi entry.

    Returns 'movie', 'tv', or '' (the latter for person results that callers
    must skip). For endpoints that don't carry media_type (popular_movies,
    popular_tv, …) the heuristic prefers TV when an air-date is present.
    """
    mt = item.get('media_type')
    if mt in ('movie', 'tv'):
        return mt
    if mt == 'person':
        return ''
    # No explicit media_type → list endpoints. Use TV-specific keys to detect.
    if 'first_air_date' in item or 'episode_run_time' in item:
        return 'tv'
    return 'movie'


def poster_url(item: dict) -> str:
    p = item.get('poster_path')
    return f'{IMG_POSTER}{p}' if p else ''


def backdrop_url(item: dict) -> str:
    b = item.get('backdrop_path')
    return f'{IMG_BACKDROP}{b}' if b else ''


def title_of(item: dict, media_type: str) -> str:
    if media_type == 'tv':
        return item.get('name') or item.get('title') or ''
    return item.get('title') or item.get('name') or ''


def year_of(item: dict, media_type: str) -> str:
    date = item.get('first_air_date' if media_type == 'tv' else 'release_date') or ''
    return date.split('-', 1)[0] if date else ''


def to_listitem_info(item: dict, media_type: str) -> dict:
    """Build the `info` dict consumed by kodi.make_listitem."""
    genres = []
    g = item.get('genres')
    if isinstance(g, list):
        genres = [x.get('name', '') for x in g if isinstance(x, dict) and x.get('name')]
    elif isinstance(item.get('genre_ids'), list):
        # /trending and /search/multi return numeric ids only — leave empty.
        genres = []

    info = {
        'title':     title_of(item, media_type),
        'plot':      item.get('overview') or '',
        'year':      year_of(item, media_type) or None,
        'rating':    item.get('vote_average'),
        'votes':     item.get('vote_count'),
        'genre':     genres,
        'mediatype': 'movie' if media_type == 'movie' else 'tvshow',
        'premiered': item.get('first_air_date' if media_type == 'tv' else 'release_date') or None,
    }

    runtime = item.get('runtime')
    if isinstance(runtime, int) and runtime > 0:
        info['duration'] = runtime * 60

    ext = item.get('external_ids') or {}
    if ext.get('imdb_id'):
        info['imdbnumber'] = ext['imdb_id']

    return info


def to_listitem_art(item: dict) -> dict:
    poster = poster_url(item)
    fanart = backdrop_url(item)
    art = {}
    if poster:
        art['poster'] = poster
        art['thumb'] = poster
        art['icon'] = poster
    if fanart:
        art['fanart'] = fanart
    return art
