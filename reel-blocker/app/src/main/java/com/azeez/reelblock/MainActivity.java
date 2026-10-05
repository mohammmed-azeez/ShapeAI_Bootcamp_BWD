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

        lockButton = addButton(layout, "4. LOCK (needs 24h wait to undo)", v ->
                new AlertDialog.Builder(this)
                        .setTitle("Lock ReelBlock?")
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

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append(isServiceEnabled() ? "✅ Blocking is ON\n" : "❌ Blocking is OFF. Do step 1.\n");
        sb.append(isAdminActive() ? "✅ Uninstall is blocked\n" : "❌ Uninstall is not blocked. Do step 3.\n");

        boolean locked = Lock.isLocked(this);
        boolean protecting = Lock.isProtecting(this);
        if (!locked) {
            sb.append("🔓 Not locked. Do step 4.");
        } else if (Lock.requestedAt(this) == 0) {
            sb.append("🔒 Locked.");
        } else if (protecting) {
            long mins = Lock.remainingMs(this) / 60000;
            sb.append("⏳ Unlock requested. Wait ").append(mins / 60).append("h ")
                    .append(mins % 60).append("m more.");
        } else {
            sb.append("🔓 Unlocked. You can now turn it off in Settings. Press LOCK to protect again.");
        }
        status.setText(sb);

        lockButton.setEnabled(!protecting);
        unlockButton.setEnabled(locked && Lock.requestedAt(this) == 0);
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
