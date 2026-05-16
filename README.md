# AidFlow Pro — Mobile

> **Offline field worker assistant, powered by Gemma 4.**
> Android companion to the [AidFlow Pro web app](https://github.com/Cyberman-HZ/Aidflow).

[![Beta](https://img.shields.io/badge/status-beta-orange)](#status)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Gemma 4](https://img.shields.io/badge/Gemma%204-E2B%20on--device-brightgreen)](https://ai.google.dev/gemma)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)](#installing)

This project is **AidFlow Pro Mobile**, an Android app built for the
[Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon/overview)
(Google DeepMind × Kaggle, $200K prize pool, submission deadline 2026-05-18).

---

## Status — Beta

This is an **early beta release**. The core flows are working and battle-tested
enough to demo, but expect rough edges: large model download on first launch,
multi-second inference latency on mid-range phones, and occasional language
gaps in the on-device speech recognizer. Not yet hardened for unsupervised
deployment in critical operations.

## What it is

AidFlow Pro Mobile is a **fully offline humanitarian aid assistant** designed
for **field aid workers** — the people doing intake at refugee camps, running
distribution lines at disaster sites, triaging in mobile clinics, and
communicating across language barriers where connectivity is unreliable.

Everything runs on the phone:

- **Gemma 4 E2B** (effective 2B-parameter multimodal model) via the LiteRT-LM
  runtime — text, vision, and audio understanding without leaving the device.
- **ML Kit Text Recognition v2** for fast offline OCR.
- **Google Document Scanner** for camera + auto-crop + perspective correction.
- **Android system on-device speech recognizer** + **TextToSpeech**.

The only network call AidFlow Pro Mobile ever makes is the **one-time
download** of the Gemma 4 checkpoint (~2.6 GB) on first launch. Everything
after that works in airplane mode — refugee camps, evacuation shelters, remote
clinics, conflict zones.

## How it relates to the AidFlow Pro web app

The [AidFlow Pro web app](https://github.com/Cyberman-HZ/Aidflow) is the
**desk-side coordination tool**: case management, priority triage, dispatch
orders, knowledge-base Q&A, reporting. It is itself offline-first and powered
by Gemma 4 (via Ollama).

**AidFlow Pro Mobile is its eyes and ears in the field.** Field workers
capture data on their phone while walking through camps or clinics; the web
app, running on a coordinator's laptop, picks it up for triage and
distribution.

The interop is **deliberate and zero-friction**:

- The mobile app's family-intake Excel exports use the *exact* 12 canonical
  column names from the web app's
  [`spreadsheetImport.ts`](https://github.com/Cyberman-HZ/Aidflow/blob/main/src/services/spreadsheetImport.ts)
  (`head_name`, `member_count`, `children_under_5`, `elderly_count`,
  `has_pregnant_member`, `medical_conditions`, `displacement_status`,
  `income_level`, `location_sector`, `street`, `city`, `notes`), so importing
  a mobile-captured file into the web app requires no column-mapping step.
- The mobile app's priority-score prompt mirrors the web app's triage logic,
  so families arrive in the web app already pre-scored.

The two apps share a name on purpose — they are halves of the same system.

## Screenshots

| | |
|---|---|
| ![Home](docs/screenshots/01-home.png) | ![Family Intake](docs/screenshots/02-family-intake.png) |
| Home — four feature cards | Family intake by voice |
| ![Family extracted](docs/screenshots/03-family-extracted.png) | ![Family captured](docs/screenshots/04-family-captured.png) |
| Extracted family with priority score | Captured-session list ready to export |
| ![Items](docs/screenshots/05-items.png) | ![Scan translated](docs/screenshots/06-scan-translated.png) |
| Identify items from a photo | Translate a paper document |
| ![Translate voice](docs/screenshots/07-translate-voice.png) | ![Export sheet](docs/screenshots/08-export-sheet.png) |
| Real-time voice translation | Export — pick scope and format |
| ![Document scanner](docs/screenshots/09-doc-scanner.png) | ![Model setup](docs/screenshots/10-setup.png) |
| Built-in document scanner | One-time on-device setup |

(Add screenshots under [`docs/screenshots/`](docs/screenshots/) — see the
[guide](docs/screenshots/README.md) for filenames.)

---

## Features and their real-life impact

### 1. Voice & photo family intake → Excel

Field worker says *"Ahmed Mahmoud, 42, four kids, youngest is six with asthma,
displaced from Aleppo three weeks ago"* — or photographs a paper registration
form. Gemma 4 extracts a structured family record (head name, member counts,
medical conditions, displacement, income, location, priority score 0–100 with
reasoning). The worker reviews, edits, captures the next family, and exports
an Excel file that drops straight into the AidFlow web app.

**Impact.** Cuts a paper-intake cycle from **5+ minutes of typing per family
to ~30 seconds of speaking**. In mass-displacement events (recent earthquakes,
floods, conflict displacements have all required registering 1,000+ families
in a single day per camp), this turns a multi-hour backlog into a real-time
operation, and frees the worker to keep their eyes on the family they're
speaking with instead of a phone keyboard.

### 2. Identify items from a photo

Photograph relief supplies. Gemma 4's multimodal vision lists every distinct
item with a category (food / water / medical / shelter / hygiene / clothing /
education / other), quantity estimate, and unit. Stack multiple photos into
one inventory and export.

**Impact.** Rapid supply audits at distribution sites and warehouse
checkpoints — what used to be a clipboard-and-tally exercise becomes a
camera-and-confirm exercise. Helps coordinators know what's actually
available *before* dispatching aid orders.

### 3. Document scan + OCR + translation + export

Photograph a prescription, an intake form, a foreign-language sign, or a
medical history. ML Kit extracts text; Gemma 4 cleans up the OCR layout *or*
re-reads the image directly with multimodal vision; Gemma 4 then translates
into any of 20 languages. Export the result as TXT, CSV, PDF, or DOCX — and
choose what to include: original text only, translation only, or both.

**Impact.** Medical and legal documents become legible to caregivers in
their working language. A prescription written in Spanish becomes a PDF in
Arabic ready to share with a refugee family in seconds — fully offline, no
data leaves the phone.

### 4. Real-time voice and text translation

Speak in one language, get the translation read back in another. Works
offline (Android 13+ has a guaranteed on-device speech recognizer; on Android
12 we fall back to the system's offline preference). 20 supported languages
spanning the humanitarian use-case set (English, Spanish, French, Portuguese,
Arabic, Ukrainian, Russian, Polish, Turkish, Persian, Pashto, Urdu, Hindi,
Bengali, Swahili, Amharic, Somali, Chinese Simplified, Vietnamese, Tagalog).

**Impact.** Triage conversations across language barriers. A field clinic
volunteer can take a patient history from a Pashto-speaking mother while
working in English. An evacuation coordinator can deliver instructions in
Ukrainian without an interpreter on site.

### 5. Built-in camera with auto-crop and lens/flash control

Every photo input — Family Intake (paper form mode), Document Scan, and
Identify Items — has both a gallery picker and a Take-photo button. The
camera uses Google's on-device document scanner, which provides:

- Front/back lens switch
- Flash toggle (auto / on / off)
- Live edge detection with green corner outline
- Auto perspective correction (paper turned into a clean rectangle)
- Manual corner adjustment if the auto-detection guesses wrong
- Color / grayscale / B&W filter options
- Retake before confirming

**Impact.** Aid workers don't need a flatbed scanner or a clean working
surface. A crumpled paper form held at an angle on a folding table becomes a
near-perfect rectangular scan ready for OCR.

### 6. Offline-first, end-to-end

Gemma 4 runs on the phone via LiteRT-LM (CPU backend, XNNPack-accelerated).
ML Kit OCR runs on the phone. The document scanner runs on the phone. The
speech recognizer runs on the phone. TextToSpeech runs on the phone.

**Impact.** Works in places where humanitarian work most needs to work —
refugee camps and disaster zones with no mobile data, conflict zones where
sending data across borders would be unsafe, mobile clinics in rural areas
with intermittent coverage. **No data ever leaves the device.**

### 7. Web-app interop, no mapping required

The XLSX/CSV exporters target the AidFlow web app's exact canonical column
names. Mobile-captured data drops into the web app's spreadsheet import
dialog and is recognized field-by-field with no manual mapping.

**Impact.** A field team captures intake on five phones during a morning
shift. At lunch, the coordinator collects five Excel files via USB / Bluetooth
/ a local Wi-Fi share, drags them into the web app, and the case list is
populated, pre-triaged, and ready for distribution planning by the time the
team is back in the field.

---

## Installing the prebuilt APK

A signed debug APK is checked into the repository root:

**[`AidFlowPro-debug.apk`](AidFlowPro-debug.apk)** (~145 MB)

1. Copy the APK to your phone (USB, email, Drive — anything).
2. On the phone: **Settings → Apps → Install unknown apps** → enable for your
   file manager / browser.
3. Tap the APK to install. Allow camera + microphone permissions when asked.
4. Via adb: `adb install AidFlowPro-debug.apk`.

Requirements: **Android 12+ (API 31)**, **3 GB free storage**, **2 GB free
RAM**. Voice translation works best on Android 13+ (API 33) where Google
ships a guaranteed on-device speech recognizer.

On first launch the app downloads `gemma-4-E2B-it.litertlm` (~2.6 GB) from
HuggingFace. Wi-Fi is strongly recommended; the in-app downloader gates on
unmetered networks unless you tick "allow mobile data."

## Building from source

```bash
./gradlew testDebugUnitTest      # 31 unit tests
./gradlew assembleDebug          # writes app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # if a device is connected
```

Requires JDK 17 + Android SDK 34 + Gradle 8.7, or Android Studio Iguana or
newer. On Windows, you can use the bundled convenience wrapper (downloads
JDK + Gradle + SDK into `.tools/`):

```powershell
.\build.ps1 test
.\build.ps1 apk
```

## Architecture

```
                       ┌────────────────────┐
                       │  Jetpack Compose   │
                       │     UI screens     │
                       └─────────┬──────────┘
                                 │
                       ┌─────────▼──────────┐
                       │   ViewModels       │
                       │ Family / Items /   │
                       │ Scan / Translate / │
                       │ ModelSetup         │
                       └─────────┬──────────┘
                                 │
       ┌──────────┬──────────────┼──────────────┬───────────┐
       │          │              │              │           │
 ┌─────▼──┐ ┌─────▼────┐ ┌───────▼──────┐ ┌────▼─────┐ ┌────▼─────┐
 │ OCR    │ │ Doc      │ │ Translation  │ │ Speech   │ │ Intake   │
 │ ML Kit │ │ Scanner  │ │ Repository   │ │ Engine   │ │ Mapper   │
 └────────┘ │ ML Kit   │ └───────┬──────┘ │ + TTS    │ │ +JSON    │
            │ GMS      │         │        └──────────┘ │ extract  │
            └──────────┘         │                      └──────────┘
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

Detailed write-up: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Tuned
prompts: [`docs/PROMPT_CARDS.md`](docs/PROMPT_CARDS.md). Demo script:
[`docs/DEMO.md`](docs/DEMO.md).

## Tests

```bash
./gradlew testDebugUnitTest
```

31 unit tests across:

- **CSV / DOCX / XLSX / TXT exporters** — escaping (commas, quotes, newlines,
  BOM), OOXML structure, cell references past column Z, XML escaping.
- **JsonExtractor** — strips markdown fences, scans past preambles, handles
  nested braces inside string literals.
- **IntakeMapper** — 12-canonical-field round-trip, defensive defaults,
  loose enum normalization for `displacement_status` and `income_level`.
- **Languages** — every BCP-47 tag round-trips; RTL flags correct.
- **Prompts** — translate / OCR-cleanup / family-extraction prompt invariants.

## Submission information

- **Hackathon:** [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon/overview) (Kaggle × Google DeepMind, 2026).
- **Required model use:** Gemma 4 E2B is the sole inference engine for
  translation, OCR cleanup, family extraction (voice + photo), and item
  identification. Multimodal vision + structured-JSON output + native function
  calling all exercised.
- **Companion artifact:** [AidFlow Pro web app](https://github.com/Cyberman-HZ/Aidflow)
  (separate repository, same maintainer).

## License

[Apache 2.0](LICENSE). Gemma 4 model weights are governed by the [Gemma Terms
of Use](https://ai.google.dev/gemma/terms); downloading and running the model
implies acceptance.
