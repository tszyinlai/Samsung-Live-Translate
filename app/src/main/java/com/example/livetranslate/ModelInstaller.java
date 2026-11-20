package com.example.livetranslate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads an Apache-2.0 Vosk English model once, over HTTPS with fixed SHA-256 validation. */
public final class ModelInstaller {
    private static final String MODEL_NAME = "vosk-model-small-en-us-0.15";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_NAME + ".zip";
    private static final String SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498";
    private static final String HIGH_NAME = "vosk-model-en-us-0.22-lgraph";
    // Pinned open-source model mirror, with fixed archive SHA-256 from the host.
    private static final String HIGH_URL =
        "https://huggingface.co/rhasspy/vosk-models/resolve/" +
        "e7ac2109d134b5f2404ba95389b2fb51916d4cab/en/" + HIGH_NAME + ".zip";
    private static final String HIGH_SHA256 =
        "d9838b4aaa82a75c4a17f5aca300eaca129aaab2a7cbf951bafbb500eb9c4334";
    private static final long MAX_ARCHIVE_BYTES = 170L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 650L * 1024L * 1024L;
    public interface Listener {
        void update(String message);
        void finished(boolean success, String message);
    }

    private ModelInstaller() {}

    private static File modelPath(Context context, String modelName) {
        return new File(context.getFilesDir(), modelName);
    }

    private static boolean hasStructure(File root) {
        return new File(root, "am").isDirectory()
            && new File(root, "conf").isDirectory()
            && new File(root, "graph").isDirectory();
    }

    public static boolean isEnhancedInstalled(Context context) {
        return hasStructure(modelPath(context, HIGH_NAME));
    }

    public static boolean isEnhancedSelected(Context context) {
        return isEnhancedInstalled(context)
            && context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("enhancedSpeech", false);
    }

    public static void setEnhancedSelected(Context context, boolean enabled) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("enhancedSpeech", enabled && isEnhancedInstalled(context)).apply();
    }

    public static File path(Context context) {
        if (isEnhancedSelected(context)) return modelPath(context, HIGH_NAME);
        File standard = modelPath(context, MODEL_NAME);
        return hasStructure(standard) ? standard : modelPath(context, HIGH_NAME);
    }

    public static boolean isInstalled(Context context) {
        return hasStructure(modelPath(context, MODEL_NAME)) || isEnhancedInstalled(context);
    }

    /** Normal setup reuses any existing model; never downloads the 128 MB one silently. */
    public static void download(Context context, Listener listener) {
        if (isInstalled(context)) {
            new Handler(Looper.getMainLooper()).post(() -> listener.finished(true, "語音模型已安裝"));
        } else {
            downloadModel(context, MODEL_NAME, MODEL_URL, SHA256, false, listener);
        }
    }

    public static void downloadEnhanced(Context context, Listener listener) {
        if (isEnhancedInstalled(context)) {
            setEnhancedSelected(context, true);
            new Handler(Looper.getMainLooper()).post(() -> listener.finished(true, "較準確英文模型已啟用"));
        } else {
            downloadModel(context, HIGH_NAME, HIGH_URL, HIGH_SHA256, true, listener);
        }
    }

    private static void downloadModel(Context context, String modelName, String source,
                                      String hash, boolean enhanced, Listener listener) {
        final Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File zip = new File(appContext.getCacheDir(), modelName + ".zip.download");
            File tempDir = new File(appContext.getFilesDir(), modelName + ".unpacking");
            try {
                File destination = modelPath(appContext, modelName);
                if (hasStructure(destination)) {
                    if (enhanced) setEnhancedSelected(appContext, true);
                    main.post(() -> listener.finished(true, "語音模型已安裝"));
                    return;
                }
                deleteRecursively(tempDir);
                post(main, listener, enhanced ? "正在下載較準確英文模型（約128 MB）…" :
                    "正在下載英文語音模型（約40 MB）…");
                downloadZip(source, zip, percent -> post(main, listener, "下載語音模型：" + percent + "%"));
                post(main, listener, "正在檢查 SHA-256 及解壓縮…");
                if (!hash.equalsIgnoreCase(sha256(zip))) {
                    throw new IOException("模型 SHA-256 驗證失敗，已拒絕安裝");
                }
                if (!tempDir.mkdirs()) throw new IOException("無法建立模型暫存資料夾");
                unpack(zip, tempDir);
                File extracted = new File(tempDir, modelName);
                if (!hasStructure(extracted)) throw new IOException("模型 ZIP 結構不符合預期");
                if (destination.exists() && !hasStructure(destination)) deleteRecursively(destination);
                if (!extracted.renameTo(destination)) throw new IOException("無法完成語音模型安裝");
                if (enhanced) setEnhancedSelected(appContext, true);
                main.post(() -> listener.finished(true,
                    enhanced ? "較準確英文模型已啟用；重新開始字幕即可生效" : "英文語音模型安裝完成"));
            } catch (Exception error) {
                String info = error.getMessage() == null ? error.toString() : error.getMessage();
                main.post(() -> listener.finished(false, "下載失敗：" + info));
            } finally {
                zip.delete();
                deleteRecursively(tempDir);
            }
        }, enhanced ? "Vosk-Enhanced-Downloader" : "Vosk-Model-Downloader").start();
    }

    private interface Progress { void update(int pct); }

    private static void downloadZip(String url, File target, Progress progress) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("下載伺服器回應 HTTP " + code);
            long declaredSize = conn.getContentLengthLong();
            if (declaredSize > MAX_ARCHIVE_BYTES) throw new IOException("檔案大小超出安全限制");
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] b = new byte[16 * 1024];
                long total = 0;
                int n, lastReported = -1;
                while ((n = in.read(b)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > MAX_ARCHIVE_BYTES) throw new IOException("模型檔案過大");
                    out.write(b, 0, n);
                    if (declaredSize > 0) {
                        int pct = (int) Math.min(99, (100 * total) / declaredSize);
                        if (pct >= lastReported + 5) { lastReported = pct; progress.update(pct); }
                    }
                }
            }
        } finally { conn.disconnect(); }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[16 * 1024];
            int n;
            while ((n = in.read(b)) != -1) digest.update(b, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : digest.digest()) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }

    private static void unpack(File zip, File root) throws IOException {
        long total = 0;
        int entries = 0;
        String rootPath = root.getCanonicalPath() + File.separator;
        try (ZipInputStream stream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (++entries > 4000) throw new IOException("ZIP 檔案包含過多項目");
                File dest = new File(root, entry.getName());
                if (!dest.getCanonicalPath().startsWith(rootPath)) throw new IOException("ZIP 路徑不安全");
                if (entry.isDirectory()) {
                    if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("無法建立資料夾");
                } else {
                    File parent = dest.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("無法建立模型子資料夾");
                    try (FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] data = new byte[16 * 1024];
                        int n;
                        while ((n = stream.read(data)) != -1) {
                            total += n;
                            if (total > MAX_EXPANDED_BYTES) throw new IOException("ZIP 解壓超出安全大小限制");
                            out.write(data, 0, n);
                        }
                    }
                }
                stream.closeEntry();
            }
        }
    }

    private static void post(Handler handler, Listener listener, String message) {
        handler.post(() -> listener.update(message));
    }

    private static void deleteRecursively(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        f.delete();
    }
}
