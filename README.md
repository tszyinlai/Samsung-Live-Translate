# Live Translate Captions — Android (Samsung)

Please see **README_繁體中文.md** for instructions.

This is a native Java Android Studio / Gradle project targeting Android 10+. It captures permitted media audio using AudioPlaybackCapture or microphone input, transcribes via offline Vosk and translates via free ML Kit on-device translation. Android ICU converts Chinese output to Traditional Chinese. No paid API, tracking, account, or subscription is used.

**Not a compiled or handset-tested APK.** A GitHub Actions workflow is included at `.github/workflows/build-android-apk.yml` to build `app-debug.apk` in an Android SDK-equipped GitHub runner. The source and pure-Java caption history have been statically checked, but the full Android build requires external Android tooling.
