# -*- coding: utf-8 -*-
"""Action dispatcher.

Maps the `action` query parameter to a handler function. Handlers all share
the signature `(handle: int, params: dict) -> None`.

Phase 1: only `home` and `settings` are real; the rest are stubs that show
"Coming soon" and end the directory cleanly so Kodi doesn't hang.
"""
from .handlers import home, catalog, search, seasons, sources, play
from .handlers import favorites as favorites_handler
from . import kodi


_ROUTES = {
    None:           home.show,
    '':             home.show,
    'home':         home.show,
    'settings':     home.open_settings,

    # Phase 3 — TMDB browsing
    'catalog':      catalog.list_,
    'search':       search.prompt,
    'search_run':   search.run,
    'seasons':      seasons.list_seasons,
    'episodes':     seasons.list_episodes,

    # Phase 4-5 — sources + playback
    'sources':      sources.list_,
    'play':         play.resolve,

    # Phase 6 — favorites
    'fav_list':     favorites_handler.list_,
    'fav_toggle':   favorites_handler.toggle,
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
