# Scrudio

Stremio-like Kodi 21 add-on: TMDB browsing + Torrentio sources + Real-Debrid playback. Designed for Android TV / TCL hardware. Remote-only.

---

## Install (recommended — auto-updating repository)

1. On Kodi → **Settings → System → Add-ons → enable "Unknown sources"** (one-time).
2. Kodi → **Settings → File manager → Add source**
   - Path: `https://raw.githubusercontent.com/jakephobia/scrudio/main/repo/`
   - Name: `scrudio`
3. Kodi → **Add-ons → Install from zip file → scrudio → repository.scrudio → repository.scrudio-1.0.0.zip**
4. Kodi → **Add-ons → Install from repository → Scrudio Repository → Video add-ons → Scrudio → Install**
5. Open **Video → Add-ons → Scrudio**, hit the cogwheel, and enter your **TMDB API key** and **Real-Debrid API key**.

Future updates land automatically through the repository.

## Install (one-shot zip, no auto-updates)

Download `plugin.video.scrudio-1.0.0.zip` directly from
<https://github.com/jakephobia/scrudio/raw/main/repo/plugin.video.scrudio/plugin.video.scrudio-1.0.0.zip>
and use Kodi's **Install from zip file**.

## Requirements

- Kodi 21 Omega (Android TV / Windows / Linux / macOS)
- TMDB API key v3 — free at <https://www.themoviedb.org/settings/api>
- Real-Debrid premium + API token — <https://real-debrid.com/apitoken>

## What's inside

| Path | Purpose |
|---|---|
| `plugin.video.scrudio/` | The Kodi video add-on (Python 3 / Kodi 21) |
| `repository.scrudio/` | Tiny "repository" add-on pointing Kodi at this GitHub tree |
| `repo/` | Published artefacts: `addons.xml`, `addons.xml.md5`, per-addon `.zip` |
| `build.py` | Builds both add-ons and refreshes `repo/` |
| `tests/` | Pure-parser unit tests (no Kodi needed) |
| `scrudio_master_v3.md` | Architecture & decision document |

## Develop / build

```powershell
# Run the parser tests
python tests\test_torrentio_parsers.py

# Build both add-ons + refresh repo/
python build.py

# Commit & push
git add .
git commit -m "release vX.Y.Z"
git push
```

To bump a version, edit `<addon version="...">` in the relevant `addon.xml`, run `python build.py`, commit and push. Kodi will pick up the new version on the next "Check for updates".

## Logs

- Windows: `%APPDATA%\Kodi\kodi.log`
- Android: `/sdcard/Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log`
- Linux: `~/.kodi/temp/kodi.log`

```bash
grep "plugin.video.scrudio" kodi.log
```

## License

MIT — see `plugin.video.scrudio/LICENSE.txt`.
