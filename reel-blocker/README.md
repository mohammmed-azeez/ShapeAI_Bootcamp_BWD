# ReelBlock

An Android app that stops doomscrolling on **YouTube Shorts** and **Instagram Reels**.

## The rule

- Up to **3 shorts in any 60 seconds** is allowed.
- Every short counts: each swipe is one, new or repeated.
- The **4th short within 60 seconds** blocks Shorts and Reels for **15 minutes**.
- While blocked, opening Shorts or Reels sends you straight back out.
- Works in the YouTube and Instagram apps, and on `youtube.com/shorts` and `instagram.com/reel` in browsers.
- Normal YouTube videos and the Instagram feed, Stories and DMs are not touched.
- The numbers are fixed in the code, so you cannot loosen them in a weak moment.

## Install on realme P3 Ultra (one time)

1. On your phone, download `ReelBlock.apk` from this repo's **Releases** page (**ReelBlock (latest build)**).
2. Open the file. Allow "Install unknown apps" when asked.
3. Open ReelBlock and do these steps:
   1. **Turn on ReelBlock (Accessibility)**: Settings > Accessibility > Downloaded apps > ReelBlock > On.
      - If the toggle is greyed out ("Restricted setting"), go to Settings > Apps > ReelBlock > ⋮ (top right) > **Allow restricted settings**, then try again.
   2. **Battery**: App info > Battery > **Allow background activity** / **No restrictions**. Turn on **Auto launch** if shown.
   3. **Block uninstall**: press Activate on the Device admin screen.

After that it runs by itself, even after a restart.
**10 minutes after step 1 it locks by itself.** Then the Settings pages that could turn it off or uninstall it are blocked. Do steps 2 and 3 within those 10 minutes.

## Unlocking

Open ReelBlock > **Request unlock**. After 24 hours you can turn it off in Settings.
While you wait, and while a 15-minute block is running, the Date & time settings are also blocked, so changing the clock does not help.

## Limits (honest)

- Android does not let any app turn on Accessibility or Device admin by itself. You must do steps 1 to 3 once.
- View names inside Instagram/YouTube can change after an app update. If counting stops working, the app needs an update.
- Safe mode, a factory reset, or a PC with ADB can still remove it. No app can stop the owner of a phone completely.
- Each new build is signed with a different key. To install a newer build, unlock, uninstall the old one, then install the new one.
