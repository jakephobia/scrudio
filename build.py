# -*- coding: utf-8 -*-
"""Build the Scrudio plugin AND the Kodi repository tree.

Outputs (everything under `repo/` is what GitHub serves to Kodi):

    repo/
        addons.xml
        addons.xml.md5
        plugin.video.scrudio/
            plugin.video.scrudio-<ver>.zip
            icon.png
            fanart.jpg
            changelog.txt
        repository.scrudio/
            repository.scrudio-<ver>.zip
            icon.png

Run:  python build.py
"""
from __future__ import annotations

import hashlib
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).parent.resolve()
REPO_DIR = ROOT / 'repo'

# Each tuple: (folder containing the addon source = addon id)
ADDONS = (
    'plugin.video.scrudio',
    'repository.scrudio',
)

EXCLUDE_DIRS = {'__pycache__', '.pytest_cache', '.mypy_cache', '.git'}
EXCLUDE_SUFFIX = {'.pyc', '.pyo'}


# ── helpers ─────────────────────────────────────────────────────────────────
def parse_addon_xml(path: Path) -> tuple[str, str]:
    """Return (addon_id, version) from an addon.xml."""
    text = path.read_text(encoding='utf-8')
    m_id = re.search(r'<addon\s[^>]*\bid="([^"]+)"', text)
    m_ver = re.search(r'<addon\s[^>]*\bversion="([^"]+)"', text)
    if not (m_id and m_ver):
        raise RuntimeError(f'Cannot parse id/version in {path}')
    return m_id.group(1), m_ver.group(1)


def iter_addon_files(addon_root: Path):
    for path in addon_root.rglob('*'):
        if not path.is_file():
            continue
        if any(part in EXCLUDE_DIRS for part in path.parts):
            continue
        if path.suffix in EXCLUDE_SUFFIX:
            continue
        yield path


def zip_addon(addon_id: str, version: str) -> Path:
    """Zip an addon's source tree under the canonical name expected by Kodi."""
    src = ROOT / addon_id
    if not src.exists():
        raise RuntimeError(f'Addon source missing: {src}')

    out_dir = REPO_DIR / addon_id
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f'{addon_id}-{version}.zip'
    if out.exists():
        out.unlink()

    n = 0
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in iter_addon_files(src):
            # Inside the zip, path must start with the addon_id folder.
            arcname = str(addon_id / path.relative_to(src)).replace('\\', '/')
            z.write(path, arcname=arcname)
            n += 1
    return out


def copy_addon_assets(addon_id: str) -> None:
    """Copy icon/fanart/changelog next to the zip so Kodi can preview them."""
    src = ROOT / addon_id
    dst = REPO_DIR / addon_id

    candidates = [
        ('icon.png',                   ['icon.png',
                                        'resources/media/icon.png']),
        ('fanart.jpg',                 ['fanart.jpg',
                                        'resources/media/fanart.jpg']),
        ('changelog.txt',              ['changelog.txt']),
    ]
    for dst_name, search_paths in candidates:
        for sp in search_paths:
            p = src / sp
            if p.exists():
                shutil.copy2(p, dst / dst_name)
                break


def write_addons_xml(versions: dict[str, str]) -> None:
    """Concatenate every addon's <addon>…</addon> into repo/addons.xml."""
    root = ET.Element('addons')
    for addon_id in versions:
        src_xml = ROOT / addon_id / 'addon.xml'
        node = ET.fromstring(src_xml.read_text(encoding='utf-8'))
        root.append(node)

    # Pretty-print with stdlib only
    ET.indent(root, space='    ')
    out = REPO_DIR / 'addons.xml'
    out.write_bytes(b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
                    + ET.tostring(root, encoding='utf-8'))

    md5 = hashlib.md5(out.read_bytes()).hexdigest()
    (REPO_DIR / 'addons.xml.md5').write_text(md5, encoding='utf-8')


# ── main ────────────────────────────────────────────────────────────────────
def main() -> int:
    REPO_DIR.mkdir(exist_ok=True)
    versions: dict[str, str] = {}

    for addon_id in ADDONS:
        src = ROOT / addon_id
        if not src.exists():
            print(f'SKIP  {addon_id} (source folder missing)')
            continue
        addon_xml = src / 'addon.xml'
        parsed_id, version = parse_addon_xml(addon_xml)
        if parsed_id != addon_id:
            print(f'WARN  {addon_xml} has id={parsed_id!r} but lives in {addon_id!r}',
                  file=sys.stderr)
        versions[addon_id] = version

        zpath = zip_addon(addon_id, version)
        copy_addon_assets(addon_id)
        size_kb = zpath.stat().st_size // 1024
        print(f'OK    {zpath.relative_to(ROOT)}  ({size_kb} KB)')

    write_addons_xml(versions)
    print(f'OK    {(REPO_DIR / "addons.xml").relative_to(ROOT)}')
    print(f'OK    {(REPO_DIR / "addons.xml.md5").relative_to(ROOT)}')

    print()
    print('Repository ready. Commit & push to GitHub:')
    print('  git add repo/ plugin.video.scrudio/ repository.scrudio/')
    print('  git commit -m "build vX.Y.Z"')
    print('  git push')
    return 0


if __name__ == '__main__':
    sys.exit(main())
