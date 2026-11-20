package com.example.livetranslate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Small, dependency-free heuristics for Vosk transcripts, which usually have no punctuation.
 * This is caption layout, not a learned punctuation or translation model.
 */
public final class CaptionText {
    private static final int MAX_WORDS = 11;
    private static final int MAX_CHARS = 76;
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern QUESTION_START = Pattern.compile(
        "^(what|why|where|when|who|whom|whose|which|how|can|could|would|will|should|" +
        "do|does|did|is|are|was|were|have|has|had|am|may|might)\\b.*", Pattern.CASE_INSENSITIVE);

    private CaptionText() {}

    public static String tidy(String speech) {
        if (speech == null) return "";
        return SPACES.matcher(speech.trim()).replaceAll(" ");
    }

    public static int wordCount(String speech) {
        String clean = tidy(speech);
        return clean.isEmpty() ? 0 : clean.split(" ").length;
    }

    /** Keep the real-time overlay on the most recent, reasonably short clause. */
    public static String visibleTail(String speech) {
        List<String> pieces = split(speech);
        return pieces.isEmpty() ? "" : pieces.get(pieces.size() - 1);
    }

    public static int visibleChunkIndex(String speech) {
        return Math.max(0, split(speech).size() - 1);
    }

    /** Preserve every recognized word; split at punctuation or safe length limits. */
    public static List<String> split(String speech) {
        String text = tidy(speech);
        if (text.isEmpty()) return Collections.emptyList();
        String[] words = text.split(" ");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String word : words) {
            boolean nextClause = isClauseStarter(word);
            if (count >= 6 && nextClause) {
                result.add(current.toString());
                current.setLength(0);
                count = 0;
            }
            if (count >= MAX_WORDS || (count >= 6 && current.length() + 1 + word.length() > MAX_CHARS)) {
                result.add(current.toString());
                current.setLength(0);
                count = 0;
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
            count++;
            if (endsSentence(word)) {
                result.add(current.toString());
                current.setLength(0);
                count = 0;
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static boolean isClauseStarter(String word) {
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return "but".equals(w) || "however".equals(w) || "because".equals(w)
            || "therefore".equals(w) || "then".equals(w) || "although".equals(w);
    }

    private static boolean endsSentence(String word) {
        return word.endsWith(".") || word.endsWith("?") || word.endsWith("!");
    }

    /** Vosk word timestamps make pauses visible even inside one final recognition result. */
    public static final class TimedWord {
        public final String word;
        public final double start;
        public final double end;
        public TimedWord(String word, double start, double end) {
            this.word = tidy(word);
            this.start = start;
            this.end = end;
        }
    }

    public static List<String> splitTimed(List<TimedWord> words, String fallback) {
        if (words == null || words.isEmpty()) return split(fallback);
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        double previousEnd = -1.0;
        for (TimedWord w : words) {
            if (w == null || w.word.isEmpty()) continue;
            // Gaps near half a second are often sentence or clause boundaries.
            boolean pause = previousEnd >= 0 && w.start - previousEnd >= 0.42;
            if (pause && count >= 3) {
                pieces.addAll(split(current.toString()));
                current.setLength(0);
                count = 0;
            }
            if (current.length() > 0) current.append(' ');
            current.append(w.word);
            count++;
            previousEnd = w.end;
        }
        if (current.length() > 0) pieces.addAll(split(current.toString()));
        return pieces.isEmpty() ? split(fallback) : pieces;
    }

    /** Capitalisation and punctuation give the small on-device translation model context. */
    public static String forTranslation(String speech, boolean finalClause) {
        String text = tidy(speech);
        if (text.isEmpty()) return "";
        // Only restore obvious proper acronyms; never auto-rewrite ambiguous words.
        text = text.replaceAll("(?i)\\bhmrc\\b", "HMRC")
            .replaceAll("(?i)\\bpaye\\b", "PAYE")
            .replaceAll("(?i)\\bvat\\b", "VAT")
            .replaceAll("(?i)\\buk\\b", "UK")
            .replaceAll("\\bi\\b", "I");
        text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        if (finalClause && !text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
            text += QUESTION_START.matcher(text).matches() ? "?" : ".";
        }
        return text;
    }
}
