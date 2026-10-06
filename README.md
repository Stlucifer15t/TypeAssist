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

- **⚡ Streaming responses** — AI output appears live, token by token, in a floating card while Gemini / OpenAI-compatible / Cloudflare providers generate. No more staring at the spinner.
- **💡 Trigger autocomplete** — start typing a command (`.t`…) and a hint popup above the keyboard lists the matching commands with descriptions; tap one to complete and run it.
- **✂️ Selection toolbar** — select text in any app and a floating M3 toolbar appears: Fix grammar · Improve · Translate · Ask AI. Results replace only the selection (undo-able).
- **📊 Usage dashboard** — Home → Usage: requests, words generated, success rate, top commands and models. All local, 30-day rolling window.
- **🎨 Material 3 theme system** — Settings → General → *Appearance*: choose **System / Light / Dark / AMOLED** (true-black for OLED screens). Optional **dynamic colour** (Material You wallpaper colours) on Android 12+. Applied instantly across the app *and* the floating overlays (preview card, snippet picker, undo chip).
- **🧹 One profile per endpoint** — saving a provider (Gemini / custom API / Cloudflare) with the same URL + key no longer piles up duplicate rows when you change models; the existing profile is updated in place. Old duplicates are merged automatically on first launch.
- **🕘 History lasts 1 hour** — up from 5 minutes, and it now survives app restarts (persisted, capped at 200 items).
- **🎨 New App Icon** — Rebranded to **P** for Prompt AI with purple-to-blue gradient and sparkle accent. All mipmap densities + monochrome + Play Store icon updated.
- **💫 Loading Indicator Styles** — No more bland spinner! Choose from 7 styles in Settings → General → Floating controls:
  - `Classic` - Simple spinner
  - `Dots` - Bouncing 3 dots with alpha + scale
  - `Pulse` - Pulsing glow effect
  - `Bars` - Equalizer bars
  - `Typing` - iMessage-style typing bubble
  - `Pill` - Rounded pill with "AI thinking..."
  - `Neon ring` - Glowing neon tube that sweeps the ring: faded trail, soft halo, bright leading head and a breathing glow
  - Live preview in settings + fully animated overlay via `OverlayManager`
- **🎨 Indicator Colour** — Settings → General → *Indicator colour*: quick-pick swatches, a hex field for any colour at all (`#RRGGBB`), and a hue/saturation/brightness mixer. Applies to every style, including the neon ring's glow.
- **📏 Indicator Size** — Settings → General → *Indicator size*: 50%–200% slider that scales the whole overlay indicator (100% is the original size).
- Both settings live in `AppConfig` (`loadingIndicatorColor`, `loadingIndicatorSizePercent`) and are backward compatible: configs saved before them fall back to white / 100%.
- **🔧 GitHub Workflows Simplified** — All workflows (`build.yml`, `release.yml`, `pre-release.yml`, `preview.yml`) now only **build Full APK and upload unsigned artifact** (`app-full-release-unsigned.apk`). No `SIGNING_KEY_*` secrets required — download artifact and sign with MT Manager.
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

This fork's workflows build **unsigned** Full APK as artifact:
- **Pull Requests** → `PromptAI-Full-unsigned`
- **Tags `v*`** → `PromptAI-vX.Y.Z-Full-unsigned`
- **Preview tags `v*-preview.*`** → same pattern
- **Manual dispatch** → `PromptAI-preview-Full-unsigned`

Download from **Actions → workflow run → Artifacts**, then sign with **MT Manager** or `apksigner`.

[![Download Artifacts](https://img.shields.io/badge/Download-Artifacts-blue?style=for-the-badge)](https://github.com/Stlucifer15t/TypeAssist/actions)

---

## 📥 Installation & Setup

1.  **Download:** Get the latest unsigned APK from Actions artifacts or Releases.
2.  **Sign:** Sign with MT Manager if unsigned.
3.  **Permissions:** Enable the **Prompt AI Accessibility Service** in Android Settings.
4.  **API Key:** Open the app → **Settings** → add your API keys (Gemini, Cloudflare, Custom, Local LLM).
5.  **Start Typing:** Open any app and try a trigger!

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
