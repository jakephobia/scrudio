# Scrudio

Stremio-like Kodi 21 add-on: TMDB browsing + Torrentio sources + Real-Debrid playback.

## Requirements

- **Kodi 21 Omega** (Android TV / Windows / Linux / macOS)
- **TMDB API key (v3)** — free at <https://www.themoviedb.org/settings/api>
- **Real-Debrid premium + API token** — <https://real-debrid.com/apitoken>

## Install

1. Download the latest `plugin.video.scrudio-X.Y.Z.zip` from the project's `dist/` folder
   (or build it yourself with `python build.py` from the project root).
2. In Kodi → Settings → System → Add-ons → enable **Unknown sources** (first time only).
3. Kodi → Add-ons → "Install from zip file" → select the downloaded zip.
4. Open **Video → Add-ons → Scrudio** → cogwheel → enter your TMDB and Real-Debrid keys.

## Usage

- D-pad-only navigation. Back to go up, OK to enter or play.
- Home menu → catalogue → details → sources → play.
- Phase 1 (current): only the home menu and settings work. The rest will land in subsequent phases — see `scrudio_master_v3.md`.

## Logs

Kodi log path:

- Windows: `%APPDATA%\Kodi\kodi.log`
- Android: `/sdcard/Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log`
- Linux: `~/.kodi/temp/kodi.log`

Filter for Scrudio:

```bash
grep "plugin.video.scrudio" kodi.log
```

## License

MIT — see `LICENSE.txt`.
