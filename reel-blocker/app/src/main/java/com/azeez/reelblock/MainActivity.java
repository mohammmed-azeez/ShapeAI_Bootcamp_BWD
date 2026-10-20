package com.azeez.reelblock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Setup screen: shows status and walks through the setup steps. */
public class MainActivity extends Activity {

    private TextView status;
    private Button lockButton;
    private Button unlockButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        layout.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, pad, 0, pad);
        layout.addView(status);

        addButton(layout, "1. Turn on ReelBlock (Accessibility)", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        addButton(layout, "2. Stop battery saver from killing it", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        addButton(layout, "3. Block uninstall (Device admin)", v -> {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent());
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_description));
            startActivity(intent);
        });

        lockButton = addButton(layout, "Lock now (skip the 10 min setup time)", v ->
                new AlertDialog.Builder(this)
                        .setTitle("Lock ReelBlock now?")
                        .setMessage("Settings pages that could turn off or uninstall ReelBlock "
                                + "will be blocked. To undo, you must request an unlock here "
                                + "and wait 24 hours.\n\nFinish steps 1 to 3 first.")
                        .setPositiveButton("Lock", (d, w) -> {
                            Lock.lock(this);
                            refresh();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        unlockButton = addButton(layout, "Request unlock (starts 24h wait)", v -> {
            Lock.requestUnlock(this);
            refresh();
        });

        addButton(layout, "Send debug report (if Reels/Shorts are not blocked)", v -> {
            String report = statusReport() + "\n--- log ---\n" + DebugLog.read(this);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "ReelBlock debug report");
            send.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(send, "Send debug report"));
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SelfHeal.ensureRunning(this);
        SelfHeal.schedule(this);
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule: up to ").append(Limiter.MAX_PER_WINDOW)
                .append(" Shorts/Reels per minute. One more blocks them for 15 minutes.\n\n");
        sb.append(serviceLine()).append('\n');
        sb.append(isAdminActive() ? "✅ Uninstall is blocked\n" : "❌ Uninstall is not blocked. Do step 3.\n");

        long blocked = new Limiter(this).blockedRemainingMs();
        if (blocked > 0) {
            sb.append("⛔ Shorts and Reels blocked for ").append((blocked + 59_999) / 60_000)
                    .append(" more min\n");
        }

        boolean locked = Lock.isLocked(this);
        boolean protecting = Lock.isProtecting(this);
        long setup = Lock.setupRemainingMs(this);
        if (!locked) {
            sb.append(isServiceEnabled() ? "🔓 Unlocked. You can turn it off in Settings."
                    : "🔓 Locks by itself 10 min after step 1.");
        } else if (setup > 0) {
            sb.append("⏳ Locks by itself in ").append((setup + 59_999) / 60_000)
                    .append(" min. Finish steps 2 and 3 before that.");
        } else if (Lock.requestedAt(this) == 0) {
            sb.append("🔒 Locked.");
        } else if (protecting) {
            long mins = Lock.remainingMs(this) / 60000;
            sb.append("⏳ Unlock requested. Wait ").append(mins / 60).append("h ")
                    .append(mins % 60).append("m more.");
        } else {
            sb.append("🔓 Unlocked. You can turn it off in Settings now. Press Lock to protect again.");
        }
        status.setText(sb);

        lockButton.setEnabled(!protecting);
        unlockButton.setEnabled(protecting && Lock.requestedAt(this) == 0);
    }

    /** Settings can say "On" while realme has stopped the service, so check that it really runs. */
    private String serviceLine() {
        if (!isServiceEnabled()) {
            return "❌ Not running. Do step 1 (Accessibility is off).";
        }
        if (Status.startedAt(this) == 0) {
            return "❌ Accessibility is on, but the service never started.\n"
                    + "   Fix: turn ReelBlock OFF and ON again in Accessibility. Then restart the phone.";
        }
        long idleMin = (System.currentTimeMillis() - Status.lastHeartbeat(this)) / 60000;
        if (idleMin >= 10) {
            return "⚠️ Accessibility is on, but nothing seen for " + idleMin + " min.\n"
                    + "   If you used the phone in that time, realme stopped it: turn ReelBlock OFF\n"
                    + "   and ON in Accessibility, and do step 2.";
        }
        return "✅ Watching Shorts and Reels";
    }

    private String statusReport() {
        java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance();
        long started = Status.startedAt(this);
        long beat = Status.lastHeartbeat(this);
        return "ReelBlock " + BuildConfig.VERSION_NAME
                + "\nPhone: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + ", Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\nAccessibility on in Settings: " + isServiceEnabled()
                + "\nService last started: " + (started == 0 ? "never" : f.format(new java.util.Date(started)))
                + "\nService last saw an app: " + (beat == 0 ? "never" : f.format(new java.util.Date(beat)))
                + "\nUninstall blocked: " + isAdminActive()
                + "\nLocked: " + Lock.isProtecting(this)
                + "\nSelf-heal (USB setup done): " + SelfHeal.canHeal(this);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, BlockerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    private boolean isAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(adminComponent());
    }

    private ComponentName adminComponent() {
        return new ComponentName(this, AdminReceiver.class);
    }

    private Button addButton(LinearLayout layout, String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        layout.addView(b, lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
