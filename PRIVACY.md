# Privacy

Altitude collects nothing. It has no account, no analytics, no advertising and no crash reporting,
and it sends nothing to DStudio.

## What leaves the device

Altitude talks to one service, Apple's aerial video server `sylvan.apple.com`, over HTTPS:

- Altitude fetches Apple's list of clips from it, at most once a day, while ATV Launcher uses
  Altitude. The request carries nothing about you: no account, no identifier and none of your
  settings.
- ATV Launcher streams the clips on that list from the same server while they play.
- While Altitude's settings are open, an Apple clip playing behind them is streamed from the same
  server too.

As with any website, Apple sees this device's IP address when it answers. Its handling of that is
covered by [Apple's privacy policy](https://www.apple.com/legal/privacy/).

## What stays on the device

- Your settings.
- A copy of Apple's list of clips, the list Altitude last gave the launcher, and which of its clips
  was last on screen.

None of this is backed up by Android (`allowBackup` is off). Clearing Altitude's storage or
uninstalling it removes all of it. Your settings leave the device only inside a backup or a shared
setup you make in ATV Launcher, which holds them as they are, the path of your My videos folder
included.

## Your videos

Altitude finds the videos of the folder you choose through Android's index of the videos on this
device and its drives. It never uploads, copies or changes them. It serves them read-only, and no
other file, only to a launcher that holds ATV Launcher's wallpaper permission. That permission can
be held only by an app signed with ATV Launcher's key, so other apps on the device cannot read your
videos through Altitude.

## Permissions

- **Internet**, to reach Apple's server.
- **Video access** (storage access on Android 12 and older), to list the videos of the folder you
  choose for My videos and hand them to the launcher. Altitude asks for it only when you choose a
  folder.

## Contact

info@dstudio.ca
