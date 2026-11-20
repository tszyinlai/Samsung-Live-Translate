package com.example.livetranslate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Movable subtitle panel. It is intentionally not focusable or full-screen. */
public final class OverlayCaption {
    private final Context context;
    private final WindowManager manager;
    private WindowManager.LayoutParams params;
    private View view;
    private TextView englishView;
    private TextView chineseView;
    private TextView handle;

    public OverlayCaption(Context context) {
        this.context = context;
        this.manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (view != null || !Settings.canDrawOverlays(context)) return;
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(11);
        panel.setPadding(pad, dp(5), pad, dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE8172132);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), 0x9956D3CC);
        panel.setBackground(bg);
        handle = new TextView(context);
        handle.setText("━━  拖動字幕位置");
        handle.setTextSize(11);
        handle.setTextColor(0xFF9CC1C9);
        handle.setGravity(Gravity.CENTER);
        panel.addView(handle);
        englishView = new TextView(context);
        englishView.setTextColor(0xFFDCE5F2);
        englishView.setTextSize(13);
        englishView.setGravity(Gravity.CENTER);
        englishView.setMaxLines(3);
        panel.addView(englishView);
        chineseView = new TextView(context);
        chineseView.setTextColor(Color.WHITE);
        chineseView.setTextSize(20);
        chineseView.setGravity(Gravity.CENTER);
        chineseView.setMaxLines(4);
        panel.addView(chineseView);
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = dp(36);
        // Controls only the header's drag, allowing taps below the subtitle window.
        handle.setOnTouchListener(new View.OnTouchListener() {
            int lastY; float touchY;
            @Override public boolean onTouch(View ignored, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    lastY = params.y;
                    touchY = event.getRawY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.y = Math.max(0, lastY - Math.round(event.getRawY() - touchY));
                    if (view != null) manager.updateViewLayout(view, params);
                    return true;
                }
                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });
        view = panel;
        try {
            manager.addView(view, params);
            update("", "字幕會在這裏顯示");
        } catch (Exception e) {
            // Still show captions within MainActivity if Samsung blocks overlays.
            view = null;
        }
    }

    public void update(String english, String chinese) {
        if (view == null) return;
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int font = Math.min(30, Math.max(15, prefs.getInt("fontSize", 20)));
        boolean bilingual = prefs.getBoolean("bilingual", true);
        englishView.setVisibility(bilingual ? View.VISIBLE : View.GONE);
        englishView.setText(english == null ? "" : english);
        chineseView.setText(chinese == null || chinese.isEmpty() ? "…" : chinese);
        chineseView.setTextSize(font);
    }

    public void close() {
        if (view != null) {
            try { manager.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
    }
    private int dp(int value) { return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f); }
}
