package com.example.livetranslate;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** A foreground service is required for microphone or MediaProjection capture on modern Android. */
public final class CaptureService extends Service {
    public static final String ACTION_START = "com.example.livetranslate.START";
    public static final String ACTION_STOP = "com.example.livetranslate.STOP";
    public static final String ACTION_STYLE = "com.example.livetranslate.STYLE";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PROJECTION_CODE = "projectionCode";
    public static final String EXTRA_PROJECTION_DATA = "projectionData";
    public static final int MODE_MIC = 1;
    public static final int MODE_PLAYBACK = 2;
    private static final String CHANNEL = "live_translate_active";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SAMPLE_RATE = 16000;
    private static final String TAG = "LiveTranslate";

    public static volatile boolean running = false;
    public static volatile String status = "尚未開始";
    public static volatile String english = "";
    public static volatile String chinese = "";
    public static final CaptionHistory history = new CaptionHistory();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean keepRecording = false;
    private volatile AudioRecord recorder;
    private Thread worker;
    private MediaProjection projection;
    private OverlayCaption overlay;
    private TranslationEngine translator;
    private String lastPartialText = "";
    private long lastTranslationAt = 0L;
    private long lastPartialPostAt = 0L;
    private long previewSerial = 0L;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "即時字幕運作中", NotificationManager.IMPORTANCE_LOW));
        overlay = new OverlayCaption(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STYLE.equals(intent.getAction())) {
            updateOverlay();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            status = "已停止";
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;

        int mode = intent.getIntExtra(EXTRA_MODE, MODE_MIC);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "未取得錄音權限";
            stopSelf();
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_PROJECTION_CODE, 0);
        Intent consent = null;
        if (mode == MODE_PLAYBACK) {
            if (code == 0) { status = "缺少系統聲音擷取授權"; stopSelf(); return START_NOT_STICKY; }
            if (Build.VERSION.SDK_INT >= 33) {
                consent = intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent.class);
            } else {
                consent = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
            }
            if (consent == null) { status = "缺少系統聲音擷取授權"; stopSelf(); return START_NOT_STICKY; }
        }

        try {
            int fgsType = mode == MODE_PLAYBACK
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification(), fgsType);
            else if (mode == MODE_PLAYBACK) startForeground(NOTIFICATION_ID, notification(), fgsType);
            else startForeground(NOTIFICATION_ID, notification());

            if (mode == MODE_PLAYBACK) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(code, consent);
                if (projection == null) throw new IllegalStateException("系統拒絕擷取聲音");
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() {
                        status = "系統停止了音訊擷取授權";
                        stopSelf();
                    }
                }, ui);
            }
            running = true;
            keepRecording = true;
            english = "";
            chinese = "";
            status = "正在載入翻譯模型…";
            if (Settings.canDrawOverlays(this)) overlay.show();
            final int actualMode = mode;
            translator = new TranslationEngine();
            translator.ensureReady((ok, message) -> {
                if (!running || !keepRecording) return;
                if (!ok) {
                    status = message;
                    stopSelf();
                    return;
                }
                status = actualMode == MODE_PLAYBACK
                    ? "正在辨識手機內部聲音（若來源 App 禁止擷取，將沒有字幕）"
                    : "正在聆聽手機麥克風";
                worker = new Thread(() -> captureLoop(actualMode), "Audio-Vosk-Worker");
                worker.start();
            });
        } catch (Exception e) {
            Log.e(TAG, "Unable to start caption capture", e);
            status = "啟動失敗：" + e.getMessage();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(this, 42, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent openPI = PendingIntent.getActivity(this, 43, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Live Translate Captions")
            .setContentText("英文語音 → 繁體中文字幕 · 正在本機處理")
            .setContentIntent(openPI)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "停止字幕", stopPI)
            .build();
    }

    private AudioRecord buildRecorder(int mode) {
        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build();
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("手機不支援 16kHz 聲音輸入");
        AudioRecord.Builder builder = new AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(Math.max(min, SAMPLE_RATE));
        if (mode == MODE_PLAYBACK) {
            if (projection == null) throw new IllegalStateException("手機聲音擷取授權已失效");
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();
            builder.setAudioPlaybackCaptureConfig(config);
        } else {
            builder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        }
        AudioRecord input = builder.build();
        if (input.getState() != AudioRecord.STATE_INITIALIZED) {
            input.release();
            throw new IllegalStateException("無法初始化聲音輸入，請檢查裝置權限及其他錄音 App");
        }
        return input;
    }

    private void captureLoop(int mode) {
        try (Model model = new Model(ModelInstaller.path(this).getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            AudioRecord input = buildRecorder(mode);
            recorder = input;
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("錄音裝置無法啟動");
            short[] pcm = new short[2560]; // 160 ms, continuous streaming, no forced 4-second batches.
            while (keepRecording && !Thread.currentThread().isInterrupted()) {
                int n = input.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (n < 0) throw new IllegalStateException("讀取聲音失敗 (" + n + ")");
                if (n == 0) continue;
                if (recognizer.acceptWaveForm(pcm, n)) {
                    String finalEnglish = parse(recognizer.getResult(), "text");
                    if (!finalEnglish.isEmpty()) ui.post(() -> handleFinal(finalEnglish));
                } else {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastPartialPostAt < 260) continue;
                    lastPartialPostAt = now;
                    String partial = parse(recognizer.getPartialResult(), "partial");
                    if (!partial.isEmpty()) ui.post(() -> handlePartial(partial));
                }
            }
            if (keepRecording) {
                String finalEnglish = parse(recognizer.getFinalResult(), "text");
                if (!finalEnglish.isEmpty()) ui.post(() -> handleFinal(finalEnglish));
            }
        } catch (Exception e) {
            if (keepRecording) {
                Log.e(TAG, "Live captions processing error", e);
                String message = e.getMessage();
                ui.post(() -> {
                    status = "辨識失敗：" + message;
                    stopSelf();
                });
            }
        } finally {
            AudioRecord input = recorder;
            recorder = null;
            if (input != null) {
                try { input.stop(); } catch (Exception ignored) {}
                input.release();
            }
        }
    }

    private static String parse(String json, String key) {
        try { return new JSONObject(json).optString(key, "").trim(); }
        catch (Exception e) { return ""; }
    }

    private void handlePartial(String text) {
        if (!running || !keepRecording || text.equals(lastPartialText)) return;
        lastPartialText = text;
        english = text;
        chinese = "辨識中…";
        updateOverlay();
        long now = SystemClock.elapsedRealtime();
        // Intermediate translations are expensive. Limit to one request every 1100ms.
        if (now - lastTranslationAt < 1100) return;
        lastTranslationAt = now;
        long revision = ++previewSerial;
        if (translator == null) return;
        translator.translate(text, new TranslationEngine.Result() {
            @Override public void success(String zh) {
                if (running && revision == previewSerial && english.equals(text)) {
                    chinese = zh;
                    updateOverlay();
                }
            }
            @Override public void failure(String why) { /* keep English interim visible */ }
        });
    }

    private void handleFinal(String text) {
        if (!running || text.isEmpty()) return;
        ++previewSerial;
        lastPartialText = "";
        english = text;
        chinese = "翻譯中…";
        updateOverlay();
        CaptionHistory.Line line = history.add(text);
        if (translator == null) return;
        translator.translate(text, new TranslationEngine.Result() {
            @Override public void success(String zh) {
                history.complete(line, zh);
                if (running && english.equals(text)) { chinese = zh; updateOverlay(); }
            }
            @Override public void failure(String why) {
                history.complete(line, "[翻譯失敗] " + why);
                if (running && english.equals(text)) { chinese = "翻譯未完成"; updateOverlay(); }
            }
        });
    }

    private void updateOverlay() { if (overlay != null) overlay.update(english, chinese); }

    @Override public void onDestroy() {
        keepRecording = false;
        running = false;
        ++previewSerial;
        AudioRecord input = recorder;
        if (input != null) try { input.stop(); } catch (Exception ignored) {}
        if (worker != null) worker.interrupt();
        if (overlay != null) overlay.close();
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }
        if (translator != null) { translator.close(); translator = null; }
        if (status.startsWith("正在")) status = "已停止";
        super.onDestroy();
    }
}
