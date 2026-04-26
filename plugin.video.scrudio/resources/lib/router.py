# -*- coding: utf-8 -*-
"""Action dispatcher.

Maps the `action` query parameter to a handler function. Handlers all share
the signature `(handle: int, params: dict) -> None`.

A blanket try/except wraps every dispatch so a single bad request can never
leave Kodi spinning on a half-built directory.
"""
from .handlers import home, catalog, search, seasons, sources, play, maintenance
from .handlers import favorites as favorites_handler
from . import kodi


_ROUTES = {
    None:           home.show,
    '':             home.show,
    'home':         home.show,
    'settings':     home.open_settings,

    # Browsing
    'catalog':      catalog.list_,
    'search':       search.prompt,
    'search_run':   search.run,
    'seasons':      seasons.list_seasons,
    'episodes':     seasons.list_episodes,

    # Sources + playback
    'sources':      sources.list_,
    'play':         play.resolve,

    # Favorites
    'fav_list':     favorites_handler.list_,
    'fav_toggle':   favorites_handler.toggle,

    # Maintenance
    'cache_clear':  maintenance.cache_clear,
}


def dispatch(handle: int, params: dict) -> None:
    action = params.get('action')
    fn = _ROUTES.get(action)
    if fn is None:
        kodi.log_warning(f"Unknown action {action!r}, falling back to home")
        fn = home.show
    try:
        fn(handle, params)
    except Exception as e:
        kodi.log_error(f"Unhandled error in action={action!r}: {e!r}")
        kodi.notify_error(kodi.t(30951))
        try:
            import xbmcplugin
            xbmcplugin.endOfDirectory(handle, succeeded=False)
        except Exception:
            pass
