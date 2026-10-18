package com.azeez.reelblock;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Short log of what the blocker saw, so a missed Reel or Short can be fixed. Stays on the phone. */
final class DebugLog {
    private static final int MAX_LINES = 300;
    private static final String FILE = "debug.txt";
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static boolean loaded;

    private DebugLog() {}

    static synchronized void add(Context c, String line) {
        load(c);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        lines.addLast(time + " " + line);
        while (lines.size() > MAX_LINES) lines.pollFirst();
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), FILE))) {
            out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static synchronized String read(Context c) {
        load(c);
        return String.join("\n", lines);
    }

    private static void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            File f = new File(c.getFilesDir(), FILE);
            if (f.exists()) {
                for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (!l.isEmpty()) lines.addLast(l);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
