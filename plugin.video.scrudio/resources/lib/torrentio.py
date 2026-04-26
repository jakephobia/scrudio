# -*- coding: utf-8 -*-
"""Torrentio client (Real-Debrid only).

Endpoint pattern (with RD enabled):
    https://torrentio.strem.fun/realdebrid=<KEY>/stream/<type>/<id>.json

  - type: 'movie' or 'series'
  - id:   imdbId           (movie)
          imdbId:S:E       (series episode)

Streams returned by Torrentio when RD is configured carry a `url` field:
that URL is a Torrentio endpoint that 302-redirects to the actual Real-Debrid
CDN download. Kodi follows redirects natively, so we just hand the URL to the
player.
"""
from __future__ import annotations

import re
from typing import List, Optional

from . import http, settings

BASE = 'https://torrentio.strem.fun'

_RE_SEEDS = re.compile(r'👤\s*(\d+)')
_RE_SIZE = re.compile(r'💾\s*([\d.,]+\s*[GMKT]?B)', re.IGNORECASE)
_RE_PROVIDER = re.compile(r'⚙️\s*([\w.\-]+)')

# Quality ranking for stable sorting
_QUALITY_RANK = {'4K': 4, '1080p': 3, '720p': 2, '480p': 1, 'HD': 0}


# ── Pure parsers (testable without Kodi) ─────────────────────────────────────
def parse_quality(name: str) -> str:
    u = (name or '').upper()
    if '2160' in u or '4K' in u:    return '4K'
    if '1080' in u:                  return '1080p'
    if '720' in u:                   return '720p'
    if '480' in u:                   return '480p'
    return 'HD'


def parse_seeds(title: str) -> int:
    m = _RE_SEEDS.search(title or '')
    return int(m.group(1)) if m else 0


def parse_size(title: str) -> str:
    m = _RE_SIZE.search(title or '')
    return m.group(1).strip() if m else ''


def parse_provider(name: str) -> str:
    m = _RE_PROVIDER.search(name or '')
    if m:
        return m.group(1)
    lines = (name or '').split('\n')
    return lines[1].strip() if len(lines) > 1 else 'Unknown'


def parse_codec(name: str) -> str:
    u = (name or '').upper()
    if 'HEVC' in u or 'H265' in u or 'X265' in u: return 'HEVC'
    if 'H264' in u or 'X264' in u:                 return 'H.264'
    if 'AV1' in u:                                 return 'AV1'
    return ''


def first_line(s: str) -> str:
    return (s or '').split('\n', 1)[0].strip()


# ── API call ─────────────────────────────────────────────────────────────────
def get_streams(imdb_id: str,
                media_type: str,
                season: int = 0,
                episode: int = 0) -> List[dict]:
    """media_type: 'movie' | 'series'.  Returns sources sorted by quality+seeds."""
    if not imdb_id:
        return []
    if not settings.has_rd():
        return []

    rd_key = settings.rd_key()
    config = f'realdebrid={rd_key}/'

    if media_type == 'movie':
        sid = imdb_id
    else:
        if not season or not episode:
            return []
        sid = f'{imdb_id}:{int(season)}:{int(episode)}'

    url = f'{BASE}/{config}stream/{media_type}/{sid}.json'
    data = http.get_json(url) or {}
    raw = data.get('streams') or []

    allowed = settings.quality_filter()
    drop_no_seeds = settings.hide_no_seeds()

    out = []
    for s in raw:
        if not isinstance(s, dict):
            continue
        stream_url = s.get('url')
        if not stream_url:
            # No RD-resolvable URL → we can't play it without a torrent client.
            continue

        name = s.get('name') or ''
        title = s.get('title') or ''
        quality = parse_quality(name + ' ' + title)
        if quality not in allowed and quality != 'HD':
            continue

        seeds = parse_seeds(title)
        if drop_no_seeds and seeds == 0:
            # When RD has the file, seeds is sometimes 0; trust the URL anyway
            # only when RD has it cached (Torrentio prefixes the name with "RD+").
            if 'RD+' not in name:
                continue

        out.append({
            'release':  first_line(title),
            'quality':  quality,
            'seeds':    seeds,
            'size':     parse_size(title),
            'provider': parse_provider(name),
            'codec':    parse_codec(name),
            'url':      stream_url,
            'info_hash': s.get('infoHash'),
            'rd_cached': 'RD+' in name,
        })

    # Sort: quality desc, then seeds desc, RD cached items first within tier
    out.sort(key=lambda x: (
        _QUALITY_RANK.get(x['quality'], -1),
        1 if x['rd_cached'] else 0,
        x['seeds'],
    ), reverse=True)
    return out


def format_label(src: dict) -> str:
    """Pretty single-line label for a source row."""
    parts = []
    parts.append(f'[{src["quality"]}]')
    if src.get('codec'):
        parts.append(src['codec'])
    if src.get('size'):
        parts.append(src['size'])
    if src.get('seeds'):
        parts.append(f'👤{src["seeds"]}')
    if src.get('rd_cached'):
        parts.insert(0, 'RD+')
    head = ' · '.join(parts)
    tail = f'{src.get("provider", "?")} — {src.get("release", "")}'
    return f'{head}  {tail}'.strip()
