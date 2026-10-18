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

## If the APK does not install

Made for realme UI 7 / Android 16 (works on Android 8 and newer).

- **"App blocked to protect your device" / "Blocked by Play Protect"**: Google blocks apps from outside the Play Store that use Accessibility (this is on in India). Open Play Store > your photo > **Play Protect** > ⚙️ > turn off **Scan apps with Play Protect**. Install ReelBlock. Turn it back on afterwards.
- **realme "This app may be risky" / install stopped**: Settings > Security & privacy > turn off **Installation safety check** / **Enhanced protection** while installing. Or tap **Install anyway** / **More details > Install**.
- **"App not installed" / "package conflicts"**: an older ReelBlock with a different key is installed. Uninstall it first (Settings > Apps > ReelBlock > Uninstall; if Device admin is on, turn it off first). From version 3.0 on, every build uses the same key, so updates install over the old app.
- **Nothing happens when you tap the file**: open it from the **Files** app instead of the browser, and allow "Install unknown apps" for Files.

## If Reels or Shorts are not blocked

1. Open ReelBlock. The first line must say **✅ Watching Shorts and Reels**. If not, turn it on again (step 1) and check step 2. realme may turn it off to save battery.
2. Watch 1 or 2 Reels, then open ReelBlock and press **Send debug report**. It shows what ReelBlock saw on screen. Share that text so the detection can be fixed for your app version.

## Unlocking

Open ReelBlock > **Request unlock**. After 24 hours you can turn it off in Settings.
While you wait, and while a 15-minute block is running, the Date & time settings are also blocked, so changing the clock does not help.

## Limits (honest)

- Android does not let any app turn on Accessibility or Device admin by itself. You must do steps 1 to 3 once.
- View names inside Instagram/YouTube can change after an app update. If counting stops working, the app needs an update.
- Safe mode, a factory reset, or a PC with ADB can still remove it. No app can stop the owner of a phone completely.
