# -*- coding: utf-8 -*-
"""Typed wrapper over xbmcaddon settings.

All access to user-configurable values goes through this module so the rest of
the codebase never touches xbmcaddon directly.
"""
from .kodi import ADDON


# ── API keys ─────────────────────────────────────────────────────────────────
def tmdb_key() -> str:
    return (ADDON.getSettingString('tmdb_api_key') or '').strip()


def has_tmdb() -> bool:
    return bool(tmdb_key())


def rd_key() -> str:
    return (ADDON.getSettingString('rd_api_key') or '').strip()


def has_rd() -> bool:
    return bool(rd_key())


# ── Catalog ──────────────────────────────────────────────────────────────────
def language() -> str:
    """TMDB language code, e.g. 'it-IT', 'en-US'."""
    return ADDON.getSettingString('tmdb_language') or 'it-IT'


def quality_filter() -> set:
    """Set of allowed quality labels. Empty filter = allow all."""
    out = set()
    mapping = {
        'quality_4k':    '4K',
        'quality_1080p': '1080p',
        'quality_720p':  '720p',
        'quality_480p':  '480p',
    }
    for key, label in mapping.items():
        try:
            if ADDON.getSettingBool(key):
                out.add(label)
        except Exception:
            # Setting not yet defined -> default to enabled
            out.add(label)
    return out or {'4K', '1080p', '720p', '480p'}


def hide_no_seeds() -> bool:
    try:
        return ADDON.getSettingBool('hide_no_seeds')
    except Exception:
        return True


# ── Cache ────────────────────────────────────────────────────────────────────
def cache_ttl_hours() -> int:
    try:
        v = ADDON.getSettingInt('cache_ttl_hours')
    except Exception:
        v = 0
    return v if v > 0 else 6


# ── Actions ──────────────────────────────────────────────────────────────────
def open_addon_settings() -> None:
    ADDON.openSettings()
