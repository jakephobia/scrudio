# -*- coding: utf-8 -*-
"""Home menu — the root of the add-on UI.

Phase 1: shows the static menu. Items pointing to unimplemented features
(catalog, search, favorites) currently dispatch to `not_implemented` which
just shows a notification.
"""
import xbmcplugin

from .. import kodi


# (label_id, params, is_folder)
_HOME_ITEMS = [
    (30100, {'action': 'catalog', 'kind': 'trending'},          True),
    (30101, {'action': 'catalog', 'kind': 'popular_movies'},    True),
    (30102, {'action': 'catalog', 'kind': 'popular_tv'},        True),
    (30103, {'action': 'catalog', 'kind': 'top_rated_movies'},  True),
    (30104, {'action': 'catalog', 'kind': 'top_rated_tv'},      True),
    (30110, {'action': 'search'},                               True),
    (30120, {'action': 'fav_list'},                             True),
    (30130, {'action': 'settings'},                             False),
]


def show(handle: int, params: dict) -> None:
    icon = kodi.MEDIA_PATH + 'icon.png'
    fanart = kodi.MEDIA_PATH + 'fanart.jpg'
    art = {'icon': icon, 'thumb': icon, 'fanart': fanart}

    for label_id, p, is_folder in _HOME_ITEMS:
        url = kodi.build_url(**p)
        li = kodi.make_listitem(kodi.t(label_id), art=art)
        kodi.add_directory_item(handle, url, li, is_folder=is_folder)

    kodi.end_directory(handle, content='files')


def open_settings(handle: int, params: dict) -> None:
    from .. import settings
    settings.open_addon_settings()
    # Settings dialog is modal; nothing to render in the directory.
    xbmcplugin.endOfDirectory(handle, succeeded=False)


def not_implemented(handle: int, params: dict) -> None:
    kodi.notify(kodi.t(30950))
    xbmcplugin.endOfDirectory(handle, succeeded=False)
