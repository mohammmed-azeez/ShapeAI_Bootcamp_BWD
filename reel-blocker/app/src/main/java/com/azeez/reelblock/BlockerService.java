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
 * Watches the screen. When a short-video feed is showing, it presses Back (and Home if
 * Back is not enough). While locked, it also leaves Settings pages that could disable it.
 */
public class BlockerService extends AccessibilityService {

    /** Apps that are only short videos. Opening them goes straight to Home. */
    private static final Set<String> FULLY_BLOCKED_APPS = Set.of(
            "com.zhiliaoapp.musically",        // TikTok
            "com.ss.android.ugc.trill",         // TikTok (Asia)
            "in.mohalla.video",                // Moj
            "com.eterno.shortvideos",          // Josh
            "io.chingari.app",                 // Chingari
            "com.next.innovation.takatak",     // MX TakaTak
            "com.roposo.android",              // Roposo
            "com.instagram.barcelona.reels",   // possible standalone reels app
            "com.google.android.youtube.shorts"
    );

    /** View ids that only exist on the short-video player screen. Best effort: apps rename them. */
    private static final Map<String, String[]> SHORTS_VIEW_IDS = Map.of(
            "com.instagram.android", new String[] {
                    "clips_viewer_view_pager", "clips_viewer_container", "clips_swipe_refresh_container"},
            "com.google.android.youtube", new String[] {
                    "reel_recycler", "reel_player_page_container", "reel_watch_player",
                    "reel_watch_fragment_root"},
            "com.facebook.katana", new String[] {
                    "reels_viewer", "reel_viewer_root"}
    );

    /** Bottom tab labels. If this tab is selected, the user is in the short-video feed. */
    private static final Map<String, String[]> SHORTS_TABS = Map.of(
            "com.instagram.android", new String[] {"Reels"},
            "com.google.android.youtube", new String[] {"Shorts"},
            "com.facebook.katana", new String[] {"Reels", "Video"},
            "com.snapchat.android", new String[] {"Spotlight"}
    );

    private static final Set<String> BROWSERS = Set.of(
            "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
            "com.sec.android.app.sbrowser", "com.heytap.browser", "com.coloros.browser",
            "com.realme.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser",
            "com.vivaldi.browser", "com.UCMobile.intl"
    );

    private static final String[] BLOCKED_URLS = {
            "youtube.com/shorts", "instagram.com/reel", "tiktok.com", "facebook.com/reel"
    };

    /** Settings apps used to disable or uninstall ReelBlock (stock Android + Realme/ColorOS). */
    private static final Set<String> SETTINGS_APPS = Set.of(
            "com.android.settings", "com.oplus.settings", "com.coloros.settings",
            "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.oplus.securitypermission", "com.coloros.securitypermission",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
    );

    /** Date/time pages: changing the clock could skip the 24 hour unlock wait. */
    private static final String[] CLOCK_PAGE_TEXTS = {
            "Set time automatically", "Automatic date", "Date & time", "Date and time"
    };

    private static final int MAX_NODES = 4000;
    private static final long CHECK_INTERVAL_MS = 250;

    private long lastCheck;
    private long lastBlock;
    private long lastToast;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String eventPkg = event.getPackageName().toString();
        if (eventPkg.equals(getPackageName())) return;

        if (FULLY_BLOCKED_APPS.contains(eventPkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            toast("Short videos are blocked.");
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();

        if (FULLY_BLOCKED_APPS.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            toast("Short videos are blocked.");
        } else if (isShortVideoScreen(root, pkg)) {
            leave("Reels and Shorts are blocked.");
        } else if (SETTINGS_APPS.contains(pkg) && Lock.isProtecting(this)
                && isProtectedSettingsPage(root)) {
            leave("ReelBlock is locked. Request an unlock in the app and wait 24 hours.");
        }
    }

    private boolean isShortVideoScreen(AccessibilityNodeInfo root, String pkg) {
        String[] ids = SHORTS_VIEW_IDS.get(pkg);
        if (ids != null) {
            for (String id : ids) {
                if (anyVisible(root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id))) {
                    return true;
                }
            }
        }
        String[] tabs = SHORTS_TABS.get(pkg);
        if (tabs != null && hasSelectedTab(root, tabs)) {
            return true;
        }
        if (BROWSERS.contains(pkg)) {
            for (String url : BLOCKED_URLS) {
                if (anyVisible(root.findAccessibilityNodeInfosByText(url))) return true;
            }
        }
        return false;
    }

    private boolean isProtectedSettingsPage(AccessibilityNodeInfo root) {
        if (anyVisible(root.findAccessibilityNodeInfosByText(getString(R.string.app_name)))) {
            return true;
        }
        if (Lock.isCoolingDown(this)) {
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
        if (now - lastBlock < 1500) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        lastBlock = now;
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
