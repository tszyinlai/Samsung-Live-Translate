package com.example.livetranslate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Simple, native Android UI, built without ads, accounts or paid services. */
public final class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 801;
    private static final int REQUEST_PROJECTION = 802;
    private static final int REQUEST_NOTIFICATIONS = 803;
    private static final int BG = 0xFF0E1220;
    private static final int PANEL = 0xFF182236;
    private static final int MUTED = 0xFF99A9BE;
    private static final int ACCENT = 0xFF56D3CC;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private TextView englishText;
    private TextView chineseText;
    private TextView historyText;
    private Button modelButton;
    private Button micButton;
    private Button playbackButton;
    private Button stopButton;
    private int desiredMode = CaptureService.MODE_PLAYBACK;
    private boolean downloading = false;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshCaptionStatus();
            ui.postDelayed(this, 450);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override public void onResume() {
        super.onResume();
        ui.removeCallbacks(refresh);
        ui.post(refresh);
    }
    @Override public void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(34));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(label("LIVE TRANSLATE", 11, ACCENT, true));
        root.addView(label("即時翻譯字幕", 30, Color.WHITE, true));
        TextView subtitle = label("Samsung Android · 英文語音 → 繁體中文字幕", 14, MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(20));
        root.addView(subtitle);

        LinearLayout card = panel();
        card.addView(label("目前狀態", 12, ACCENT, true));
        statusText = label("尚未開始", 15, Color.WHITE, false);
        statusText.setPadding(0, dp(5), 0, dp(12));
        card.addView(statusText);
        card.addView(label("ENGLISH", 11, MUTED, true));
        englishText = label("Waiting for speech…", 16, 0xFFD3E4F4, false);
        englishText.setPadding(0, dp(4), 0, dp(12));
        card.addView(englishText);
        card.addView(label("繁體中文", 11, ACCENT, true));
        chineseText = label("開始後，這裏會顯示翻譯字幕。", 21, Color.WHITE, true);
        chineseText.setPadding(0, dp(4), 0, dp(4));
        card.addView(chineseText);
        root.addView(card);

        textGap(root, 18);
        root.addView(label("1  首次下載免費模型", 17, Color.WHITE, true));
        root.addView(label("建議使用 Wi-Fi：英文語音模型約 40 MB，中文翻譯模型亦需下載。完成後可離線使用，不需 API Key。", 13, MUTED, false));
        modelButton = button("下載／檢查免費模型", false);
        root.addView(modelButton);
        modelButton.setOnClickListener(v -> installModels());

        textGap(root, 16);
        root.addView(label("2  選擇聲音來源", 17, Color.WHITE, true));
        root.addView(label("內部聲音模式需要 Android 的一次性擷取授權，來源 App 也必須允許聲音被擷取。", 13, MUTED, false));
        playbackButton = button("▶ 手機內部聲音（影片／播放器）", true);
        micButton = button("🎙 麥克風（外界講話）", false);
        stopButton = button("■ 停止翻譯", false);
        root.addView(playbackButton);
        root.addView(micButton);
        root.addView(stopButton);
        playbackButton.setOnClickListener(v -> start(CaptureService.MODE_PLAYBACK));
        micButton.setOnClickListener(v -> start(CaptureService.MODE_MIC));
        stopButton.setOnClickListener(v -> stop());

        textGap(root, 16);
        root.addView(label("3  浮動字幕設定", 17, Color.WHITE, true));
        Button overlayButton = button("允許顯示在其他 App 上層", false);
        root.addView(overlayButton);
        overlayButton.setOnClickListener(v -> openOverlaySettings());
        CheckBox bilingual = new CheckBox(this);
        bilingual.setText("同時顯示英文及中文字幕");
        bilingual.setTextColor(Color.WHITE);
        bilingual.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bilingual.setChecked(prefs().getBoolean("bilingual", true));
        root.addView(bilingual);
        bilingual.setOnCheckedChangeListener((ignored, checked) -> {
            prefs().edit().putBoolean("bilingual", checked).apply();
            refreshOverlayStyle();
        });

        LinearLayout textSize = new LinearLayout(this);
        textSize.setOrientation(LinearLayout.HORIZONTAL);
        textSize.setGravity(Gravity.CENTER_VERTICAL);
        textSize.addView(label("字幕字體大小  ", 14, MUTED, false));
        Button smaller = button("A−", false), larger = button("A+", false);
        LinearLayout.LayoutParams smallP = new LinearLayout.LayoutParams(0, dp(48), 1f);
        smallP.setMargins(dp(4), 0, dp(4), 0);
        textSize.addView(smaller, smallP);
        textSize.addView(larger, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(textSize);
        smaller.setOnClickListener(v -> changeSize(-2));
        larger.setOnClickListener(v -> changeSize(2));

        textGap(root, 14);
        root.addView(label("字幕紀錄（只保存在記憶體）", 17, Color.WHITE, true));
        historyText = label("開始使用後，已確認的句子會暫時顯示在這裏。", 13, MUTED, false);
        historyText.setPadding(dp(12), dp(12), dp(12), dp(12));
        historyText.setBackground(rounded(PANEL, 12));
        root.addView(historyText);
        Button share = button("分享／儲存字幕文字", false);
        root.addView(share);
        share.setOnClickListener(v -> shareTranscript());
        Button clear = button("清除字幕紀錄", false);
        root.addView(clear);
        clear.setOnClickListener(v -> {
            CaptureService.history.clear();
            toast("字幕紀錄已清除");
        });

        textGap(root, 22);
        root.addView(label("無訂閱費、無付費 API。音訊在手機本機辨識；不會上傳音訊。首次下載模型需要網絡。", 12, MUTED, false));
        textGap(root, 8);
        root.addView(label("Translations powered by Google. · Speech recognition by Vosk.", 11, MUTED, false));
        root.addView(label("注意：通話、受保護影片及禁止擷取的 App 可能無法在內部聲音模式顯示字幕。", 12, 0xFFFFC97E, false));
    }

    private void installModels() {
        if (downloading || CaptureService.running) return;
        downloading = true;
        modelButton.setEnabled(false);
        statusText.setText("正在準備語音模型…");
        ModelInstaller.download(this, new ModelInstaller.Listener() {
            @Override public void update(String info) { statusText.setText(info); }
            @Override public void finished(boolean success, String info) {
                if (!success) { finishInstall(false, info); return; }
                statusText.setText("正在下載／檢查免費中文翻譯模型…");
                try {
                    TranslationEngine t = new TranslationEngine();
                    t.ensureReady((ok, msg) -> {
                        t.close();
                        finishInstall(ok, ok ? "免費語音及繁體中文模型已準備完成" : msg);
                    });
                } catch (Exception e) { finishInstall(false, "繁體轉換檢查失敗：" + e.getMessage()); }
            }
        });
    }

    private void finishInstall(boolean ok, String info) {
        downloading = false;
        modelButton.setEnabled(true);
        statusText.setText(info);
        if (!CaptureService.running) CaptureService.status = info;
        if (ok) toast("模型準備完成，可以開始翻譯");
        else new AlertDialog.Builder(this).setTitle("模型安裝未完成").setMessage(info).setPositiveButton("確定", null).show();
    }

    private void start(int mode) {
        if (CaptureService.running) { toast("請先停止目前的翻譯再切換來源"); return; }
        if (!ModelInstaller.isInstalled(this)) {
            new AlertDialog.Builder(this).setTitle("需要語音模型")
                .setMessage("首次使用請先按「下載／檢查免費模型」。建議連接 Wi-Fi。")
                .setPositiveButton("確定", null).show();
            return;
        }
        desiredMode = mode;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("浮動字幕權限")
                .setMessage("要在 YouTube 或其他 App 上看到字幕，需要允許『顯示在其他應用程式上層』。也可以先略過，字幕仍會在這個 App 內顯示。")
                .setPositiveButton("前往設定", (dialog, which) -> openOverlaySettings())
                .setNegativeButton("先略過", (dialog, which) -> beginCapture(mode))
                .show();
        } else beginCapture(mode);
    }

    private void beginCapture(int mode) {
        if (mode == CaptureService.MODE_PLAYBACK) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION);
        } else {
            Intent i = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_MODE, CaptureService.MODE_MIC);
            startForegroundService(i);
            toast("已開始麥克風字幕，可切換到其他 App");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode != RESULT_OK || data == null) {
            toast("未取得擷取授權，已取消開始");
            return;
        }
        Intent i = new Intent(this, CaptureService.class)
            .setAction(CaptureService.ACTION_START)
            .putExtra(CaptureService.EXTRA_MODE, CaptureService.MODE_PLAYBACK)
            .putExtra(CaptureService.EXTRA_PROJECTION_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_PROJECTION_DATA, data);
        startForegroundService(i);
        toast("開始擷取允許的媒體聲音");
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) start(desiredMode);
            else toast("必須取得錄音權限才能辨識語音");
        }
    }

    private void stop() {
        if (!CaptureService.running) { toast("目前沒有正在運作的翻譯"); return; }
        startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void changeSize(int diff) {
        int current = prefs().getInt("fontSize", 20);
        int value = Math.max(15, Math.min(30, current + diff));
        prefs().edit().putInt("fontSize", value).apply();
        toast("浮動字幕字體大小：" + value);
        refreshOverlayStyle();
    }

    private void refreshOverlayStyle() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STYLE));
        }
    }

    private void shareTranscript() {
        String text = CaptureService.history.asText();
        if (text.isEmpty()) { toast("目前沒有已確認的字幕"); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Live Translate Captions 字幕紀錄");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "分享或儲存字幕文字"));
    }

    private void refreshCaptionStatus() {
        if (statusText == null) return;
        String state = CaptureService.status;
        if (!state.equals(statusText.getText().toString()) && !downloading) statusText.setText(state);
        if (CaptureService.english.isEmpty()) {
            englishText.setText("Waiting for speech…");
            chineseText.setText("等待聲音…");
        } else {
            englishText.setText(CaptureService.english);
            chineseText.setText(CaptureService.chinese);
        }
        boolean running = CaptureService.running;
        micButton.setEnabled(!running && !downloading);
        playbackButton.setEnabled(!running && !downloading);
        stopButton.setEnabled(running);
        String transcript = CaptureService.history.asText();
        if (transcript.isEmpty()) transcript = "字幕紀錄尚未產生";
        if (!historyText.getText().toString().equals(transcript)) historyText.setText(transcript);
    }

    private SharedPreferences prefs() { return getSharedPreferences("settings", Context.MODE_PRIVATE); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    private void textGap(LinearLayout root, int height) {
        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private TextView label(String text, int size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(3), 0, dp(3));
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(16));
        l.setBackground(rounded(PANEL, 16));
        return l;
    }
    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary ? BG : Color.WHITE);
        b.setBackground(rounded(primary ? ACCENT : 0xFF29364E, 11));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(0, dp(7), 0, dp(2));
        b.setLayoutParams(p);
        return b;
    }
}
