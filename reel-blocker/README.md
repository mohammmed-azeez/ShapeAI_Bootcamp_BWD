# ReelBlock

An Android app that stops you from watching short videos.

- **Instagram Reels** and **YouTube Shorts**: the app presses Back the moment they open.
- **Facebook Reels** and **Snapchat Spotlight**: best effort.
- **TikTok, Moj, Josh, Chingari, MX TakaTak, Roposo**: the app sends you to the Home screen right away.
- **Browsers**: `youtube.com/shorts`, `instagram.com/reel` and `tiktok.com` pages get closed.
- Normal Instagram (feed, stories, DMs) and normal YouTube videos still work.

## Install on realme P3 Ultra

1. Open this repo's **Releases** page on your phone. Download `ReelBlock.apk` from **ReelBlock (latest build)**.
2. Open the file. Allow "Install unknown apps" for your browser or file manager when asked.
3. Open ReelBlock and do the steps in order:
   1. **Turn on ReelBlock (Accessibility)**: Settings > Accessibility > Downloaded apps > ReelBlock > On.
      - If the toggle is greyed out ("Restricted setting"), go to Settings > Apps > ReelBlock > ⋮ (top right) > **Allow restricted settings**, then try again.
   2. **Stop battery saver from killing it**: App info > Battery > **Allow background activity** / **No restrictions**. Also turn on **Auto launch** if your phone shows it.
   3. **Block uninstall**: press Activate on the Device admin screen.
   4. **LOCK**: press it last. After this, the Settings pages that could turn ReelBlock off are blocked.

## Unlocking

Open ReelBlock > **Request unlock**. After 24 hours you can turn it off in Settings.
While the 24 hours run, the Date & time settings are also blocked, so changing the clock does not help.

## Limits (honest)

- View names inside Instagram/YouTube can change after an app update. If Reels or Shorts start working again, the app needs an update.
- Safe mode, a factory reset, or a PC with ADB can still remove it. No app can stop the owner of a phone completely.
- Each new build is signed with a different key. To install a newer build, unlock, uninstall the old one, then install the new one.
