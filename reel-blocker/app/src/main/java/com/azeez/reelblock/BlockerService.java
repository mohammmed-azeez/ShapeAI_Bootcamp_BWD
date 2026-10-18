package com.azeez.reelblock;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches YouTube Shorts and Instagram Reels. Counts every short you see (each swipe is one).
 * Up to 3 per minute is allowed. The 4th blocks Shorts and Reels for 15 minutes.
 * While locked, it also leaves Settings pages that could disable it.
 */
public class BlockerService extends AccessibilityService {

    private static final String INSTAGRAM = "com.instagram.android";
    private static final String YOUTUBE = "com.google.android.youtube";

    /** View ids of the swipeable short-video player. Best effort: apps rename them. */
    private static final Map<String, String[]> PLAYER_VIEW_IDS = Map.of(
            INSTAGRAM, new String[] {
                    "clips_viewer_view_pager", "clips_viewer_container", "clips_swipe_refresh_container"},
            YOUTUBE, new String[] {
                    "reel_recycler", "reel_player_page_container", "reel_watch_player",
                    "reel_watch_fragment_root"}
    );

    /** Bottom tab labels. If this tab is selected, the user is in the short-video feed. */
    private static final Map<String, String[]> PLAYER_TABS = Map.of(
            INSTAGRAM, new String[] {"Reels"},
            YOUTUBE, new String[] {"Shorts"}
    );

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

    private static final int MAX_NODES = 4000;
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        limiter = new Limiter(this);
        Lock.autoLockOnFirstRun(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (limiter == null || event.getPackageName() == null) return;
        if (event.getPackageName().toString().equals(getPackageName())) return;

        long now = SystemClock.uptimeMillis();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();

        if (PLAYER_VIEW_IDS.containsKey(pkg)) {
            handleApp(root, pkg, event, now);
        } else if (BROWSERS.contains(pkg)) {
            handleBrowser(root);
        } else if (SETTINGS_APPS.contains(pkg) && Lock.isProtecting(this)
                && isProtectedSettingsPage(root)) {
            leave("ReelBlock is locked. Request an unlock in the app and wait 24 hours.");
        }
    }

    private void handleApp(AccessibilityNodeInfo root, String pkg, AccessibilityEvent event, long now) {
        if (!isPlayerScreen(root, pkg)) {
            inPlayer = false;
            return;
        }
        if (limiter.isBlocked()) {
            leave(blockedMessage());
            return;
        }
        // Entering the player is one short. After that, each swipe in the player is one more.
        boolean newShort = !inPlayer
                || (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED && isPlayerScroll(event, pkg));
        inPlayer = true;
        if (newShort && now - lastCounted >= SWIPE_GAP_MS) {
            lastCounted = now;
            countShort();
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
            countShort();
        }
    }

    private void countShort() {
        int count = limiter.recordView();
        if (count > Limiter.MAX_PER_WINDOW) {
            leave("Doomscrolling detected. Shorts and Reels are blocked for 15 minutes.");
        } else if (count == Limiter.MAX_PER_WINDOW) {
            toast("That's 3 this minute. One more blocks Shorts and Reels for 15 minutes.");
        }
    }

    private String blockedMessage() {
        long mins = (limiter.blockedRemainingMs() + 59_999) / 60_000;
        return "Shorts and Reels are blocked for " + mins + " more min.";
    }

    private boolean isPlayerScreen(AccessibilityNodeInfo root, String pkg) {
        for (String id : PLAYER_VIEW_IDS.get(pkg)) {
            if (anyVisible(root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id))) return true;
        }
        return hasSelectedTab(root, PLAYER_TABS.get(pkg));
    }

    /** True if a scroll came from the video pager, not from e.g. the comments list. */
    private static boolean isPlayerScroll(AccessibilityEvent event, String pkg) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || source.getViewIdResourceName() == null) return true;
        String id = source.getViewIdResourceName();
        for (String playerId : PLAYER_VIEW_IDS.get(pkg)) {
            if (id.equals(pkg + ":id/" + playerId)) return true;
        }
        return id.contains("clips") || id.contains("reel");
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

    /** Breadth-first search for a selected node whose label matches one of the tab names. */
    private static boolean hasSelectedTab(AccessibilityNodeInfo root, String[] labels) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.poll();
            if (node.isSelected() && node.isVisibleToUser() && matches(node, labels)) {
                return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
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
