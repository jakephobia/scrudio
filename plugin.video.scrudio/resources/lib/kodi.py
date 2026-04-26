# -*- coding: utf-8 -*-
"""Kodi runtime helpers.

Thin wrappers over xbmc/xbmcgui/xbmcplugin/xbmcvfs to keep the rest of the
codebase free of boilerplate.
"""
import sys
from typing import Optional
from urllib.parse import urlencode

import xbmc
import xbmcaddon
import xbmcgui
import xbmcplugin
import xbmcvfs

ADDON = xbmcaddon.Addon()
ADDON_ID = ADDON.getAddonInfo('id')
ADDON_NAME = ADDON.getAddonInfo('name')
ADDON_VERSION = ADDON.getAddonInfo('version')
ADDON_PATH = xbmcvfs.translatePath(ADDON.getAddonInfo('path'))
PROFILE_PATH = xbmcvfs.translatePath(ADDON.getAddonInfo('profile'))
RESOURCES_PATH = ADDON_PATH + 'resources/'
MEDIA_PATH = RESOURCES_PATH + 'media/'

BASE_URL = sys.argv[0] if len(sys.argv) > 0 else f'plugin://{ADDON_ID}/'


# ── Logging ──────────────────────────────────────────────────────────────────
def log(msg: str, level: int = xbmc.LOGINFO) -> None:
    xbmc.log(f'[{ADDON_ID}] {msg}', level)


def log_warning(msg: str) -> None:
    log(msg, xbmc.LOGWARNING)


def log_error(msg: str) -> None:
    log(msg, xbmc.LOGERROR)


# ── Localization ─────────────────────────────────────────────────────────────
def t(string_id: int) -> str:
    return ADDON.getLocalizedString(string_id)


# ── URL building ─────────────────────────────────────────────────────────────
def build_url(**params) -> str:
    if not params:
        return BASE_URL
    # Filter None values to keep URLs clean
    clean = {k: v for k, v in params.items() if v is not None}
    return f'{BASE_URL}?{urlencode(clean)}'


# ── Dialogs ──────────────────────────────────────────────────────────────────
def notify(msg: str, heading: Optional[str] = None,
           icon: str = xbmcgui.NOTIFICATION_INFO, millis: int = 3500) -> None:
    xbmcgui.Dialog().notification(heading or ADDON_NAME, msg, icon, millis)


def notify_error(msg: str, heading: Optional[str] = None) -> None:
    notify(msg, heading, icon=xbmcgui.NOTIFICATION_ERROR, millis=5000)


def confirm(heading: str, message: str) -> bool:
    return bool(xbmcgui.Dialog().yesno(heading, message))


def keyboard(heading: str, default: str = '') -> str:
    return xbmcgui.Dialog().input(heading, default, type=xbmcgui.INPUT_ALPHANUM) or ''


# ── ListItem builder ─────────────────────────────────────────────────────────
def make_listitem(label: str,
                  info: Optional[dict] = None,
                  art: Optional[dict] = None,
                  is_playable: bool = False) -> xbmcgui.ListItem:
    """Build a Kodi 21-friendly ListItem.

    `info` keys (all optional): title, plot, year, rating, votes, duration,
    genre (list[str]), mediatype, imdbnumber, season, episode, premiered.
    `art` keys: poster, fanart, thumb, banner, clearlogo, ...
    """
    li = xbmcgui.ListItem(label=label)
    if art:
        li.setArt(art)
    if info:
        vi = li.getVideoInfoTag()
        if (v := info.get('title')) is not None:
            vi.setTitle(str(v))
        if (v := info.get('plot')) is not None:
            vi.setPlot(str(v))
        if (v := info.get('year')) is not None:
            try:
                vi.setYear(int(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('rating')) is not None:
            try:
                vi.setRating(float(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('votes')) is not None:
            try:
                vi.setVotes(int(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('duration')) is not None:
            try:
                vi.setDuration(int(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('genre')) is not None:
            vi.setGenres(list(v) if isinstance(v, (list, tuple)) else [str(v)])
        if (v := info.get('mediatype')) is not None:
            vi.setMediaType(str(v))
        if (v := info.get('imdbnumber')) is not None:
            vi.setIMDBNumber(str(v))
        if (v := info.get('season')) is not None:
            try:
                vi.setSeason(int(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('episode')) is not None:
            try:
                vi.setEpisode(int(v))
            except (TypeError, ValueError):
                pass
        if (v := info.get('premiered')) is not None:
            vi.setPremiered(str(v))
    if is_playable:
        li.setProperty('IsPlayable', 'true')
    return li


def add_directory_item(handle: int, url: str, list_item: xbmcgui.ListItem,
                       is_folder: bool = True) -> None:
    xbmcplugin.addDirectoryItem(handle, url, list_item, isFolder=is_folder)


def end_directory(handle: int, content: str = 'files',
                  succeeded: bool = True, update: bool = False) -> None:
    if succeeded:
        xbmcplugin.setContent(handle, content)
    xbmcplugin.endOfDirectory(handle, succeeded=succeeded, updateListing=update,
                              cacheToDisc=True)
