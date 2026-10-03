# Prompt AI

**AI writing tools and practical text utilities, wherever you type.**

Prompt AI is an Android assistant that works alongside your existing keyboard. Add a short command to text in a compatible app to rewrite it with an AI provider, expand a saved snippet, calculate an expression, or insert a date or password.

> **Project identity:** Prompt AI is the new display name for this TypeAssist-based project. The Android application ID (`com.typeassist.app`) and this repository’s URL remain unchanged so the rename does not create a separate Android package. Installing an APK as an update still requires a compatible signing certificate. The project is licensed under GPL-3.0; see [`LICENSE`](LICENSE).

## What you can do

- **Rewrite text with AI:** fix grammar, translate, adjust tone, improve wording, or use your own prompts.
- **Choose a provider:** Google Gemini, an OpenAI-compatible chat API, Cloudflare Workers AI, or an on-device GGUF model.
- **Discover models:** load Gemini models that support `generateContent`, or request model IDs from an OpenAI-compatible `/models` endpoint. Search the list, favorite models, and revisit recent selections. You can still type a model ID manually.
- **Use local text tools:** expand snippets, calculate expressions, insert the current time or date, and generate a password without making an AI request.
- **Undo and recover:** undo recent replacements and review recent text history when history is enabled.
- **Customize shortcuts:** edit standard and inline commands, add your own prompts, and configure a global rewrite pattern.

## Quick examples

Type one of these at the end of text in a compatible text field:

| Command | Result |
| --- | --- |
| `That sentence need fixing .g` | Corrects spelling and grammar |
| `Please send me the file .polite` | Rewrites the text in a polite tone |
| `你好世界 .tr` | Translates the text to English by default |
| `What is the capital of Japan? .ta` | Sends the question to your selected AI provider |
| `Total: (.c: 25 * 4)` | `Total: 100` |
| `..email` | Expands a snippet named `email` |
| `.now` / `.date` / `.pass` | Inserts a time, date, or generated password |
| `.undo` | Reverts a recent replacement when it is still available |

Commands are configurable in the app. By default, suffix commands are expected at the end of the text; enable **Allow triggers anywhere** in General settings to process them mid-text.

### Inline and global commands

Inline patterns are editable in **Commands → Inline**. The default inline Ask pattern is `(%:.ta)`, so a sample phrase is `(capital of Japan:.ta)`.

The default global rewrite pattern is `...%...`. Put an instruction between the markers to rewrite the text in the field, for example:

```text
I will be late ...make this sound more professional...
```

Snippet shortcuts use `..` by default. To save a snippet while typing, use:

```text
(.save:email:hello@example.com)
```

## AI providers

| Provider | Setup | Model selection |
| --- | --- | --- |
| **Google Gemini** | Add a Gemini API key. | Load models from Google; the list includes models advertising `generateContent` support. Manual IDs are also accepted. |
| **OpenAI-compatible API** | Enter a Base URL and, if required, an API key. | Load IDs from `/models`. Some services do not expose that endpoint; enter the model ID manually in that case. |
| **Cloudflare Workers AI** | Enter your Cloudflare Account ID, API token, and model ID. | Enter the model ID supported by your account. |
| **On-device model** | Select a GGUF model in Local LLM settings. | Runs locally through the app’s llama.cpp integration; no cloud AI key is needed. |

Use **Test connection & save** in provider settings to check a configuration. The test sends a short request to the selected provider; provider usage or billing may apply. Model discovery also makes network requests to the configured provider.

## Install and set up

1. Get a published APK from [Releases](https://github.com/Stlucifer15t/TypeAssist/releases), or download a CI artifact from [GitHub Actions](https://github.com/Stlucifer15t/TypeAssist/actions) when available.
2. Open Prompt AI and enable its **Accessibility Service** in Android Settings. Accessibility is required so the app can detect commands in editable text fields and insert the result.
3. In **Settings → AI Provider**, choose Gemini, an OpenAI-compatible API, Cloudflare, or Local LLM. Add the required credentials and model, then test the connection if using a cloud provider.
4. Open a compatible app, type your text and a command, and wait for the result.

### Device requirements

- Android 7.0 (API 24) or later.
- ARM64 (`arm64-v8a`) device.
- Internet access and provider credentials for cloud AI. Local models require enough free storage and memory for the chosen GGUF file.

Android and some apps restrict accessibility behavior in particular fields. Prompt AI may not be able to read or replace text in every app or every input type.

## Privacy and data

- Prompt AI has **no AI proxy server**. When you invoke an AI command, the relevant text is sent directly from your device to the provider you configured. That provider’s terms, privacy policy, and billing rules apply.
- The Accessibility Service observes text-change events in editable fields to find configured commands. AI processing is triggered by an AI command; local utilities and snippet expansion are handled on the device.
- API settings are saved in the app’s private local preferences. Treat exported backups as sensitive: an unprotected `.tabak` backup is compressed but not encrypted; choosing a backup password enables encryption.
- When enabled, the history feature keeps recent originals in app memory for up to five minutes. It is not a permanent archive.
- Provider connection tests and live model discovery also contact the selected provider.

Grant Accessibility permission only if you trust the app and understand that it needs access to text fields to provide its core functionality.

## Build from source

### Requirements

- JDK 17 or newer.
- Android SDK Platform 35.
- Android NDK `27.0.12077973` and CMake `3.22.1` for the native llama.cpp component.
- Clone the repository with its submodules.

```bash
git clone --recurse-submodules https://github.com/Stlucifer15t/TypeAssist.git
cd TypeAssist

# Full distribution, debug APK
./gradlew assembleFullDebug

# F-Droid distribution, debug APK
./gradlew assembleFdroidDebug

# Release APKs for both distributions
./gradlew assembleRelease

# Unit tests for the Full debug variant
./gradlew testFullDebugUnitTest
```

The **Full** variant includes in-app update checks; the **F-Droid** variant disables them. Release APKs are unsigned unless a release signing keystore is configured. The pull-request CI workflow builds both release variants and uploads its APKs as artifacts.

## Contributing

Issues and changes are welcome through [GitHub Issues](https://github.com/Stlucifer15t/TypeAssist/issues) and pull requests. Please include the Android version, device model, provider type, and relevant logs when reporting a problem—never post API keys or private text.

## License and origins

Prompt AI is distributed under the [GNU General Public License v3.0](LICENSE). It is based on the open-source [TypeAssist project](https://github.com/estiaksoyeb/TypeAssist); the existing package namespace and repository history are retained for compatibility and attribution.
