package com.example.livetranslate;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** In-memory only. Never stores or uploads microphone/system sound. */
public final class CaptionHistory {
    private static final int MAX_LINES = 150;
    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    public static final class Line {
        public final String time;
        public final String english;
        public String chinese;
        private Line(String time, String english) {
            this.time = time;
            this.english = english;
            this.chinese = "翻譯中…";
        }
    }

    public synchronized Line add(String english) {
        if (english == null || english.trim().isEmpty()) return null;
        Line line = new Line(new SimpleDateFormat("HH:mm:ss", Locale.UK).format(new Date()), english.trim());
        lines.addLast(line);
        while (lines.size() > MAX_LINES) lines.removeFirst();
        return line;
    }

    public synchronized void complete(Line line, String chinese) {
        if (line != null) line.chinese = chinese == null ? "" : chinese.trim();
    }

    public synchronized void clear() { lines.clear(); }

    public synchronized String asText() {
        StringBuilder out = new StringBuilder();
        for (Line line : lines) {
            out.append('[').append(line.time).append("] ").append(line.english).append('\n');
            out.append(line.chinese).append("\n\n");
        }
        return out.toString();
    }
}
