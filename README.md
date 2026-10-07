# Prompt AI 🚀 — Fork by Stlucifer15t

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" />
</p>

<p align="center">
  <b>AI writing tools and utilities in every Android text field.</b><br/>
  <i>Forked from <a href="https://github.com/estiaksoyeb/TypeAssist">estiaksoyeb/TypeAssist</a> with new features and cleanups.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" />
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-purple.svg" />
  <a href="https://github.com/Stlucifer15t/TypeAssist/releases"><img src="https://img.shields.io/github/downloads/Stlucifer15t/TypeAssist/total?logo=Github"/></a>
</p>

> **Acknowledgement:** This repository is a **fork** of [estiaksoyeb/TypeAssist](https://github.com/estiaksoyeb/TypeAssist) (originally Prompt AI / TypeAssist). Original work and core functionality by Estiak Soyeb. This fork maintains the same package ID `com.typeassist.app` for compatibility and builds upon it.

---

## 🆕 What's New in This Fork

### Compared to upstream:

- **🎨 New App Icon** — Rebranded to **P** for Prompt AI with purple-to-blue gradient and sparkle accent. All mipmap densities + monochrome + Play Store icon updated.
- **💫 Loading Indicator Styles** — No more bland spinner! Choose from 7 styles in Settings → General → Floating controls:
  - `Classic` - Simple spinner
  - `Dots` - Bouncing 3 dots with alpha + scale
  - `Pulse` - Pulsing glow effect
  - `Bars` - Equalizer bars
  - `Typing` - iMessage-style typing bubble
  - `Pill` - Rounded pill with "AI thinking..."
  - `Neon ring` - Locked, animated neon spectrum (electric cyan, laser blue, ultraviolet, magenta and hot pink) with a rotating light trail, breathing halo and white-hot core
  - Live preview in settings + fully animated overlay via `OverlayManager`
- **🎨 Indicator Colour** — Settings → General → *Indicator colour*: quick-pick swatches, a hex field for any colour at all (`#RRGGBB`), and a hue/saturation/brightness mixer. Applies to every style except the fixed neon spectrum.
- **🌑 Material 3 Themes** — Choose System, Light, Dark, or AMOLED black in Settings → General → Appearance. The installed version shows its release announcement on first launch, once per version.
- **📏 Indicator Size** — Settings → General → *Indicator size*: 50%–200% slider that scales the whole overlay indicator (100% is the original size).
- Both settings live in `AppConfig` (`loadingIndicatorColor`, `loadingIndicatorSizePercent`) and are backward compatible: configs saved before them fall back to white / 100%.
- **🔏 Signed Android releases** — Stable releases, preview tags, and manual preview builds produce a ready-to-install APK signed with the repository’s protected Android release keystore. Stable tags create/update a GitHub Release with the APK attached, and in-app update checks point to this fork. Pull-request/branch CI artifacts remain unsigned and never receive signing secrets.
- **🧹 UI Cleanup** — Removed `Support development` (Binance/USDT) section, `Twitter`, and `Telegram community` links from Home screen. `Made with care` now only shows **Source code on GitHub → https://github.com/Stlucifer15t/TypeAssist**
- **⚙️ Config** — Added `loadingIndicatorStyle` to `AppConfig` with backward-compatible fallback.

---

## 📸 Screenshots

  <div align="center">
    <img src="screenshots/7.PNG" width="30%"  alt=""/>
    <img src="screenshots/8.PNG" width="30%"  alt=""/>
    <img src="screenshots/9.PNG" width="30%"  alt=""/>
  </div>

---

## ✨ Features (from upstream)

### 🤖 AI Capabilities
*   **Ask AI:** Query Google Gemini, Cloudflare Workers AI, or **any OpenAI-compatible Custom API** directly from any app.
*   **Live Model Picker:** Load models available to your Gemini key or OpenAI-compatible endpoint, then search, select, favorite, and revisit recent models.
*   **Provider Diagnostics:** Test a setup with clearer guidance for common key, URL, model, quota, and network errors.
*   **Grammar Fix:** Instantly correct spelling, punctuation, and grammar errors.
*   **Translation:** Translate text from any language to English (or your preferred language).
*   **Tone Adjustment:** Rewrite messages to be more professional, polite, or friendly.
*   **Inline Commands:** Embed AI queries within sentences using a configurable inline pattern (the default is `(%:.ta)`, for example `(capital of Japan:.ta)`).
*   **Global Rewrite:** Transform the entire text field with a custom instruction using `...instruction...`.
    *   Example: `meeting at 3pm, bring laptop ...expand to formal invite...`
    *   **Result:** "Please join us for a meeting at 3:00 PM. Kindly remember to bring your laptop as we will be working through some examples together."

### 🛠 Utility Belt (Offline Tools)
*   **Smart Calculator:** Solve math expressions in-place. Example: `(.c: 25 * 4 + 10)` -> `110`
*   **Snippets (Text Expander):** Expand shortcuts into full text blocks. Example: `..email` -> `user@example.com`
*   **Quick Save:** Save new snippets instantly: `(.save:trigger:content)`
*   **Date & Time:** Insert current timestamps with `.now` or `.date`.
*   **Password Generator:** Generate strong random passwords with `.pass`.

### 💾 Data Management
*   **Backup & Restore:** Export your settings, snippets, and API configurations to a `.tabak` file. Add a password to encrypt the backup.
*   **Saved Configurations:** Save and switch between multiple API setups.

### 🛡 Safety & Privacy
*   **Global Undo:** Revert any action instantly using `.undo`.
*   **History Manager:** View and recover original text from the last 5 minutes.
*   **Privacy First:** AI requests are sent only when you invoke an AI command.

---

## 📖 Usage Guide

| Trigger | Action | Example |
| :--- | :--- | :--- |
| `.ta` | Ask AI | `Population of Tokyo? .ta` |
| `.g` | Fix Grammar | `i go home yestarday .g` |
| `.tr` | Translate | `你好世界 .tr` |
| `.polite` | Polite Tone | `Give me the money .polite` |
| `...` | Global Rewrite | `I am late ...make polite...` |
| `.undo` | Undo | Reverts the last replacement |

---

## 🧪 Preview Builds

Build and distribution workflows are split by trust level:
- **Pull requests and branch CI** → unsigned `PromptAI-Full-unsigned` artifact (for CI/testing only).
- **Stable tags `v*`** → signed APK attached to the GitHub Release as `PromptAI-vX.Y.Z.apk`.
- **Preview tags `v*-preview.*`** → signed preview APK artifact.
- **Manual preview build** → signed `PromptAI-preview.apk` artifact.

Signed workflows require the maintainer keystore secrets described below. Users can install the signed release APK directly—no MT Manager signing step.

[![Download Artifacts](https://img.shields.io/badge/Download-Artifacts-blue?style=for-the-badge)](https://github.com/Stlucifer15t/TypeAssist/actions)

### Android release signing (maintainers)

The same signing key must be used for every release so Android accepts updates over an existing installation. **Never commit the keystore or its passwords.** Create a release keystore once on a trusted machine and keep a secure offline backup:

```bash
keytool -genkeypair -v -keystore prompt-ai-release.jks -storetype JKS -alias promptai -keyalg RSA -keysize 2048 -validity 10000
```

Add these as **repository Actions secrets** in GitHub → **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of `prompt-ai-release.jks` (Linux: `base64 -w 0 prompt-ai-release.jks`; macOS: `base64 < prompt-ai-release.jks \| tr -d '\n'`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password chosen when creating it |
| `ANDROID_KEY_ALIAS` | `promptai` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | Password for that key alias |

For local signing, the ignored root-level `keystore.properties` file uses these keys (store real values only on your own machine):

```properties
storeFile=/absolute/path/to/prompt-ai-release.jks
storeType=JKS
storePassword=your-keystore-password
keyAlias=promptai
keyPassword=your-key-password
```

The tag and manual-preview workflows validate these secrets, decode the keystore only into the runner’s temporary directory, build the Full APK, and verify its signature. Local maintainers can alternatively use the ignored `keystore.properties` file supported by Gradle. Keep the application ID unchanged and increment `versionCode` for every published update. **If this private key is lost or replaced, Android will not accept future APKs as updates.**

**Moving existing users to the new key:** an APK previously signed with a different MT Manager key may not accept the first centrally signed update. Those users may need to export a backup in-app, uninstall the old copy, and install the new signed APK once. After that, subsequent releases signed with this same keystore install as normal updates. Back up first; uninstalling clears the app’s private data.

---

## 📥 Installation & Setup

1.  **Download:** Get the signed APK from the latest GitHub Release.
2.  **Install/update:** Open it with Android’s package installer. Future releases signed with the same keystore install over the existing app.
3.  **Permissions:** Enable the **Prompt AI Accessibility Service** in Android Settings.
4.  **API Key:** Open the app → **Settings** → add your API keys (Gemini, Cloudflare, Custom, Local LLM).
5.  **Start Typing:** Open any app and try a trigger!

> Pull-request/branch CI artifacts are unsigned and are not the normal install/update path.

---

## 🛠 Tech Stack
*   **UI:** Jetpack Compose (Material 3)
*   **Language:** Kotlin 2.1.0
*   **Network:** OkHttp / Gson
*   **Service:** Android AccessibilityService
*   **Architecture:** MVVM

---

## 📜 License
Distributed under the **GPLv3 License**. See `LICENSE` for more information. Original license retained from upstream.

---

## 🔒 Privacy & Data Security

Prompt AI has no AI proxy server: AI requests go directly from your device to the provider you choose.

*   **Direct Connection:** AI text is sent directly from your device to your selected provider.
*   **On-Demand AI:** The Accessibility Service observes editable-field text changes to find commands. AI text is sent only when an AI command is invoked.
*   **Local Storage:** API settings are kept in app-private storage.

### 📜 Provider Policies
*   **Google Gemini:** [API Terms of Service](https://ai.google.dev/gemini-api/terms)
*   **Cloudflare Workers AI:** [Data Usage & Privacy](https://developers.cloudflare.com/workers-ai/platform/data-usage/)

---

## 🙏 Credits
- **Original Author:** [estiaksoyeb](https://github.com/estiaksoyeb) — [TypeAssist](https://github.com/estiaksoyeb/TypeAssist)
- **Fork Maintainer:** [Stlucifer15t](https://github.com/Stlucifer15t)
- New icon, loading styles, workflow simplification, and UI cleanup by this fork.
