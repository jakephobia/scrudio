# -*- coding: utf-8 -*-
"""HTTP client with sane defaults for a Kodi plugin.

- Single shared `requests.Session` with connection pooling.
- 10 s timeout, 1 retry on 5xx with backoff.
- Custom User-Agent.
- All errors swallowed and logged — callers get None on failure.
"""
from __future__ import annotations

from typing import Any, Optional

import requests
from requests.adapters import HTTPAdapter

try:  # urllib3 v2 location
    from urllib3.util.retry import Retry
except ImportError:  # very old fallback
    from requests.packages.urllib3.util.retry import Retry  # type: ignore

from .kodi import ADDON_NAME, ADDON_VERSION, log_warning, log_error

TIMEOUT = 10  # seconds
USER_AGENT = f'{ADDON_NAME}/{ADDON_VERSION} (Kodi)'

_session: Optional[requests.Session] = None


def _build_session() -> requests.Session:
    s = requests.Session()
    retry = Retry(
        total=2,                        # up to 2 retries on transient 5xx
        backoff_factor=0.4,             # 0.4 s, 0.8 s
        status_forcelist=(500, 502, 503, 504, 522, 524),
        allowed_methods=frozenset(['GET']),
        respect_retry_after_header=True,
    )
    adapter = HTTPAdapter(max_retries=retry, pool_connections=4, pool_maxsize=4)
    s.mount('http://', adapter)
    s.mount('https://', adapter)
    s.headers.update({
        'User-Agent': USER_AGENT,
        'Accept': 'application/json',
    })
    return s


def session() -> requests.Session:
    global _session
    if _session is None:
        _session = _build_session()
    return _session


def get_json(url: str,
             params: Optional[dict] = None,
             headers: Optional[dict] = None,
             timeout: int = TIMEOUT) -> Optional[Any]:
    """GET a URL and return parsed JSON, or None on any failure."""
    try:
        r = session().get(url, params=params, headers=headers, timeout=timeout)
    except requests.exceptions.Timeout:
        log_warning(f'Timeout on GET {url}')
        return None
    except requests.exceptions.ConnectionError as e:
        log_warning(f'Connection error on GET {url}: {e}')
        return None
    except Exception as e:
        log_error(f'Unexpected HTTP error on {url}: {e!r}')
        return None

    if r.status_code != 200:
        log_warning(f'HTTP {r.status_code} on {url}')
        return None

    try:
        return r.json()
    except ValueError as e:
        log_error(f'Invalid JSON from {url}: {e}')
        return None


def get_text(url: str,
             params: Optional[dict] = None,
             headers: Optional[dict] = None,
             timeout: int = TIMEOUT) -> Optional[str]:
    """GET a URL and return text, or None on failure."""
    try:
        r = session().get(url, params=params, headers=headers, timeout=timeout)
        if r.status_code == 200:
            return r.text
        log_warning(f'HTTP {r.status_code} on {url}')
    except Exception as e:
        log_error(f'HTTP error on {url}: {e!r}')
    return None
