# Scrudio — Master Document v3 (Kodi Add-on Edition)

**Add-on Kodi simil-Stremio per TCL 40S5400A · Kodi 21 Omega · Android TV 11**

> Documento unico di riferimento dopo il pivot da app Flutter ad add-on Kodi (2026-04-26).
> Sostituisce `scrudio_master_v2.md` (archiviato per riferimento storico).
> Mantiene gli obiettivi funzionali della v2 — catalogo TMDB, sorgenti Torrentio, Real-Debrid, riproduzione D-pad — ma ribalta lo stack: niente più Flutter/Dart/Android Studio, solo Python 3 dentro Kodi.

---

## 0. TL;DR

- **Cosa**: video plugin Kodi (`plugin.video.scrudio`) che mostra il catalogo TMDB, recupera le sorgenti via Torrentio (con risoluzione Real-Debrid) e le passa al player nativo Kodi.
- **Dove gira**: Kodi 21 Omega installato sulla TCL 40S5400A (Android TV 11, ARM64, 1 GB RAM, 1080p). In sviluppo gira anche su Kodi desktop (Windows/Linux/macOS) per debug rapido.
- **Distribuzione**: file `.zip` sideloadabile via "Installa da file zip" di Kodi.
- **Streaming**: **solo Real-Debrid**. Niente torrent on-device, niente Elementum/Torrest. Torrentio + RD restituisce URL HTTPS diretti che Kodi riproduce in 2-3 s.
- **Vincolo duro**: navigazione 100 % telecomando — gestita nativamente da Kodi (zero codice nostro per il D-pad).
- **Niente Real-Debrid → niente streaming**. La RD key è obbligatoria. Senza, l'add-on mostra solo il catalogo e un messaggio "configura RD nelle Impostazioni".

---

## 1. Decisioni Bloccate

| Voce | Scelta |
|---|---|
| Add-on id | `plugin.video.scrudio` |
| Versione | `1.0.0` |
| Provider | `jacop` |
| Target Kodi | **21 Omega** (`xbmc.python` 3.0.0, Python 3.11) |
| Posizione progetto | `Scrudio/plugin.video.scrudio/` |
| Linguaggio | Python 3.11 (zero codegen, zero build step) |
| HTTP | `script.module.requests` (preinstallato in Kodi 21) |
| Cache | JSON file in `special://profile/addon_data/plugin.video.scrudio/cache/` |
| Settings storage | `xbmcaddon.Addon().getSetting*/setSetting*` (file `settings.xml` autogenerato) |
| API key handling | utente le inserisce nelle Impostazioni dell'add-on (TMDB key, RD key opzionale ma richiesta per stream) |
| Strategia stream | **Solo Real-Debrid**: Torrentio config `realdebrid=<KEY>` → `stream.url` è già HTTP, nessun resolve aggiuntivo |
| Lingue UI | Italiano + Inglese (file `.po` in `resources/language/`) |
| Sottotitoli | Delegati all'add-on ufficiale Kodi `service.subtitles.opensubtitlescom` (zero codice nostro) |
| Preferiti | Custom in `special://profile/addon_data/plugin.video.scrudio/favorites.json` (i Favoriti nativi Kodi non gestiscono bene i parametri plugin) |
| Test | `pytest` su parser Torrentio + smoke test del router |
| Distribuzione | zip nominato `plugin.video.scrudio-1.0.0.zip`, install "Da file zip" |

---

## 2. Cosa cambia rispetto a v2

| Aspetto v2 (Flutter APK) | v3 (Kodi addon) |
|---|---|
| Linguaggio Dart | Python 3.11 |
| State `provider` (ChangeNotifier) | Stateless: ogni click ricarica `default.py` con nuovi `argv` |
| UI custom (`TvFocusable`, card, banner) | Liste native Kodi (`xbmcplugin.addDirectoryItem`) — la skin disegna tutto |
| Player `video_player + chewie` | `xbmc.Player` nativo |
| `flutter_go_torrent_streamer` (rischio alto) | Niente — solo URL HTTP da RD |
| `imageCache` 50 MB cap manuale | Kodi gestisce la texture cache da sé |
| `network_security_config.xml` | Non serve |
| `AndroidManifest.xml`, `build.gradle`, `MainActivity` | Non serve |
| `--dart-define` per le keys | Settings dell'add-on, persisted dal Kodi runtime |
| Soft keyboard custom (issue #147772) | Tastiera nativa Kodi, già perfetta D-pad |
| Sottotitoli OpenSubtitles custom | Add-on Kodi ufficiale — installato a parte dall'utente |
| Banner Leanback, foreground service | Non serve |
| RAM picco ≤ 250 MB stretto | Vincolo molto rilassato — Kodi è già ottimizzato |
| Build: `flutter build apk` | Build: `python build.py` → `.zip` |

---

## 3. Vincoli Dispositivo (TCL 40S5400A) — Kodi Edition

Molti dei "10 comandamenti" v2 spariscono perché Kodi li risolve da sé. Restano questi:

1. **Solo D-pad**: ogni interazione passa dal sistema di liste Kodi (frecce + OK + BACK). Le tastiere virtuali per ricerca usano `xbmcgui.Dialog().input()` (D-pad nativo).
2. **Niente blocking call lunghe** sul thread plugin: ogni HTTP > 100 ms va con `xbmcgui.DialogProgressBG` per feedback. Timeout fisso 10 s.
3. **Niente immagini > w780**: `image.tmdb.org/t/p/w780` per backdrop, `w342` per poster. Kodi cache è veloce ma su 1 GB RAM evitare 1280×720+ inutilmente.
4. **Persistenza solo in `special://profile/addon_data/...`**: mai scrivere altrove. La cache va periodicamente potata (TTL).
5. **Tasto BACK**: gestito da Kodi automaticamente. Non intercettare `onAction` salvo necessità.
6. **`reuselanguageinvoker=true`** in `addon.xml`: avvii successivi del plugin sono ~10× più veloci. **Critico** su 1 GB RAM. Implica: niente stato a livello modulo, tutte le inizializzazioni dentro `main()`.
7. **Niente thread daemon longevi** dal pluginsource: il plugin è effimero, finisce dopo `endOfDirectory`. Per task di sfondo serve un `service.py` separato (non in v1).

---

## 4. Stack & Dipendenze

### `addon.xml` (manifest)

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<addon id="plugin.video.scrudio" name="Scrudio" version="1.0.0" provider-name="jacop">
  <requires>
    <import addon="xbmc.python" version="3.0.0"/>
    <import addon="script.module.requests" version="2.27.1"/>
  </requires>
  <extension point="xbmc.python.pluginsource" library="default.py">
    <provides>video</provides>
  </extension>
  <extension point="xbmc.addon.metadata">
    <summary lang="en_GB">Stremio-like browser for TMDB + Torrentio (Real-Debrid).</summary>
    <summary lang="it_IT">Browser tipo Stremio per TMDB + Torrentio (Real-Debrid).</summary>
    <description lang="en_GB">Browse TMDB, fetch streams from Torrentio resolved through Real-Debrid, play with native Kodi player. Remote-only.</description>
    <description lang="it_IT">Sfoglia TMDB, recupera sorgenti via Torrentio risolte da Real-Debrid, riproduci col player nativo Kodi. Solo telecomando.</description>
    <platform>all</platform>
    <license>MIT</license>
    <language>en it</language>
    <reuselanguageinvoker>true</reuselanguageinvoker>
    <assets>
      <icon>resources/media/icon.png</icon>
      <fanart>resources/media/fanart.jpg</fanart>
    </assets>
  </extension>
</addon>
```

### Dipendenze esterne (utente)

- **Kodi 21 Omega** sulla TCL (sideload APK arm64-v8a da kodi.tv).
- **TMDB API key v3** (gratuita): https://www.themoviedb.org/settings/api
- **Real-Debrid premium + API token**: https://real-debrid.com/apitoken
- **Opzionale**: addon Kodi `service.subtitles.opensubtitlescom` per i sottotitoli (installabile dal repo ufficiale Kodi).

---

## 5. Struttura File

```
Scrudio/
├── scrudio_master_full.md    (v1 originale — archivio)
├── scrudio_master_v2.md      (v2 Flutter — archivio)
├── scrudio_master_v3.md      (questo file — attivo)
├── ciao.txt
├── build.py                  (zippa in plugin.video.scrudio-X.Y.Z.zip)
└── plugin.video.scrudio/
    ├── addon.xml
    ├── default.py            (entry point — solo bootstrap del router)
    ├── LICENSE.txt
    ├── README.md
    ├── changelog.txt
    ├── icon.png              -> resources/media/icon.png
    ├── fanart.jpg            -> resources/media/fanart.jpg
    └── resources/
        ├── settings.xml      (categorie: TMDB, Real-Debrid, Catalogo, Cache, Avanzate)
        ├── media/
        │   ├── icon.png      (256×256)
        │   └── fanart.jpg    (1920×1080)
        ├── language/
        │   ├── resource.language.en_gb/strings.po
        │   └── resource.language.it_it/strings.po
        └── lib/
            ├── __init__.py
            ├── router.py         (dispatch action -> handler)
            ├── kodi.py           (helpers: addon, log, dialog, listitem builder)
            ├── settings.py       (typed wrapper su xbmcaddon.Addon)
            ├── http.py           (requests session: timeout 10 s, retry 1, UA)
            ├── cache.py          (JSON cache su filesystem con TTL)
            ├── tmdb.py           (TMDB client: trending/popular/top_rated/search/details)
            ├── torrentio.py      (Torrentio client + parser qualità/seeds/size/codec)
            ├── favorites.py      (CRUD su favorites.json)
            └── handlers/
                ├── __init__.py
                ├── home.py       (root menu)
                ├── catalog.py    (liste TMDB)
                ├── search.py     (ricerca + history)
                ├── details.py    (dettaglio film o serie)
                ├── seasons.py    (lista stagioni/episodi)
                ├── sources.py    (lista Torrentio)
                ├── play.py       (setResolvedUrl)
                └── favorites.py  (gestione preferiti)

test/
├── test_torrentio_parser.py
└── test_router.py
```

---

## 6. Architettura Routing

Tutti i click in Kodi rilanciano `default.py` con un nuovo `sys.argv[2]` tipo `?action=catalog&kind=trending&page=1`. Pattern:

```
default.py
  └─ resources/lib/router.py:route(args)
        ├─ home.show()
        ├─ catalog.list(kind, page)
        ├─ search.prompt() / search.run(query, page)
        ├─ details.show(media_type, tmdb_id)
        ├─ seasons.list(tmdb_id) / seasons.episodes(tmdb_id, season)
        ├─ sources.list(media_type, imdb_id, season=, episode=)
        ├─ play.resolve(stream_url)
        └─ favorites.toggle / favorites.list
```

Routing minimale (no decoratori magici, niente `routing.Plugin`): un `dict[str, callable]` in `router.py`. Ogni handler restituisce dopo aver chiamato `xbmcplugin.endOfDirectory(handle)` o `xbmcplugin.setResolvedUrl(...)`.

---

## 7. Codice Chiave

### 7.1 `default.py`

```python
# -*- coding: utf-8 -*-
import sys
from urllib.parse import parse_qsl

from resources.lib import router

if __name__ == '__main__':
    handle = int(sys.argv[1])
    params = dict(parse_qsl(sys.argv[2][1:])) if len(sys.argv) > 2 else {}
    router.dispatch(handle, params)
```

### 7.2 `resources/lib/kodi.py`

```python
# -*- coding: utf-8 -*-
import sys
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
PROFILE_PATH = xbmcvfs.translatePath(ADDON.getAddonInfo('profile'))
RESOURCES_PATH = xbmcvfs.translatePath(ADDON.getAddonInfo('path')) + 'resources/'

BASE_URL = sys.argv[0] if len(sys.argv) > 0 else 'plugin://plugin.video.scrudio/'


def log(msg, level=xbmc.LOGINFO):
    xbmc.log(f'[{ADDON_ID}] {msg}', level)


def t(string_id: int) -> str:
    return ADDON.getLocalizedString(string_id)


def build_url(**params) -> str:
    return f'{BASE_URL}?{urlencode(params)}' if params else BASE_URL


def notify(msg: str, heading: str = ADDON_NAME, icon: str = xbmcgui.NOTIFICATION_INFO,
           millis: int = 3500):
    xbmcgui.Dialog().notification(heading, msg, icon, millis)


def confirm(heading: str, message: str) -> bool:
    return xbmcgui.Dialog().yesno(heading, message)


def keyboard(heading: str, default: str = '') -> str:
    return xbmcgui.Dialog().input(heading, default, type=xbmcgui.INPUT_ALPHANUM)


def make_listitem(title: str, info: dict | None = None,
                  art: dict | None = None,
                  is_playable: bool = False) -> xbmcgui.ListItem:
    li = xbmcgui.ListItem(label=title)
    if art:
        li.setArt(art)
    if info:
        vi = li.getVideoInfoTag()
        if 'title' in info:        vi.setTitle(info['title'])
        if 'plot' in info:         vi.setPlot(info['plot'] or '')
        if 'year' in info:         vi.setYear(int(info['year']) if info['year'] else 0)
        if 'rating' in info:       vi.setRating(float(info['rating']) if info['rating'] else 0.0)
        if 'votes' in info:        vi.setVotes(int(info['votes']) if info['votes'] else 0)
        if 'duration' in info:     vi.setDuration(int(info['duration']) if info['duration'] else 0)
        if 'genre' in info:        vi.setGenres(info['genre'] or [])
        if 'mediatype' in info:    vi.setMediaType(info['mediatype'])
        if 'imdbnumber' in info:   vi.setIMDBNumber(info['imdbnumber'] or '')
        if 'season' in info:       vi.setSeason(int(info['season']))
        if 'episode' in info:      vi.setEpisode(int(info['episode']))
    if is_playable:
        li.setProperty('IsPlayable', 'true')
    return li
```

### 7.3 `resources/lib/settings.py`

```python
# -*- coding: utf-8 -*-
from .kodi import ADDON


def tmdb_key() -> str:
    return ADDON.getSettingString('tmdb_api_key').strip()


def rd_key() -> str:
    return ADDON.getSettingString('rd_api_key').strip()


def has_rd() -> bool:
    return bool(rd_key())


def language() -> str:
    # 'it-IT' | 'en-US'
    return ADDON.getSettingString('tmdb_language') or 'it-IT'


def quality_filter() -> set[str]:
    """Restituisce l'insieme delle qualità abilitate."""
    out = set()
    for q in ('4k', '1080p', '720p', '480p'):
        if ADDON.getSettingBool(f'quality_{q}'):
            out.add(q.upper() if q == '4k' else q)
    return out or {'4K', '1080p', '720p', '480p'}


def cache_ttl_hours() -> int:
    return ADDON.getSettingInt('cache_ttl_hours') or 6


def hide_no_seeds() -> bool:
    return ADDON.getSettingBool('hide_no_seeds')


def open_addon_settings():
    ADDON.openSettings()
```

### 7.4 `resources/lib/http.py`

```python
# -*- coding: utf-8 -*-
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from .kodi import ADDON_NAME, ADDON_VERSION, log

TIMEOUT = 10
USER_AGENT = f'{ADDON_NAME}/{ADDON_VERSION} (Kodi)'

_session = None


def _build_session() -> requests.Session:
    s = requests.Session()
    retry = Retry(total=1, backoff_factor=0.4,
                  status_forcelist=(500, 502, 503, 504),
                  allowed_methods=frozenset(['GET']))
    adapter = HTTPAdapter(max_retries=retry, pool_connections=4, pool_maxsize=4)
    s.mount('http://', adapter)
    s.mount('https://', adapter)
    s.headers.update({'User-Agent': USER_AGENT, 'Accept': 'application/json'})
    return s


def session() -> requests.Session:
    global _session
    if _session is None:
        _session = _build_session()
    return _session


def get_json(url: str, params: dict | None = None, headers: dict | None = None):
    try:
        r = session().get(url, params=params, headers=headers, timeout=TIMEOUT)
        if r.status_code == 200:
            return r.json()
        log(f'HTTP {r.status_code} on {url}', 2)  # LOGWARNING
    except Exception as e:
        log(f'HTTP error {url}: {e}', 4)  # LOGERROR
    return None
```

### 7.5 `resources/lib/cache.py`

```python
# -*- coding: utf-8 -*-
import hashlib
import json
import os
import time

import xbmcvfs

from .kodi import PROFILE_PATH

CACHE_DIR = os.path.join(PROFILE_PATH, 'cache')


def _ensure_dir():
    if not xbmcvfs.exists(CACHE_DIR):
        xbmcvfs.mkdirs(CACHE_DIR)


def _key_to_path(key: str) -> str:
    h = hashlib.sha1(key.encode('utf-8')).hexdigest()
    return os.path.join(CACHE_DIR, f'{h}.json')


def get(key: str, ttl_seconds: int):
    path = _key_to_path(key)
    if not os.path.exists(path):
        return None
    try:
        with open(path, 'r', encoding='utf-8') as f:
            wrapped = json.load(f)
        if time.time() - wrapped['ts'] > ttl_seconds:
            return None
        return wrapped['data']
    except Exception:
        return None


def set(key: str, data) -> None:
    _ensure_dir()
    path = _key_to_path(key)
    try:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump({'ts': time.time(), 'data': data}, f)
    except Exception:
        pass


def clear():
    if xbmcvfs.exists(CACHE_DIR):
        for name in os.listdir(CACHE_DIR):
            try:
                os.remove(os.path.join(CACHE_DIR, name))
            except OSError:
                pass
```

### 7.6 `resources/lib/tmdb.py` (estratto)

```python
# -*- coding: utf-8 -*-
from . import cache, http, settings

BASE = 'https://api.themoviedb.org/3'
IMG_POSTER = 'https://image.tmdb.org/t/p/w342'
IMG_BACKDROP = 'https://image.tmdb.org/t/p/w780'


def _ck(*parts) -> str:
    return 'tmdb:' + ':'.join(str(p) for p in parts)


def _params(extra: dict | None = None) -> dict:
    p = {'api_key': settings.tmdb_key(), 'language': settings.language()}
    if extra:
        p.update(extra)
    return p


def _cached_get(path: str, params: dict, ttl_h: int | None = None):
    ttl = (ttl_h if ttl_h is not None else settings.cache_ttl_hours()) * 3600
    key = _ck(path, sorted(params.items()))
    hit = cache.get(key, ttl)
    if hit is not None:
        return hit
    data = http.get_json(f'{BASE}{path}', params=params)
    if data is not None:
        cache.set(key, data)
    return data


def trending(media_type: str = 'all', window: str = 'week', page: int = 1):
    return _cached_get(f'/trending/{media_type}/{window}', _params({'page': page}))


def popular_movies(page: int = 1):
    return _cached_get('/movie/popular', _params({'page': page}))


def popular_tv(page: int = 1):
    return _cached_get('/tv/popular', _params({'page': page}))


def top_rated_movies(page: int = 1):
    return _cached_get('/movie/top_rated', _params({'page': page}))


def top_rated_tv(page: int = 1):
    return _cached_get('/tv/top_rated', _params({'page': page}))


def search_multi(q: str, page: int = 1):
    return _cached_get('/search/multi', _params({'query': q, 'page': page}), ttl_h=1)


def details(media_type: str, tmdb_id: int):
    return _cached_get(
        f'/{media_type}/{tmdb_id}',
        _params({'append_to_response': 'external_ids,credits,videos'}),
        ttl_h=24,
    )


def season(tmdb_id: int, season_number: int):
    return _cached_get(f'/tv/{tmdb_id}/season/{season_number}', _params(), ttl_h=24)
```

### 7.7 `resources/lib/torrentio.py` (logica completa)

```python
# -*- coding: utf-8 -*-
import re
from urllib.parse import quote

from . import http, settings

BASE = 'https://torrentio.strem.fun'

_RE_SEEDS = re.compile(r'👤\s*(\d+)')
_RE_SIZE = re.compile(r'💾\s*([\d.,]+\s*[GMKT]B)', re.IGNORECASE)
_RE_PROVIDER = re.compile(r'⚙️\s*([\w.\-]+)')


def parse_quality(name: str) -> str:
    u = name.upper()
    if '2160' in u or '4K' in u:  return '4K'
    if '1080' in u:                return '1080p'
    if '720' in u:                 return '720p'
    if '480' in u:                 return '480p'
    return 'HD'


def parse_seeds(title: str) -> int:
    m = _RE_SEEDS.search(title)
    return int(m.group(1)) if m else 0


def parse_size(title: str) -> str:
    m = _RE_SIZE.search(title)
    return m.group(1).strip() if m else ''


def parse_provider(name: str) -> str:
    m = _RE_PROVIDER.search(name)
    if m:
        return m.group(1)
    lines = name.split('\n')
    return lines[1].strip() if len(lines) > 1 else 'Unknown'


def parse_codec(name: str) -> str:
    u = name.upper()
    if 'HEVC' in u or 'H265' in u or 'X265' in u: return 'HEVC'
    if 'H264' in u or 'X264' in u:                 return 'H.264'
    if 'AV1' in u:                                 return 'AV1'
    return ''


def first_line(s: str) -> str:
    return s.split('\n', 1)[0].strip()


def get_streams(imdb_id: str, media_type: str, season: int = 0, episode: int = 0) -> list[dict]:
    """media_type: 'movie' | 'series'."""
    if not settings.has_rd():
        return []
    rd = settings.rd_key()
    config = f'realdebrid={rd}/'
    sid = imdb_id if media_type == 'movie' else f'{imdb_id}:{season}:{episode}'
    url = f'{BASE}/{config}stream/{media_type}/{sid}.json'
    data = http.get_json(url) or {}
    raw = data.get('streams') or []

    allowed = settings.quality_filter()
    hide_no_seeds = settings.hide_no_seeds()

    out = []
    for s in raw:
        name = s.get('name') or ''
        title = s.get('title') or ''
        quality = parse_quality(name)
        if quality not in allowed and quality != 'HD':
            continue
        seeds = parse_seeds(title)
        if hide_no_seeds and seeds == 0 and not s.get('url'):
            continue
        out.append({
            'title': first_line(title),
            'quality': quality,
            'seeds': seeds,
            'size': parse_size(title),
            'provider': parse_provider(name),
            'codec': parse_codec(name),
            'url': s.get('url'),               # HTTP diretto post-RD
            'info_hash': s.get('infoHash'),
            'file_idx': s.get('fileIdx'),
        })
    out.sort(key=lambda x: x['seeds'], reverse=True)
    return out
```

### 7.8 `resources/lib/router.py`

```python
# -*- coding: utf-8 -*-
from .handlers import home, catalog, search, details, seasons, sources, play, favorites
from .kodi import log


_ROUTES = {
    None:           home.show,
    'home':         home.show,
    'catalog':      catalog.list_,
    'search':       search.prompt,
    'search_run':   search.run,
    'details':      details.show,
    'seasons':      seasons.list_seasons,
    'episodes':     seasons.list_episodes,
    'sources':      sources.list_,
    'play':         play.resolve,
    'fav_toggle':   favorites.toggle,
    'fav_list':     favorites.list_,
    'settings':     home.open_settings,
}


def dispatch(handle: int, params: dict):
    action = params.get('action')
    fn = _ROUTES.get(action)
    if fn is None:
        log(f'Unknown action {action!r}, falling back to home', 3)  # WARNING
        fn = home.show
    fn(handle, params)
```

### 7.9 `resources/lib/handlers/home.py` (estratto)

```python
# -*- coding: utf-8 -*-
import xbmcplugin

from .. import kodi


def show(handle: int, params: dict):
    items = [
        (kodi.t(30100), 'catalog', {'kind': 'trending'}),
        (kodi.t(30101), 'catalog', {'kind': 'popular_movies'}),
        (kodi.t(30102), 'catalog', {'kind': 'popular_tv'}),
        (kodi.t(30103), 'catalog', {'kind': 'top_rated_movies'}),
        (kodi.t(30104), 'catalog', {'kind': 'top_rated_tv'}),
        (kodi.t(30110), 'search', {}),
        (kodi.t(30120), 'fav_list', {}),
        (kodi.t(30130), 'settings', {}),
    ]
    for label, action, extra in items:
        url = kodi.build_url(action=action, **extra)
        li = kodi.make_listitem(label)
        xbmcplugin.addDirectoryItem(handle, url, li, isFolder=True)
    xbmcplugin.setContent(handle, 'files')
    xbmcplugin.endOfDirectory(handle)


def open_settings(handle: int, params: dict):
    from .. import settings
    settings.open_addon_settings()
```

### 7.10 `resources/lib/handlers/play.py`

```python
# -*- coding: utf-8 -*-
import xbmcplugin

from .. import kodi


def resolve(handle: int, params: dict):
    url = params.get('url')
    if not url:
        kodi.notify(kodi.t(30901), icon='error')
        xbmcplugin.setResolvedUrl(handle, False, kodi.make_listitem('Error'))
        return
    li = kodi.make_listitem(params.get('title') or 'Scrudio', is_playable=True)
    li.setPath(url)
    xbmcplugin.setResolvedUrl(handle, True, li)
```

(Gli altri handler — `catalog.py`, `search.py`, `details.py`, `seasons.py`, `sources.py`, `favorites.py` — seguono lo stesso pattern: chiamano i service in `lib/`, costruiscono `ListItem`, fanno `addDirectoryItem` o `setResolvedUrl`.)

---

## 8. `resources/settings.xml`

```xml
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<settings version="1">
    <section id="plugin.video.scrudio">

        <category id="api" label="30200">
            <group id="tmdb" label="30210">
                <setting id="tmdb_api_key" type="string" label="30211">
                    <default></default>
                    <constraints><allowempty>true</allowempty></constraints>
                    <control type="edit" format="string"><heading>30211</heading></control>
                </setting>
                <setting id="tmdb_language" type="string" label="30212">
                    <default>it-IT</default>
                    <constraints>
                        <options>
                            <option label="Italiano">it-IT</option>
                            <option label="English">en-US</option>
                        </options>
                    </constraints>
                    <control type="list" format="string"/>
                </setting>
            </group>

            <group id="rd" label="30220">
                <setting id="rd_api_key" type="string" label="30221">
                    <default></default>
                    <constraints><allowempty>true</allowempty></constraints>
                    <control type="edit" format="string">
                        <heading>30221</heading>
                    </control>
                </setting>
            </group>
        </category>

        <category id="catalog" label="30300">
            <setting id="quality_4k"   type="boolean" label="30301"><default>true</default><control type="toggle"/></setting>
            <setting id="quality_1080p" type="boolean" label="30302"><default>true</default><control type="toggle"/></setting>
            <setting id="quality_720p" type="boolean" label="30303"><default>true</default><control type="toggle"/></setting>
            <setting id="quality_480p" type="boolean" label="30304"><default>false</default><control type="toggle"/></setting>
            <setting id="hide_no_seeds" type="boolean" label="30310"><default>true</default><control type="toggle"/></setting>
        </category>

        <category id="cache" label="30400">
            <setting id="cache_ttl_hours" type="integer" label="30401">
                <default>6</default>
                <constraints><minimum>1</minimum><maximum>168</maximum><step>1</step></constraints>
                <control type="slider" format="integer"><formatlabel>30402</formatlabel></control>
            </setting>
            <setting id="cache_clear" type="action" label="30410">
                <data>RunPlugin(plugin://plugin.video.scrudio/?action=cache_clear)</data>
                <control type="button" format="action"><heading>30410</heading></control>
            </setting>
        </category>

    </section>
</settings>
```

(I label `3xxxx` mappano agli stringhe in `resources/language/.../strings.po` — IT + EN.)

---

## 9. Plan Operativo (Fasi)

### Fase 1 — Skeleton installabile (½ giornata)

- `addon.xml`, `default.py` minimal, `resources/lib/router.py`, `resources/lib/kodi.py`, `resources/lib/handlers/home.py`.
- `resources/settings.xml` con le 4 chiavi base.
- `resources/language/.../strings.po` IT + EN.
- `resources/media/icon.png` (256×256), `fanart.jpg` (1920×1080) — placeholder, anche solo monocromatici sage.
- **Exit**: zip installabile, l'add-on appare in Video → Add-on, mostra il menu home, tasti Indietro/OK funzionano.

### Fase 2 — Lib core (½ giornata)

- `http.py` (session + retry + UA + timeout 10 s).
- `cache.py` (JSON + TTL).
- `settings.py` (typed wrapper).
- Test offline che `cache.get` ritorni `None` su miss e dato corretto su hit.

### Fase 3 — TMDB browsing (1 giornata)

- `tmdb.py` (trending, popular, top_rated, search, details, season).
- `handlers/catalog.py`: pagina N + "Pagina successiva" come item finale.
- `handlers/search.py`: `xbmcgui.Dialog().input(...)`, salva ultime 20 query in `search_history.json`.
- `handlers/details.py`: per i film mostra subito un voce "🎬 Trova sorgenti"; per le serie va in `seasons`.
- **Exit**: si naviga TMDB con D-pad, immagini caricano, dettagli appaiono.

### Fase 4 — Torrentio + Real-Debrid (½ giornata)

- `torrentio.py` (logica §7.7).
- `handlers/sources.py`: build label tipo `[4K · HEVC · 2.4 GB · 👤 1234] EZTV — My.Show.S01E01...`. Riempi `info` ListItem con `title`, `mediatype=video`. `IsPlayable=true`. URL → `?action=play&url=<encoded>&title=<encoded>`.
- Validazione RD key: se vuota, `sources.list_` mostra un dialog "Configura Real-Debrid" e apre le impostazioni.
- **Exit**: lista sorgenti per 3 film + 2 serie.

### Fase 5 — Playback (¼ giornata)

- `handlers/play.py`: `setResolvedUrl` con `ListItem.setPath(url)` e `IsPlayable=true`.
- Test: avvio film, OK = play/pause, frecce ←→ = seek (gestiti da Kodi), Indietro = stop.
- **Exit**: un film parte in < 5 s da home → catalogo → dettaglio → sorgente → play.

### Fase 6 — Polish IT/EN, preferiti, qualità (½ giornata)

- `favorites.py` con `favorites.json`.
- `details.show`: voce "❤ Aggiungi/Rimuovi preferito".
- Filtro qualità da settings (§7.7, già pronto).
- "Cancella cache" da settings (action button).
- **Exit**: tutte le stringhe localizzate, preferiti persistono.

### Fase 7 — Build & deploy (¼ giornata)

- `build.py` script che produce `plugin.video.scrudio-1.0.0.zip` (struttura corretta: cartella `plugin.video.scrudio/` come root del zip).
- `flutter_test` rimosso dal vocabolario; usiamo `pytest` su `tests/test_torrentio_parser.py`.
- Documentare install in `README.md`: download zip → Kodi → Add-on → "Installa da file zip".
- Test installazione su Kodi 21 desktop, poi sulla TCL.

---

## 10. Build & Deploy

### `build.py`

```python
# -*- coding: utf-8 -*-
"""Costruisce plugin.video.scrudio-<version>.zip pronto per 'Installa da file zip'."""
import os
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / 'plugin.video.scrudio'

def get_version() -> str:
    addon_xml = (SRC / 'addon.xml').read_text(encoding='utf-8')
    return re.search(r'version="([^"]+)"', addon_xml).group(1)

def main():
    version = get_version()
    out = ROOT / 'dist' / f'plugin.video.scrudio-{version}.zip'
    out.parent.mkdir(exist_ok=True)
    if out.exists():
        out.unlink()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in SRC.rglob('*'):
            if any(part in {'__pycache__', '.pytest_cache'} for part in path.parts):
                continue
            if path.is_file():
                z.write(path, arcname=path.relative_to(ROOT))
    print(f'Built {out} ({out.stat().st_size // 1024} KB)')

if __name__ == '__main__':
    main()
```

### Install su Kodi

1. PC dev: `python build.py` → `dist/plugin.video.scrudio-1.0.0.zip`.
2. Copia zip su pendrive USB o servilo via HTTP semplice (`python -m http.server 8000`).
3. Kodi → Impostazioni → Add-on → **Abilita origini sconosciute** (la prima volta).
4. Kodi → Add-on → "Installa da file zip" → seleziona il zip.
5. Apri **Video → Add-on → Scrudio** → Impostazioni → inserisci TMDB key e RD key.

### Aggiornamento su TV via ADB (rapido in dev)

```powershell
adb push dist\plugin.video.scrudio-1.0.0.zip /sdcard/Download/
adb shell input keyevent KEYCODE_HOME
# Poi su Kodi: "Installa da file zip" -> /sdcard/Download/...
```

In alternativa, una volta abilitato il **webserver Kodi** (Impostazioni → Servizi → Controllo → Consenti controllo remoto via HTTP), si può fare `POST /jsonrpc` con `Addons.ExecuteAddon` per ricaricarlo dopo l'aggiornamento.

---

## 11. Testing

```python
# tests/test_torrentio_parser.py
from plugin.video.scrudio.resources.lib import torrentio as t

def test_quality():
    assert t.parse_quality('Torrentio\n4K HEVC') == '4K'
    assert t.parse_quality('1080p WEBDL') == '1080p'
    assert t.parse_quality('foo') == 'HD'

def test_seeds():
    assert t.parse_seeds('Foo\n👤 1234 💾 2 GB') == 1234
    assert t.parse_seeds('no seeds') == 0

def test_size():
    assert t.parse_size('👤 10 💾 2.5 GB') == '2.5 GB'

def test_codec():
    assert t.parse_codec('x265 HEVC') == 'HEVC'
    assert t.parse_codec('av1 10bit') == 'AV1'
```

> Nota: `pytest` lo lanciamo dal PC con `PYTHONPATH=plugin.video.scrudio` e moduli xbmc mockati (creiamo `tests/conftest.py` che inserisce stubs vuoti per `xbmc`, `xbmcgui`, `xbmcplugin`, `xbmcaddon`, `xbmcvfs` quando `torrentio` viene importato indirettamente). Per la v1 testiamo solo i parser puri (zero dipendenza Kodi).

---

## 12. Rischi & Mitigazioni

| Rischio | Probabilità | Impatto | Mitigazione |
|---|---|---|---|
| TMDB rate limit (40 req / 10 s) | Bassa | Catalogo lento | Cache TTL 6 h default |
| Torrentio cambia struttura JSON | Media | Zero sorgenti | Parser difensivo; log raw + notifica utente; fallback "no sources" |
| Real-Debrid token revocato | Bassa | Niente play | Notifica con apertura settings; controllo `/user` di RD all'avvio |
| `script.module.requests` non disponibile su Kodi 21 | Bassissima | App non parte | Già preinstallato; fallback `urllib.request` se necessario |
| Filesystem cache cresce | Media | Spazio TV | Cleanup TTL + action "Svuota cache" |
| Skin custom su TV cambia comportamento ListItem | Bassa | UI strana | Usiamo solo proprietà standard `setArt`/`setInfo` |
| Caratteri emoji 👤💾 non passano regex | Bassa | Seeds 0 | Test parser su titoli reali (Fase 4) |
| TCL kill background app | N/A | — | Non abbiamo background — il plugin è effimero |

---

## 13. Master TODO (consolidato v3)

### 🔴 Bloccante
- [ ] `addon.xml` + `default.py` + `home.py` → installa in Kodi
- [ ] TMDB key + RD key in settings, validazione minima
- [ ] `tmdb.py` lista trending + dettaglio
- [ ] `torrentio.py` con RD config
- [ ] `play.py` setResolvedUrl
- [ ] Test fisico TCL: pinwheel → play < 10 s

### 🟡 Alta
- [ ] Cache TTL JSON
- [ ] Search con `Dialog().input` + history 20 voci
- [ ] Pagina N + "Pagina successiva" sui cataloghi
- [ ] Filtro qualità + hide-no-seeds
- [ ] Preferiti JSON
- [ ] Localizzazione IT + EN complete
- [ ] `build.py` zip build
- [ ] README.md install instructions

### 🟢 Media
- [ ] Sezione "Continua a guardare" (richiede service.py separato — fuori v1?)
- [ ] Trailer YouTube (apre `plugin.video.youtube` se installato)
- [ ] Cronologia ricerche cancellabile
- [ ] Migliore UX errori (dialog invece di notifica per casi gravi)

### ⚪ Bassa
- [ ] Profili multipli (= cartelle settings separate, fattibile)
- [ ] Notifiche nuovi episodi (richiede service permanente)
- [ ] Tema/skin custom
- [ ] Repository Kodi self-hosted per auto-update

---

## 14. Diagnostica

### Log Kodi

- Windows: `%APPDATA%\Kodi\kodi.log`
- Android: `/sdcard/Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log` (o usa `adb logcat -s xbmc`)

Filtri utili:

```powershell
# Solo righe Scrudio
Get-Content "$env:APPDATA\Kodi\kodi.log" -Wait -Tail 50 | Select-String "plugin.video.scrudio"
```

```bash
# Su Android TV
adb shell "tail -f /sdcard/Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log" | grep scrudio
```

### Smoke test rapido senza Kodi

```powershell
python -c "from plugin.video.scrudio.resources.lib import torrentio; print(torrentio.parse_quality('1080p WEBDL'))"
```

### Webserver Kodi (dev)

Abilita Impostazioni → Servizi → Controllo → "Consenti controllo via HTTP" (porta 8080). Test:

```powershell
curl http://<ip-tv>:8080/jsonrpc -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"Addons.ExecuteAddon","params":{"addonid":"plugin.video.scrudio"},"id":1}'
```

---

## 15. Tre Principi Non Negoziabili

1. **Stateless**: il pluginsource muore dopo `endOfDirectory`. Niente variabili globali con dati utente; tutto su disco o in `xbmc.Window` properties solo se davvero indispensabile.
2. **Fail soft**: una richiesta HTTP fallita mostra notifica + lista vuota, mai un'eccezione che blocca Kodi. Tutti gli ingressi esterni (TMDB, Torrentio, RD) sono `try/except` con log.
3. **D-pad first**: ogni interazione è raggiungibile con frecce + OK + Indietro. Niente azioni che dipendono dal mouse o dalla long-press.

> "Kodi handles the hard parts of TV UX. Don't fight the framework — feed it."

---

## 16. Storico decisioni

- **2026-04-26 v3.0** — Pivot da Flutter APK a Kodi add-on. Stack Python 3.11 / Kodi 21 Omega. Streaming solo via Real-Debrid. Rimossi tutti i vincoli di build Android.
- **2026-04-?? v2** — Master document Flutter (archiviato come `scrudio_master_v2.md`).
- **2026-04-?? v1** — Master originale (archiviato come `scrudio_master_full.md`).
