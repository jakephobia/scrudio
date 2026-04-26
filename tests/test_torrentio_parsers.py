# -*- coding: utf-8 -*-
"""Pure-parser tests for torrentio.py — no Kodi runtime required.

Run with:
    pytest tests/

Or without pytest:
    python tests/test_torrentio_parsers.py
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TORRENTIO_PATH = ROOT / 'plugin.video.scrudio' / 'resources' / 'lib' / 'torrentio.py'


def _load_torrentio_in_isolation():
    """Load torrentio.py without triggering its package-level imports.

    The module begins with `from . import http, settings`, which would force a
    Kodi runtime. We mock those modules just enough to evaluate the parsers.
    """
    import types

    # Stub the relative imports
    fake_pkg = types.ModuleType('scrudio_test_pkg')
    fake_http = types.ModuleType('scrudio_test_pkg.http')
    fake_settings = types.ModuleType('scrudio_test_pkg.settings')
    fake_kodi = types.ModuleType('scrudio_test_pkg.kodi')
    fake_settings.has_rd = lambda: False
    fake_settings.rd_key = lambda: ''
    fake_settings.quality_filter = lambda: {'4K', '1080p', '720p', '480p'}
    fake_settings.hide_no_seeds = lambda: False
    fake_http.get_json = lambda *a, **kw: None
    fake_kodi.log_info = lambda *a, **kw: None
    fake_kodi.log_warning = lambda *a, **kw: None
    fake_kodi.log_error = lambda *a, **kw: None

    sys.modules['scrudio_test_pkg'] = fake_pkg
    sys.modules['scrudio_test_pkg.http'] = fake_http
    sys.modules['scrudio_test_pkg.settings'] = fake_settings
    sys.modules['scrudio_test_pkg.kodi'] = fake_kodi

    src = TORRENTIO_PATH.read_text(encoding='utf-8')
    # Rewrite the relative import to point to our stub package
    src = src.replace('from . import http, kodi, settings',
                      'from scrudio_test_pkg import http, kodi, settings')

    spec = importlib.util.spec_from_loader('torrentio_under_test', loader=None)
    module = importlib.util.module_from_spec(spec)
    exec(compile(src, str(TORRENTIO_PATH), 'exec'), module.__dict__)
    return module


t = _load_torrentio_in_isolation()


# ── parse_quality ────────────────────────────────────────────────────────────
def test_quality_4k():
    assert t.parse_quality('Torrentio\n4K HEVC') == '4K'
    assert t.parse_quality('2160p WEBDL') == '4K'

def test_quality_1080():
    assert t.parse_quality('Torrentio\n1080p WEBDL') == '1080p'

def test_quality_720():
    assert t.parse_quality('720p BluRay') == '720p'

def test_quality_480():
    assert t.parse_quality('480p TS') == '480p'

def test_quality_unknown():
    assert t.parse_quality('foo bar') == 'HD'
    assert t.parse_quality('') == 'HD'


# ── parse_seeds ──────────────────────────────────────────────────────────────
def test_seeds_basic():
    assert t.parse_seeds('Foo\n👤 1234 💾 2 GB') == 1234

def test_seeds_zero():
    assert t.parse_seeds('no seeds') == 0
    assert t.parse_seeds('') == 0

def test_seeds_no_space():
    # Some Torrentio responses omit the space after the emoji
    assert t.parse_seeds('👤42 💾 1 GB') == 42


# ── parse_size ───────────────────────────────────────────────────────────────
def test_size_gb():
    assert t.parse_size('👤 10 💾 2.5 GB') == '2.5 GB'

def test_size_mb():
    assert t.parse_size('💾 800 MB') == '800 MB'

def test_size_missing():
    assert t.parse_size('no size here') == ''


# ── parse_codec ──────────────────────────────────────────────────────────────
def test_codec_hevc():
    assert t.parse_codec('x265 HEVC 10bit') == 'HEVC'
    assert t.parse_codec('h265 release') == 'HEVC'

def test_codec_h264():
    assert t.parse_codec('x264 dvd-rip') == 'H.264'
    assert t.parse_codec('H264 release') == 'H.264'

def test_codec_av1():
    assert t.parse_codec('AV1 10bit') == 'AV1'

def test_codec_none():
    assert t.parse_codec('mystery release') == ''


# ── parse_provider ───────────────────────────────────────────────────────────
def test_provider_emoji():
    assert t.parse_provider('Torrentio\n4K HEVC ⚙️ EZTV') == 'EZTV'

def test_provider_fallback_secondline():
    assert t.parse_provider('Torrentio\nYTS') == 'YTS'

def test_provider_unknown():
    assert t.parse_provider('Solo una riga') == 'Unknown'


# ── parse_streams ────────────────────────────────────────────────────────────
def _stream(name, title, url='https://x/y', infoHash='abcd'):
    return {'name': name, 'title': title, 'url': url, 'infoHash': infoHash}


def test_parse_streams_drops_unplayable():
    raw = [_stream('Torrentio\n1080p', 'Movie 👤 10', url=None)]
    assert t.parse_streams(raw, {'1080p'}, drop_no_seeds=False) == []


def test_parse_streams_quality_filter():
    raw = [
        _stream('Torrentio\n1080p', 'A 👤 5', infoHash='a'),
        _stream('Torrentio\n720p', 'B 👤 5', infoHash='b'),
    ]
    out = t.parse_streams(raw, {'1080p'}, drop_no_seeds=False)
    assert len(out) == 1
    assert out[0]['quality'] == '1080p'


def test_parse_streams_sort_by_quality_then_cached_then_seeds():
    raw = [
        _stream('Torrentio\n720p', 'C 👤 100', infoHash='c'),
        _stream('Torrentio RD+\n1080p', 'A 👤 1', infoHash='a'),  # cached, low seeds
        _stream('Torrentio\n1080p', 'B 👤 50', infoHash='b'),     # uncached, more seeds
    ]
    out = t.parse_streams(raw, {'1080p', '720p'}, drop_no_seeds=False)
    assert [x['info_hash'] for x in out] == ['a', 'b', 'c']


def test_parse_streams_hide_no_seeds_keeps_rd_cached():
    raw = [
        _stream('Torrentio RD+\n1080p', 'A 👤 0', infoHash='a'),  # cached, seeds=0 → keep
        _stream('Torrentio\n1080p',     'B 👤 0', infoHash='b'),  # uncached, seeds=0 → drop
    ]
    out = t.parse_streams(raw, {'1080p'}, drop_no_seeds=True)
    assert [x['info_hash'] for x in out] == ['a']


def test_parse_streams_skips_non_dict():
    raw = [None, 'hello', 42, _stream('Torrentio\n1080p', 'A 👤 5')]
    out = t.parse_streams(raw, {'1080p'}, drop_no_seeds=False)
    assert len(out) == 1


# ── provider chain ───────────────────────────────────────────────────────────
def test_providers_torrentio_first():
    assert t.PROVIDERS[0][0] == 'Torrentio'


def test_providers_well_formed():
    """Every provider entry must be a (label, https-url) tuple."""
    for entry in t.PROVIDERS:
        assert isinstance(entry, tuple) and len(entry) == 2
        label, base = entry
        assert label and isinstance(label, str)
        assert base.startswith('https://')


# ── format_label ─────────────────────────────────────────────────────────────
def test_format_label_full():
    src = {
        'quality': '4K', 'codec': 'HEVC', 'size': '12 GB', 'seeds': 1234,
        'rd_cached': True, 'provider': 'EZTV', 'release': 'My.Movie.2024.4K',
    }
    label = t.format_label(src)
    assert '4K' in label
    assert 'HEVC' in label
    assert '12 GB' in label
    assert '1234' in label
    assert 'RD+' in label
    assert 'EZTV' in label
    assert 'My.Movie.2024.4K' in label


# ── poor man's runner ────────────────────────────────────────────────────────
if __name__ == '__main__':
    import inspect
    failed = 0
    tests = [(n, f) for n, f in globals().items()
             if n.startswith('test_') and inspect.isfunction(f)]
    for name, fn in tests:
        try:
            fn()
            print(f'  OK   {name}')
        except AssertionError as e:
            failed += 1
            print(f'  FAIL {name}: {e}')
        except Exception as e:
            failed += 1
            print(f'  ERROR {name}: {e!r}')
    print(f'\n{len(tests) - failed}/{len(tests)} passed')
    sys.exit(1 if failed else 0)
