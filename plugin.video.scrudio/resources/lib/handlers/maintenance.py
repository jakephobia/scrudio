# -*- coding: utf-8 -*-
"""Maintenance actions invoked from Settings buttons.

Routes:
  ?action=cache_clear   → wipe the on-disk TMDB cache
"""
from __future__ import annotations

import xbmcplugin

from .. import cache, kodi


def cache_clear(handle: int, params: dict) -> None:
    n = cache.clear()
    kodi.notify(kodi.t(30411).format(n))
    if handle >= 0:
        # When invoked from a Settings RunPlugin, handle is -1 — guard before use.
        xbmcplugin.endOfDirectory(handle, succeeded=True)
