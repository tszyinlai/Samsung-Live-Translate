# Live Translate Captions — Samsung Android 免費版（測試原始碼）

## 功能

- **英文語音即時轉英文 + 繁體中文字幕**，可以選擇只看中文。
- **來源 1：手機內部聲音**，Android 10+ 的 MediaProjection + AudioPlaybackCapture，適合測試支援擷取的媒體播放器 / YouTube 等。
- **來源 2：手機麥克風**，適合真人說話、附近電腦播放的聲音，或不允許系統聲音擷取的應用。
- **可拖動、可調字體大小的浮動字幕**，可在其他 App 上顯示（需手動授權）。
- **英文 + 繁體中文字幕紀錄**，只存在 App 的記憶體中；可使用 Android 分享選單儲存到筆記、郵件等。
- **不需要 API Key、訂閱或雲端計費服務。** 語音和翻譯都在本機運算，不會上傳音訊。

## 安裝前請先了解

這份 ZIP 是 **Android Studio / Gradle 原始碼工程，並不包含已編譯的 APK**。當前執行環境沒有 Android SDK / Gradle / Android 實機，**不能宣稱已完成 APK 編譯或三星實機測試**。工程已包含 GitHub Actions 免費構建測試版 APK 的設定。

### 方法 A：使用 GitHub Actions 建立 APK（電腦不用安裝 Android Studio）

1. 在 [GitHub](https://github.com/) 建立一個 repository。可用個人帳戶的免費方案；GitHub Actions 的免費額度/收費條款以 GitHub 當前政策為準（公有儲存庫標準 runner 一般免費）。專案原始碼本身不含任何 API Key 或個人資料。
2. 將 ZIP 解壓後，**保持 `.github/workflows/build-android-apk.yml` 及 `app/` 目錄結構不變**，整份上傳到 repository 的預設 `main` 分支。（可以用 GitHub Desktop 免費軟件發佈）
3. 打開 repository 的 **Actions** 頁面，選 **Build Samsung Android APK (free)**，按 **Run workflow**；push 到 main 也會自動開始。
4. 如果編譯成功，打開該執行紀錄下的 **Artifacts**，下載 **Samsung-LiveTranslate-Captions-APK**。
5. 解壓 Artifacts，得到 **app-debug.apk**，傳送到三星手機，從「我的檔案」點開安裝。Android 可能要求允許該來源安裝 App。只安裝自己編譯且信任的檔案。

### 方法 B：Android Studio

1. 在 Windows 電腦安裝 Google 官方 [Android Studio](https://developer.android.com/studio)。
2. 安裝 Android SDK Platform 35、Build-tools 35.0.0、JDK 17，以及 Gradle 8.10.2（工程不附 Gradle wrapper；可以透過 IDE 設定本機 Gradle，或使用命令列 `gradle assembleDebug`）。
3. 打開工程根目錄，在終端執行 `gradle testDebugUnitTest assembleDebug`。
4. APK 檔案位置：`app/build/outputs/apk/debug/app-debug.apk`。

## 在 Samsung 手機上使用

1. 手機需為 **Android 10 / API 29 或以上**，建議可使用 Google Play 服務的 Galaxy 手機。
2. 首次打開，按 **下載／檢查免費模型**。英文 Vosk 約 40 MB，加上 ML Kit 中英文語言包。推薦 Wi-Fi。首次下載有可能需要較多儲存空間。下載內容經驗證後存於手機 App 專用目錄。
3. 如果要把字幕覆蓋在其他 App 上，按 **允許顯示在其他 App 上層**，在 Samsung 設定中批准浮動視窗權限。
4. 按 **手機內部聲音**，同意 Android 系統的「開始擷取」授權，然後切換到英語影片。或按 **麥克風**，對著手機說英文。
5. 字幕會出現在手機底部，拖動字幕頂部的細橫線可調位置。
6. 要停止時返回 App 按 **停止翻譯**，或從 Android 通知欄按 **停止字幕**。每次重新使用內部聲音模式都必須重新取得系統授權。
7. 已確認字幕可以透過 App 的 **分享／儲存字幕文字** 儲存或分享。預設不會錄音，也不會持久儲存文字。

## 免費和隱私

- 英文語音由 Vosk small model 在手機 CPU 運算；中文由 **Google ML Kit 裝置端翻譯** 完成，再由 Android ICU `Hans-Hant` 轉為繁體。這 **不是 Google Cloud Translation API**。
- 模型初次下載需要網絡；手機網絡流量按你的電信供應商套餐計算，建議使用 Wi-Fi，避免流量費。
- 不設廣告、API Key、登入、月費、時數限制。使用免費開源 / 裝置端功能，但下載與使用需符合各服務條款。
- Google ML Kit 有明文的第三方應用商標與來源標示要求；App 顯示 "Translations powered by Google"。如發佈至 Play Store，仍需確認當時有效的條款。
- Vosk 模型來自 [Vosk 官方模型頁](https://alphacephei.com/vosk/models)，下載後固定使用 SHA-256 驗證，避免不完整/被更改的模型。
- App 不使用 Google 雲端語音 API，不錄製/上傳語音；使用者主動分享字幕文字時，該分享目的地自己的隱私政策適用。

## 功能限制（重要）

1. **並非任何 App 的內部聲音都能擷取。** Android 明確限制受保護內容、某些通訊/語音通話，以及原 App 拒絕擷取的內容，不能繞過。若一直沒有字幕，先使用麥克風模式測試。這不是程式可解除的權限。
2. **翻譯品質不是商業雲端翻譯的保證等級。** Vosk small model 適合手機低延遲，但在嘈雜環境、英國不同口音、專業名詞或多人同時說話可能出錯。
3. **中途字幕會變動。** Vosk partial recognition 約 260ms 更新英文，中文中途翻譯限流約每 1.1 秒一次，已確認句子會另行翻譯；最終延遲依手機效能而異。
4. **三星省電模式** 可能在背景終止 App；如需要長時間翻譯，避免極端省電設定。
5. 部分沒有 Google Play 服務的裝置可能無法下載 ML Kit 翻譯模型。某些裝置的 Android ICU 也可能不提供 `Hans-Hant` 轉換器；此時程式會顯示錯誤而非把簡體誤標為繁體。
6. **本專案尚未在 Samsung 手機完成實機、安裝、Gradle 與 APK 功能驗證。** 如 Github Actions 編譯出錯或手機出現錯誤，請將完整錯誤截圖分享以供修正。

## 技術架構

```text
YouTube / media audio ── AudioPlaybackCapture ─┐
                                              ├─ AudioRecord PCM16 16kHz ─ Vosk streaming English ASR
Microphone ──────────── AudioRecord ─────────┘                    │
                                                                  └─ ML Kit on-device translate EN → ZH
                                                                                  │
                                                                      Android ICU Hans-Hant
                                                                                  │
                                                                    Overlay / MainActivity
```

版本：0.1.0-beta，Android Java、Gradle、無付費後端。

## 官方技術文件

- [Android AudioPlaybackCapture](https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration)
- [Android foreground service](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Android overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Google ML Kit on-device translation](https://developers.google.com/ml-kit/language/translation/android)
- [Google ML Kit usage guidelines](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Vosk model catalog](https://alphacephei.com/vosk/models)
