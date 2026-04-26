# -*- coding: utf-8 -*-
"""Scrudio — Kodi add-on entry point.

This file is invoked by Kodi every time the user clicks on a directory item
that points to plugin://plugin.video.scrudio/?action=...
It must stay tiny: parse argv, hand off to the router.
"""
import sys
from urllib.parse import parse_qsl

from resources.lib import router


def _parse_params(qs: str) -> dict:
    if not qs:
        return {}
    if qs.startswith('?'):
        qs = qs[1:]
    return dict(parse_qsl(qs, keep_blank_values=True))


if __name__ == '__main__':
    handle = int(sys.argv[1]) if len(sys.argv) > 1 else -1
    params = _parse_params(sys.argv[2]) if len(sys.argv) > 2 else {}
    router.dispatch(handle, params)
