# AidFlow Pro

> Offline aid translation, powered by **Gemma 4** running entirely on your Android phone.
> Submission for the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon/overview).

---

## The problem

Hundreds of millions of people — refugees, migrant workers, disaster responders, and the
caregivers serving them — find themselves in situations where reliable translation can be
the difference between safety and harm: a misread prescription, a misunderstood evacuation
order, a medical history that can't be communicated to a doctor. These situations also
tend to coincide with the worst connectivity: refugee camps, conflict zones, remote rural
clinics, and the immediate aftermath of natural disasters.

Cloud translation services don't work where they're most needed.

## What AidFlow Pro does

Two flows, both **100% offline** after the one-time model download:

1. **Scan & Translate a Document** — point your camera at a prescription, an intake form,
   a sign, or any document. ML Kit Text Recognition v2 extracts the text on-device.
   Gemma 4 cleans up OCR artifacts (broken columns, garbled characters) and then
   translates into one of 20 languages. Export the result as **TXT, CSV, PDF, or DOCX**
   for sharing with caregivers, family, or partner NGOs.

2. **Real-time Voice & Text Translation** — speak or type in one language; Gemma 4
   translates and the device speaks the result aloud. The system speech recognizer runs
   on-device on Android 13+; everything else runs in the app process.

Both flows survive airplane mode. The only network connection AidFlow Pro ever makes is
the **one-time download** of the Gemma 4 E2B checkpoint on first launch.

## How Gemma 4 is used

| Capability | Gemma 4 role |
|---|---|
| Document text cleanup | Tuned prompt that restores paragraph order, fixes OCR confusions (`rn`→`m`, `0`↔`O`), and merges fragmented columns — **without translating** |
| Document translation | Faithful translation prompt that preserves numbers, names, dates, and units — critical for prescriptions and intake forms |
| Voice translation | Same translation prompt invoked on each final ASR transcript |

We deliberately use Gemma 4 as the **sole** translation engine (no ML Kit Translate
fallback) so that the quality story for the hackathon is a Gemma 4 story.

Prompts live in [`app/.../ai/Prompts.kt`](app/src/main/java/com/aidflow/pro/ai/Prompts.kt)
so the write-up and the code can't drift.

## Architecture

```
                       ┌────────────────────┐
                       │  Jetpack Compose   │
                       │     UI screens     │
                       └─────────┬──────────┘
                                 │
                       ┌─────────▼──────────┐
                       │   ViewModels       │
                       │ Scan / Translate / │
                       │   ModelSetup       │
                       └─────────┬──────────┘
                                 │
              ┌──────────────────┼─────────────────┐
              │                  │                 │
      ┌───────▼───────┐  ┌───────▼───────┐  ┌──────▼──────┐
      │  OcrEngine    │  │ Translation   │  │ SpeechEngine│
      │  (ML Kit v2)  │  │  Repository   │  │ TtsEngine   │
      └───────────────┘  └───────┬───────┘  └─────────────┘
                                 │
                       ┌─────────▼──────────┐
                       │   Gemma4Manager    │
                       │  (LiteRT-LM API)   │
                       └─────────┬──────────┘
                                 │
                       ┌─────────▼──────────┐
                       │  gemma-4-E2B-it    │
                       │    .litertlm       │
                       └────────────────────┘
```

Key implementation files:

- [`Gemma4Manager.kt`](app/src/main/java/com/aidflow/pro/ai/Gemma4Manager.kt) — wraps the
  LiteRT-LM `Engine` and a single long-lived `Conversation` (you cannot recreate the
  conversation without disposing the engine — known pitfall).
- [`ModelDownloader.kt`](app/src/main/java/com/aidflow/pro/ai/ModelDownloader.kt) — OkHttp
  download with HTTP Range resume support and Wi-Fi gating by default.
- [`OcrEngine.kt`](app/src/main/java/com/aidflow/pro/ocr/OcrEngine.kt) — ML Kit Text
  Recognition v2 (Latin script bundle by default; CJK/Devanagari/etc. one Gradle line away).
- [`SpeechEngine.kt`](app/src/main/java/com/aidflow/pro/asr/SpeechEngine.kt) — uses
  `SpeechRecognizer.createOnDeviceSpeechRecognizer()` on Android 13+, falls back to
  `EXTRA_PREFER_OFFLINE` on Android 12.
- [`DocxExporter.kt`](app/src/main/java/com/aidflow/pro/export/DocxExporter.kt) — a
  hand-rolled 150-line OOXML writer so we don't pay the 30 MB Apache POI tax.

## Languages supported

20 languages spanning the humanitarian use cases:
English, Spanish, French, Portuguese, Arabic, Ukrainian, Russian, Polish, Turkish, Persian,
Pashto, Urdu, Hindi, Bengali, Swahili, Amharic, Somali, Chinese (Simplified), Vietnamese,
Tagalog.

The translator can pair any two of these via Gemma 4. The on-device ASR and TTS depend on
the user's installed Android language packs.

## Installing the prebuilt APK

A prebuilt debug APK is checked in: [`AidFlowPro-debug.apk`](AidFlowPro-debug.apk) (~144 MB).

1. Enable **Settings → Apps → Install unknown apps** for your file manager / browser.
2. Copy the APK to your phone (USB, email, cloud) and tap to install.
3. Or install via adb: `adb install AidFlowPro-debug.apk`.

The app needs **Android 12+ (API 31)**. Voice translation works best on **Android 13+ (API 33)** where the system provides a guaranteed on-device speech recognizer.

On first launch the app downloads the Gemma 4 E2B model (~3 GB) over Wi-Fi.

## Building from source

Requirements: Android Studio Iguana+ OR JDK 17 + Android SDK 34 + Gradle 8.7.

```bash
./gradlew testDebugUnitTest      # 12 unit tests
./gradlew assembleDebug          # outputs app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # if a device is connected
```

On Windows you can use the convenience wrapper that bundles JDK/Gradle/SDK env-vars:

```powershell
.\build.ps1 test
.\build.ps1 apk
```

On first launch the app downloads `gemma-4-E2B-it.litertlm` (~3 GB) from
`huggingface.co/litert-community/gemma-4-E2B-it-litert-lm`. Wi-Fi is recommended.

### Skipping the first-run download (for development)

Sideload the model to the right path with adb:

```bash
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.aidflow.pro/files/models/
# Then move it into the internal files dir from inside the app (or use run-as)
```

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers:
- CSV escaping (commas, quotes, newlines, BOM) — `CsvExporterTest`
- DOCX OOXML structure + XML escaping — `DocxExporterTest`
- BCP-47 language tags + RTL flagging — `LanguagesTest`
- Prompt invariants — `PromptsTest`

## Performance notes

| Device | Backend | First-token | Decode tok/s |
|---|---|---|---|
| Pixel 8 Pro | CPU | ~5 s | ~17 |
| Pixel 8 Pro | GPU | ~0.8 s | ~22 |
| Samsung S23 | CPU | ~6 s | ~14 |

Numbers above are from public LiteRT-LM benchmarks for Gemma 4 E2B; we ship the CPU
backend by default because the GPU path still has OpenCL issues in the current LiteRT-LM
release.

## Project status

Built for the Gemma 4 Good Hackathon (deadline 2026-05-18). MVP scope: photo OCR →
translate → export + voice/text translate. Known follow-ups: legacy `.doc`, multi-document
batching, persistent translation history, on-device fine-tuning for medical terminology.

## License

Apache 2.0 — see [LICENSE](LICENSE).
