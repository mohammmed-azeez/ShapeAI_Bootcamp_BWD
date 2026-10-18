package com.azeez.reelblock;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches YouTube Shorts and Instagram Reels. Counts every short you see (each swipe is one).
 * Up to 3 per minute is allowed. The 4th blocks Shorts and Reels for 15 minutes.
 * While locked, it also leaves Settings pages that could disable it.
 */
public class BlockerService extends AccessibilityService {

    private static final String INSTAGRAM = "instagram";
    private static final String YOUTUBE = "youtube";

    /** App package -> which app it is. Includes Lite and modded versions. */
    private static final Map<String, String> APPS = Map.of(
            "com.instagram.android", INSTAGRAM,
            "com.instagram.lite", INSTAGRAM,
            "com.instander.android", INSTAGRAM,
            "com.google.android.youtube", YOUTUBE,
            "app.revanced.android.youtube", YOUTUBE,
            "app.rvx.android.youtube", YOUTUBE,
            "com.vanced.android.youtube", YOUTUBE
    );

    /**
     * Start of view ids that only exist on the swipeable short-video player.
     * Best effort: the apps rename them, so the full-screen pager check below is the backup.
     */
    private static final Map<String, String[]> PLAYER_ID_PREFIXES = Map.of(
            INSTAGRAM, new String[] {
                    "clips_viewer", "clips_ufi", "clips_swipe", "clips_author", "clips_caption",
                    "clips_media", "clips_video"},
            YOUTUBE, new String[] {
                    "reel_player", "reel_watch", "reel_recycler", "reel_time_bar", "reel_progress",
                    "reel_main_title", "reel_channel", "reel_like", "reel_dyn", "reel_pivot"}
    );

    /** Bottom tab labels. If this tab is selected, the user is in the short-video feed. */
    private static final Map<String, String[]> PLAYER_TABS = Map.of(
            INSTAGRAM, new String[] {"Reels"},
            YOUTUBE, new String[] {"Shorts"}
    );

    /** View ids of the bottom tab that opens the short-video feed. */
    private static final Map<String, String> PLAYER_TAB_IDS = Map.of(
            INSTAGRAM, "clips_tab",
            YOUTUBE, "pivot_shorts"
    );

    /** Part of the screen a pager and its page must cover to count as a full-screen player. */
    private static final float FULL_SCREEN = 0.8f;

    private static final Set<String> BROWSERS = Set.of(
            "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
            "com.sec.android.app.sbrowser", "com.heytap.browser", "com.coloros.browser",
            "com.realme.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser",
            "com.vivaldi.browser", "com.UCMobile.intl"
    );

    private static final String[] BROWSER_URLS = {"youtube.com/shorts", "instagram.com/reel"};

    /** Settings apps used to disable or uninstall ReelBlock (stock Android + Realme/ColorOS). */
    private static final Set<String> SETTINGS_APPS = Set.of(
            "com.android.settings", "com.oplus.settings", "com.coloros.settings",
            "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.oplus.securitypermission", "com.coloros.securitypermission",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
    );

    /** Date/time pages: changing the clock could skip the waits. */
    private static final String[] CLOCK_PAGE_TEXTS = {
            "Set time automatically", "Automatic date", "Date & time", "Date and time"
    };

    private static final int MAX_NODES = 5000;
    private static final long CHECK_INTERVAL_MS = 250;
    /** One swipe fires several scroll events. Events closer than this count as one short. */
    private static final long SWIPE_GAP_MS = 900;

    private Limiter limiter;
    private boolean inPlayer;
    private String lastBrowserUrl;
    private long lastCounted;
    private long lastCheck;
    private long lastLeave;
    private long lastToast;
    private long lastHeartbeat;
    private long lastError;
    private final Set<String> seenApps = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        limiter = new Limiter(this);
        Lock.autoLockOnFirstRun(this);
        Status.serviceStarted(this);
        DebugLog.add(this, "service started (v" + BuildConfig.VERSION_NAME + ")");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        DebugLog.add(this, "service stopped by the system");
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Never let one bad screen crash the service: Android would turn it off.
        try {
            handleEvent(event);
        } catch (RuntimeException e) {
            long now = SystemClock.uptimeMillis();
            if (now - lastError > 10_000) {
                lastError = now;
                DebugLog.add(this, "error: " + e);
            }
        }
    }

    private void handleEvent(AccessibilityEvent event) {
        if (limiter == null || event.getPackageName() == null) return;
        String eventPkg = event.getPackageName().toString();
        if (eventPkg.equals(getPackageName())) return;

        long nowMs = SystemClock.uptimeMillis();
        if (nowMs - lastHeartbeat > 10_000) {
            lastHeartbeat = nowMs;
            Status.heartbeat(this);
        }
        if (seenApps.add(eventPkg)) {
            DebugLog.add(this, "saw app: " + eventPkg);
        }

        long now = SystemClock.uptimeMillis();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;

        AccessibilityNodeInfo root = findRoot(eventPkg);
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();

        String app = APPS.get(pkg);
        if (app != null) {
            handleApp(root, app, pkg, event, now);
        } else {
            inPlayer = false;
            if (BROWSERS.contains(pkg)) {
                handleBrowser(root);
            } else if (SETTINGS_APPS.contains(pkg) && Lock.isProtecting(this)
                    && isProtectedSettingsPage(root)) {
                leave("ReelBlock is locked. Request an unlock in the app and wait 24 hours.");
            }
        }
    }

    /** The window of the app that sent the event. Falls back to the active window. */
    private AccessibilityNodeInfo findRoot(String eventPkg) {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null
                && eventPkg.equals(active.getPackageName().toString())) {
            return active;
        }
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null
                        && eventPkg.equals(r.getPackageName().toString())) {
                    return r;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return active;
    }

    private void handleApp(AccessibilityNodeInfo root, String app, String pkg, AccessibilityEvent event,
                           long now) {
        String reason = playerReason(root, app);
        if (reason == null) {
            if (inPlayer || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                logScreen(root, pkg, "not a player");
            }
            inPlayer = false;
            return;
        }
        if (limiter.isBlocked()) {
            DebugLog.add(this, pkg + " blocked (" + reason + ")");
            leave(blockedMessage());
            return;
        }
        // Entering the player is one short. After that, each swipe in the player is one more.
        boolean newShort = !inPlayer
                || (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED && isPlayerScroll(event, root));
        inPlayer = true;
        if (newShort && now - lastCounted >= SWIPE_GAP_MS) {
            lastCounted = now;
            int count = countShort();
            DebugLog.add(this, pkg + " short #" + count + " this minute (" + reason + ")");
        }
    }

    /** In a browser, every new Shorts/Reels address is one short. */
    private void handleBrowser(AccessibilityNodeInfo root) {
        String url = findBrowserUrl(root);
        if (url == null) {
            lastBrowserUrl = null;
            return;
        }
        if (limiter.isBlocked()) {
            leave(blockedMessage());
            return;
        }
        if (!url.equals(lastBrowserUrl)) {
            lastBrowserUrl = url;
            int count = countShort();
            DebugLog.add(this, "browser short #" + count + " this minute");
        }
    }

    private int countShort() {
        int count = limiter.recordView();
        if (count > Limiter.MAX_PER_WINDOW) {
            leave("Doomscrolling detected. Shorts and Reels are blocked for 15 minutes.");
        } else if (count == Limiter.MAX_PER_WINDOW) {
            toast("That's 3 this minute. One more blocks Shorts and Reels for 15 minutes.");
        }
        return count;
    }

    private String blockedMessage() {
        long mins = (limiter.blockedRemainingMs() + 59_999) / 60_000;
        return "Shorts and Reels are blocked for " + mins + " more min.";
    }

    /** Why this screen is a Shorts/Reels player, or null if it is not. */
    private String playerReason(AccessibilityNodeInfo root, String app) {
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        String[] prefixes = PLAYER_ID_PREFIXES.get(app);
        String[] tabs = PLAYER_TABS.get(app);
        String tabId = PLAYER_TAB_IDS.get(app);

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.poll();
            if (!node.isVisibleToUser()) continue;
            String id = node.getViewIdResourceName();
            if (id != null) {
                String shortId = id.substring(id.indexOf('/') + 1);
                for (String prefix : prefixes) {
                    if (shortId.startsWith(prefix)) return "id " + shortId;
                }
                if (node.isSelected() && shortId.equals(tabId)) return "tab id " + shortId;
            }
            if (node.isSelected() && matches(node, tabs)) return "selected tab";
            if (node.isScrollable() && isFullScreenVerticalPager(node, screen)) {
                return "full-screen pager " + node.getClassName();
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    /** A list that fills the screen, shows one full-screen page at a time, and swipes up/down. */
    private static boolean isFullScreenVerticalPager(AccessibilityNodeInfo node, Rect screen) {
        if (screen.width() <= 0 || screen.height() <= 0) return false;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.width() < screen.width() * FULL_SCREEN || r.height() < screen.height() * FULL_SCREEN) {
            return false;
        }
        if (isHorizontal(node)) return false;
        Rect c = new Rect();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            child.getBoundsInScreen(c);
            if (c.width() >= screen.width() * FULL_SCREEN && c.height() >= screen.height() * FULL_SCREEN) {
                return true;
            }
        }
        return false;
    }

    /** True for side-swiping lists such as Instagram Stories, which are not Reels. */
    private static boolean isHorizontal(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList()) {
            int id = a.getId();
            if (id == android.R.id.accessibilityActionScrollLeft
                    || id == android.R.id.accessibilityActionScrollRight
                    || (Build.VERSION.SDK_INT >= 29 && (id == android.R.id.accessibilityActionPageLeft
                    || id == android.R.id.accessibilityActionPageRight))) {
                return true;
            }
        }
        AccessibilityNodeInfo.CollectionInfo info = node.getCollectionInfo();
        return info != null && info.getColumnCount() > 1 && info.getRowCount() <= 1;
    }

    /** True if a scroll is an up/down swipe of a big list, not e.g. a side swipe or a small list. */
    private static boolean isPlayerScroll(AccessibilityEvent event, AccessibilityNodeInfo root) {
        if (Build.VERSION.SDK_INT >= 28 && event.getScrollDeltaX() != 0 && event.getScrollDeltaY() == 0) {
            return false;
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return true;
        Rect screen = new Rect();
        Rect r = new Rect();
        root.getBoundsInScreen(screen);
        source.getBoundsInScreen(r);
        return r.height() >= screen.height() / 2;
    }

    /** Logs what a screen in Instagram/YouTube looked like, to fix missed players later. */
    private void logScreen(AccessibilityNodeInfo root, String pkg, String result) {
        StringBuilder ids = new StringBuilder();
        StringBuilder pagers = new StringBuilder();
        Rect screen = new Rect();
        Rect r = new Rect();
        root.getBoundsInScreen(screen);
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        int idCount = 0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.poll();
            if (!node.isVisibleToUser()) continue;
            String id = node.getViewIdResourceName();
            if (id != null && idCount++ < 40) ids.append(id.substring(id.indexOf('/') + 1)).append(' ');
            if (node.isScrollable()) {
                node.getBoundsInScreen(r);
                pagers.append(node.getClassName()).append(' ').append(r.width()).append('x')
                        .append(r.height()).append(" children=").append(node.getChildCount()).append("; ");
            }
            if (node.isSelected()) {
                CharSequence d = node.getContentDescription() != null ? node.getContentDescription() : node.getText();
                if (d != null) ids.append("[selected:").append(d).append("] ");
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        DebugLog.add(this, pkg + " " + result + " screen=" + screen.width() + "x" + screen.height()
                + " ids: " + ids + " scrollable: " + pagers);
    }

    private static String findBrowserUrl(AccessibilityNodeInfo root) {
        for (String url : BROWSER_URLS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(url);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser() && n.getText() != null) {
                    return n.getText().toString();
                }
            }
        }
        return null;
    }

    private boolean isProtectedSettingsPage(AccessibilityNodeInfo root) {
        if (anyVisible(root.findAccessibilityNodeInfosByText(getString(R.string.app_name)))) {
            return true;
        }
        if (Lock.isCoolingDown(this) || limiter.isBlocked()) {
            for (String text : CLOCK_PAGE_TEXTS) {
                if (anyVisible(root.findAccessibilityNodeInfosByText(text))) return true;
            }
        }
        return false;
    }

    private static boolean matches(AccessibilityNodeInfo node, String[] labels) {
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        for (String label : labels) {
            if (startsWithWord(desc, label) || startsWithWord(text, label)) return true;
        }
        return false;
    }

    /** "Reels" matches "Reels" and "Reels, tab 4 of 5" but not "Reelsy". */
    private static boolean startsWithWord(CharSequence value, String label) {
        if (value == null) return false;
        String s = value.toString().trim();
        if (!s.regionMatches(true, 0, label, 0, label.length())) return false;
        return s.length() == label.length() || !Character.isLetterOrDigit(s.charAt(label.length()));
    }

    private static boolean anyVisible(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            if (n != null && n.isVisibleToUser()) return true;
        }
        return false;
    }

    /** Press Back. If the screen comes back right away, press Home too. */
    private void leave(String message) {
        long now = SystemClock.uptimeMillis();
        performGlobalAction(GLOBAL_ACTION_BACK);
        if (now - lastLeave < 1500) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        lastLeave = now;
        inPlayer = false;
        toast(message);
    }

    private void toast(String message) {
        long now = SystemClock.uptimeMillis();
        if (now - lastToast < 3000) return;
        lastToast = now;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() {}
}
