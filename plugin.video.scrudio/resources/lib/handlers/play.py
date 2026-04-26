# -*- coding: utf-8 -*-
"""Playback resolver — hands the final URL to the Kodi player.

Route:
  ?action=play&url=<encoded>&title=<encoded>
"""
from __future__ import annotations

import xbmcplugin

from .. import kodi


def resolve(handle: int, params: dict) -> None:
    url = params.get('url')
    if not url:
        kodi.notify_error(kodi.t(30901))
        xbmcplugin.setResolvedUrl(handle, False, kodi.make_listitem('Error'))
        return

    title = params.get('title') or kodi.ADDON_NAME
    li = kodi.make_listitem(title, is_playable=True)
    li.setPath(url)
    # Hint Kodi about the content type to keep it happy with HTTP redirects to RD CDN
    li.setMimeType('video/mp4')
    li.setContentLookup(False)
    xbmcplugin.setResolvedUrl(handle, True, li)
