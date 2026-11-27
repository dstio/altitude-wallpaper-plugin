# Altitude

**Made for [ATV Launcher](https://atvlauncher.com)** ([Google Play](https://play.google.com/store/apps/details?id=ca.dstudio.atvlauncher.pro)).
Altitude is a wallpaper plugin: it shows nothing on its own, and its videos play through ATV
Launcher's Wallpaper settings.

It turns the home screen into a slow aerial video. Altitude hands the launcher a list of clips and
the launcher plays them one after another, in a new order each time the list is made, with a new
list every six hours. The clips come from two sources:

- **Apple:** Apple's aerial videos of landscapes, cities, underwater scenes and space, streamed
  from Apple's servers, in 4K where this device can play it. Altitude is not made by or affiliated
  with Apple.
- **My videos:** one folder of your own videos on this device or a USB drive, its subfolders
  included. They are shuffled in with Apple's clips, or play alone with Apple turned off.

Altitude talks to the launcher through the
[ATV Launcher Wallpaper API](https://github.com/dstio/wallpaper-api).

## Settings

The settings open from the launcher's wallpaper page, over the clip that is playing, and the next
list follows them once they close.

- **Sources:** Apple on or off, and the folder for My videos, or None. One source always stays on.
- **Scenes:** Landscapes, Cities, Underwater and Space. They choose among Apple's clips and leave
  your own videos alone. One always stays ticked.
- **Quality:** Auto plays 4K where this device can decode it and its screen is 4K, else 1080p, and
  says which it picks. 1080p uses less data.

The settings travel in ATV Launcher's backups and shared setups.

## Installing

1. Install ATV Launcher from [Google Play](https://play.google.com/store/apps/details?id=ca.dstudio.atvlauncher.pro).
2. Download the APK and install it. The newest build is always at
   <https://github.com/dstio/altitude-wallpaper-plugin/releases/download/latest/altitude-atvl-plugin.apk>;
   each versioned release under [Releases](https://github.com/dstio/altitude-wallpaper-plugin/releases)
   has its own `altitude-atvl-plugin-<version>.apk`. Every release lists the APK's SHA-256, so you
   can check the download.
3. In ATV Launcher, open Settings > Wallpaper, choose Altitude under Plugins and turn it on. Apple's
   clips play with no setup.

Apple's clips need Android 7 or newer.

## Permissions

- **Internet**, to fetch Apple's list of clips and to play the clip behind the settings. The
  launcher streams the clips from Apple itself.
- **Video access**, for My videos only. Altitude asks for it when you choose a folder, and not
  before: Apple's clips need no permission. It reads video files and nothing else, and never asks
  for access to all files.

What Altitude sends where is in [PRIVACY.md](PRIVACY.md).

## Building

You need JDK 17 or newer and the Android SDK (platform 36).

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Altitude ships with a list of Apple's clips, so it can play before its first visit to Apple's feed.
`tools/build-apple-snapshot.py` (Python 3, standard library only) rebuilds that list,
`app/src/main/assets/apple-snapshot.json`, from Apple's feed:

```
python tools/build-apple-snapshot.py
```

## Credits

Aerial videos by Apple, streamed from Apple's servers. Altitude is not made by, endorsed by or
affiliated with Apple.

## Licence

Apache License 2.0 - see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Copyright 2026 DStudio.
