package com.example.livetranslate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Small, dependency-free heuristics for Vosk transcripts, which usually have no punctuation.
 * This is caption layout, not a learned punctuation or translation model.
 */
public final class CaptionText {
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

    /**
     * Compatibility helpers for old callers. The entire hypothesis is
     * intentional: a visual line-break must never truncate translation input.
     */
    public static String visibleTail(String speech) { return tidy(speech); }
    public static int visibleChunkIndex(String speech) { return 0; }

    /**
     * Punctuation-only splitting: never split by word count, line width,
     * a small silence, or a conjunction. Vosk often provides NO punctuation,
     * in which case the whole utterance must remain intact for translation.
     */
    public static List<String> split(String speech) {
        String text = tidy(speech);
        if (text.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder part = new StringBuilder();
        for (String word : words) {
            if (part.length() > 0) part.append(' ');
            part.append(word);
            if (word.endsWith(".") || word.endsWith("?") || word.endsWith("!")) {
                result.add(part.toString());
                part.setLength(0);
            }
        }
        if (part.length() > 0) result.add(part.toString());
        return result;
    }

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

    /** Word timestamps can help VAD, but 0.42 s does NOT mean a full stop. */
    public static List<String> splitTimed(List<TimedWord> words, String fallback) {
        if (words == null || words.isEmpty()) return split(fallback);
        StringBuilder all = new StringBuilder();
        for (TimedWord word : words) {
            if (word == null || word.word.isEmpty()) continue;
            if (all.length() > 0) all.append(' ');
            all.append(word.word);
        }
        return split(all.length() > 0 ? all.toString() : fallback);
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
