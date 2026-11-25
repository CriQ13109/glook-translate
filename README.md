# Glook Translate

An open-source Android translator, camera/document OCR reader, and zero-knowledge communication app designed with Material 3 Expressive.

[Installation](#installation) · [Features](#features) · [Build & Setup](#build--setup) · [Engines & Security](#engines--security)

[Privacy Policy](#privacy-policy) · [Third-Party Notices](#third-party-notices) · [Contributing](#contributing) · [Changelog](#changelog) · [Security](#security)

---

## Installation

Requires Android 8.0 (API 26) or later.

* **Direct APK Download:** Download the latest `app-release.apk` from [GitHub Releases](https://www.google.com/search?q=https://github.com/criq13109/glook-translate/releases).
* **Google Play Store:** Distribution on Google Play Store is also being considered for the future.

## Shortcuts

Jump directly to the section you need:

| Section | Description |
| --- | --- |
| [Features](#features) | Material 3 Expressive UI, `ACTION_PROCESS_TEXT` instant sheet, auto-register matching, camera/PDF document translation, fullscreen & foldable conversation modes, Quick Settings tile, and Glance widget |
| [Build & Setup](#build--setup) | Prerequisites, Gradle build commands, R8/ProGuard rules, signing, and unit/screenshot tests |
| [Engines & Security](#engines--security) | Hybrid routing (`MlKitOnDeviceEngine` + BYOK `OpenAiCompatibleEngine` / `AnthropicEngine`), `RegisterDetector`, `PhoneticTranscriber`, and AES-256 SQLCipher + Android Keystore vault |
| [Privacy Policy](#privacy-policy) | Serverless local-first design, no ads or telemetry, `FLAG_SECURE`, disabled cloud backup, and local crash reporting |
| [Third-Party Notices](#third-party-notices) | Open-source libraries, bundled Google Sans Flex variable font, custom vector icons, and license attributions |
| [Contributing](#contributing) · [Security](#security) | Pull request guidelines, coding conventions, and vulnerability reporting |

## Overview

Glook Translate combines Material 3 Expressive UI (`androidx.compose.material3` with bundled `Google Sans Flex` typography and shape-morphing controls), system-wide `ACTION_PROCESS_TEXT` floating bottom sheets, automatic speech-register preservation (`RegisterDetector`), offline-capable ML Kit translation paired with optional BYOK streaming LLM providers, camera & file OCR (`PDF` and document parsers), phonetic transcription, foldable posture adaptation (`ConversationScreen`), a Quick Settings tile (`TranslateTileService`), and a 4-action Jetpack Glance home-screen widget (`GlookWidget`) into a single privacy-first Android application. All translation history, bookmarks, offline pack metadata, and API keys are stored exclusively on-device inside an AES-256 SQLCipher database unlocked by Android Keystore hardware keys.

**Disclaimer:** Glook Translate is an independent open-source project and is not affiliated with or endorsed by Google LLC, Anthropic, OpenAI, or any third-party translation provider. Machine and LLM-assisted translations may contain inaccuracies; verify critical medical, legal, or safety-related text independently.

## Features

* **Material 3 Expressive Interface (`HomeScreen`):** Surface-tone Bento cards (`Palette`, `Theme`), shape-morphing language swap and microphone controls (`MorphSwapButton`, `MorphMicButton`), tactile haptics (`Haptics`), and Dynamic Color support across 10 localized UI languages (`en`, `ko`, `ja`, `zh-CN`, `de`, `es`, `fr`, `pt`, `ru`, `vi`).
* **System-Wide Context Translation (`ProcessTextActivity`):** Select text in any app and tap **Glook Translate** from the Android text-selection menu (`⋮`) to translate inside a translucent bottom sheet—with one-tap in-place text replacement for editable fields.
* **Zero-Click Register & Nuance Matching (`RegisterDetector` & `PromptBuilder`):** Automatically detects formality, tone, and conversational register in the source text and preserves it in the target language without cluttering the UI with manual tone switches.
* **Hybrid Online / Offline Engine (`HybridTranslationRouter`):** Routes seamlessly between on-device models (`MlKitOnDeviceEngine` with downloadable `OfflinePacksSheet`) and user-configured BYOK endpoints (`OpenAiCompatibleEngine`, `AnthropicEngine`) with network awareness (`NetworkMonitor`).
* **Camera, Image & Document Translation (`ImageTranslateScreen`, `DocumentTranslateScreen`):** Captures text via CameraX (`CameraCapture`) and ML Kit OCR (`OcrEngine`), or parses and translates local `PDF` and text documents (`PdfParser`, `DocumentParsers`, `DocumentReader`).
* **Fullscreen & Foldable Conversation Modes (`FullscreenActivity`, `ConversationScreen`):** Displays high-contrast `Display`-scale typography with phonetic transcription (`PhoneticTranscriber`) for face-to-face communication, and adapts dynamically to foldable hinge postures (`Posture`).
* **System Entry Points (`GlookWidget`, `TranslateTileService`):** Launches text, voice, camera, or conversation translation directly from the home screen or Quick Settings panel.



## Build & Setup

### Prerequisites

* Android Studio Otter / Ladybug or newer with JDK 17+
* Android SDK 35+ (`minSdk 26`)

### Quick Start

```bash
git clone https://github.com/criq13109/glook-translate.git
cd glook-translate
./gradlew assembleDebug testDebugUnitTest

```

### Running Tests

Run the full unit and screenshot verification suites (`DocumentParsersTest`, `LocalizationTest`, `RegisterDetectorTest`, `ScreenshotTest`):

```bash
./gradlew testDebugUnitTest

```

### Release Build

Configure your release keystore in `local.properties` or environment variables and run with R8/ProGuard (`app/proguard-rules.pro`) enabled:

```bash
./gradlew assembleRelease

```

## Engines & Security

* **Translation Pipeline:** `TranslationEngine` abstraction backed by `MlKitOnDeviceEngine` for offline/low-latency execution and `OpenAiCompatibleEngine` / `AnthropicEngine` for BYOK LLM translation.
* **Hardware-Backed Encryption (`DatabaseKeyManager`, `GlookDatabase`, `Vault`):** Generates a random database passphrase protected by Android Keystore (`TEE` / `StrongBox`) to encrypt all local Room records via SQLCipher.
* **Window & Backup Hardening (`SecureWindow`):** Enforces `FLAG_SECURE` screen-capture protection, disables Android cloud/device backups (`data_extraction_rules.xml`), restricts cleartext traffic (`network_security_config.xml`), and captures uncaught exceptions locally (`CrashReporter`).



## Privacy Policy

Glook Translate operates with **zero developer-operated backend servers, zero analytics, and zero advertisements**. All translation history, saved library items (`LibrarySheet`), and BYOK API keys remain encrypted on your device inside the local SQLCipher vault. Network requests are sent solely to the translation provider you explicitly configure (or to Google ML Kit servers when downloading offline language packs). Uncaught crash logs generated by `CrashReporter` remain strictly on-device unless you manually export and share them.

## Third-Party Notices

Glook Translate bundles the `Google Sans Flex` variable font (`app/src/main/res/font/google_sans_flex.ttf`) and relies on open-source libraries including Jetpack Compose, Material 3, AndroidX Room, SQLCipher (`net.zetetic:sqlcipher-android`), Google ML Kit, CameraX, and Jetpack Glance. Full dependency attributions and license texts are available in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Contributing

Contributions, bug reports, and localization improvements are welcome.

* Follow standard Kotlin official code conventions and keep UI components aligned with Material 3 Expressive design tokens (`Palette`, `Theme`, `Type`).


* Ensure all unit and screenshot tests (`./gradlew testDebugUnitTest`) pass before opening a pull request.


* Keep user privacy intact: do not introduce external telemetry, analytics SDKs, or unencrypted persistence of user text or API keys.

## Changelog

### v0.1.0

* Initial release of Glook Translate (`app.glook.translate`).


* Material 3 Expressive `HomeScreen` with Bento cards, `MorphSwapButton`, `MorphMicButton`, and 10-language UI localization.


* System-wide `ACTION_PROCESS_TEXT` instant translation bottom sheet (`ProcessTextActivity`) with in-place text replacement.


* Hybrid translation router (`MlKitOnDeviceEngine`, `OpenAiCompatibleEngine`, `AnthropicEngine`) with automatic register matching (`RegisterDetector`).


* CameraX + ML Kit OCR (`ImageTranslateScreen`), `PDF` and text document translation (`DocumentTranslateScreen`), `FullscreenActivity` with `PhoneticTranscriber`, and foldable `ConversationScreen`.


* AES-256 SQLCipher + Android Keystore encrypted vault (`DatabaseKeyManager`, `GlookDatabase`), `FLAG_SECURE` protection, Quick Settings tile, and Jetpack Glance widget.



## Security

* **Cryptographic Architecture:** Database encryption keys are generated locally and wrapped via Android Keystore (`TEE` / `StrongBox`) in `DatabaseKeyManager`. Neither the developer nor third parties can decrypt local storage.


* **Reporting Vulnerabilities:** If you discover a security issue in key storage, `FLAG_SECURE` window handling, or `ACTION_PROCESS_TEXT` intent validation, please report it privately via GitHub Security Advisories instead of opening a public issue.

## License

Licensed under the Apache License, Version 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party dependency license details.