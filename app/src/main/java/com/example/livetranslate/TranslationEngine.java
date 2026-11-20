package com.example.livetranslate;

import android.icu.text.Transliterator;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Enumeration;

/** ML Kit EN -> ZH translation on the device; Android ICU converts simplified to traditional. */
public final class TranslationEngine {
    public interface Result { void success(String traditional); void failure(String why); }
    public interface Ready { void done(boolean ok, String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Translator translator;
    private final Transliterator s2t;
    private boolean ready = false;

    public TranslationEngine() {
        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.CHINESE).build();
        translator = Translation.getClient(options);
        s2t = findTraditionalTransliterator();
    }

    private static Transliterator findTraditionalTransliterator() {
        String[] preferred = {"Hans-Hant", "Simplified-Traditional"};
        for (String id : preferred) {
            Enumeration<String> ids = Transliterator.getAvailableIDs();
            while (ids.hasMoreElements()) {
                if (id.equals(ids.nextElement())) return Transliterator.getInstance(id);
            }
        }
        throw new IllegalStateException("此手機不提供繁體中文轉換器（ICU Hans-Hant）");
    }

    public void ensureReady(Ready cb) {
        // Default permits mobile data. The user is informed about downloads in the UI.
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(ignored -> {
                ready = true;
                cb.done(true, "翻譯語言模型已準備好");
            })
            .addOnFailureListener(e -> {
                ready = false;
                cb.done(false, "翻譯模型無法下載：" + e.getMessage());
            });
    }

    public void translate(String english, Result cb) {
        if (!ready) {
            main.post(() -> cb.failure("翻譯模型尚未就緒"));
            return;
        }
        translator.translate(english)
            .addOnSuccessListener(simplified -> {
                try { cb.success(s2t.transliterate(simplified)); }
                catch (Exception e) { cb.failure("繁體中文轉換失敗：" + e.getMessage()); }
            })
            .addOnFailureListener(e -> cb.failure("翻譯失敗：" + e.getMessage()));
    }

    public void close() { translator.close(); }
}
