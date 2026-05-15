# AidFlow Pro — Technical write-up

## Hackathon framing

AidFlow Pro is a fully offline Android translation app targeting humanitarian
contexts where connectivity is unreliable: refugee intake centres, medical
clinics serving migrant patients, and field disaster response. We use the
**Gemma 4 E2B** model — Google DeepMind's edge-class multimodal LLM, released
April 2026 under Apache 2.0 — as the sole translation and document-cleanup
engine. The model runs on the device via the **LiteRT-LM** runtime; no audio,
image, or text leaves the phone after the one-time model download.

## Why Gemma 4

We considered three alternative architectures and rejected them in favour of
"Gemma 4 for everything":

1. **ML Kit Translate** — a great offline translator for 59 language pairs but
   limited to short phrases without context. It can't help us clean OCR output
   and would dilute the hackathon's "meaningful Gemma 4 use" requirement.
2. **A smaller text-only LLM (Gemma 4 1B, Phi, Qwen-0.5B)** — would run faster
   but produces visibly weaker idiomatic translations in low-resource languages
   like Pashto, Somali, and Amharic, which are exactly the languages our
   humanitarian framing requires.
3. **Cloud Gemini API** — the simplest path, but breaks the offline guarantee.

Gemma 4 E2B at ~3 GB on disk runs on phones with 1.5 GB free RAM, which covers
roughly any mid-range Android phone from 2022 onward. The model is multimodal
(text + vision + audio); we currently use only text + vision but the
infrastructure is in place for multimodal experiments.

## Runtime layering

```
LiteRT-LM  ─ com.google.ai.edge.litertlm:litertlm-android (CPU backend, XNNPack)
   ↑
Gemma4Manager  ─ thread-safe, single-Conversation-per-Engine wrapper
   ↑
TranslationRepository  ─ caches + facade
   ↑
ViewModels  ─ Compose state holders
   ↑
Screens  ─ Jetpack Compose, Material3
```

### Gemma4Manager (`app/.../ai/Gemma4Manager.kt`)

- One `Engine`, one `Conversation`, both lazily created in `AidFlowApp`.
- All calls funnel through a `kotlinx.coroutines.sync.Mutex` to serialise
  inference — Gemma's KV cache can't safely be hit from two coroutines at once.
- We bind to LiteRT-LM via reflection so that minor API drift between the
  `latest.release` resolved at build time and our compile-time expectations
  doesn't break the build. The wrapper exposes a stable Kotlin surface
  (`translate`, `cleanOcr`, `describeImage`).
- `engine.initialize()` runs on `Dispatchers.IO` because it takes multiple
  seconds. We expose a `StateFlow<State>` so the UI can show a loading panel.

### ModelDownloader (`app/.../ai/ModelDownloader.kt`)

- Pulls `gemma-4-E2B-it.litertlm` from the `litert-community` HuggingFace
  repository on first launch.
- HTTP Range header for resume — a dropped Wi-Fi connection mid-download is
  recoverable without redownloading the 2 GB you already have.
- Wi-Fi-only by default; user can toggle "allow mobile data" before starting.
- After download, refuses to proceed if the file is implausibly small
  (< 1 GB), which is the most common failure mode when the server returns an
  HTML error page instead of the model.

### Prompts

The two prompts that shape Gemma 4's output for this app live in one file so
the technical write-up and the code can't drift:

**Translate prompt (paraphrased):**
> You are a precise, faithful translator. Output ONLY the translation —
> no preamble, no explanation, no quoting. Preserve numbers, proper names,
> dates, units, and formatting. If the source contains medical, legal, or
> official terminology, prefer the standard term in the target language.

This wording is deliberately blunt about output format ("ONLY the translation")
because in early experiments Gemma 4 would otherwise prepend "Here is the
translation:" — fatal for a system that displays the output verbatim and pipes
it into a TTS engine.

**OCR cleanup prompt (paraphrased):**
> Restore paragraph breaks, drop garbled characters, fix obvious OCR confusions
> (e.g. 'rn'→'m'), reorder fragments if columns were merged. Keep the original
> language. Do not translate. Do not add commentary.

The explicit "do not translate" instruction prevents Gemma 4 from "helpfully"
translating Spanish prescriptions into English mid-cleanup, which broke our
chain when we tested with a CVS pharmacy label.

## Speech recognition

`SpeechEngine.kt` prefers `SpeechRecognizer.createOnDeviceSpeechRecognizer()`
on Android 13+ (API 33) — this is the only way to guarantee that audio stays
on the device. On Android 12 we fall back to the default `SpeechRecognizer`
with `EXTRA_PREFER_OFFLINE=true`; this is a best-effort fallback that depends
on the user having the relevant language pack installed.

Partial results update the UI's transcript field as the user speaks; we only
hand a finished sentence to Gemma 4 to keep CPU-decode latency tolerable.

## Exports

Four exporters, all targeting the public Downloads collection via
`MediaStore`:

| Format | Implementation | Dependency footprint |
|---|---|---|
| TXT | UTF-8 writer | none |
| CSV | RFC 4180 escaping, BOM, CRLF | none |
| PDF | `android.graphics.pdf.PdfDocument` + manual word-wrap | none (built-in) |
| DOCX | Hand-rolled 150-line OOXML zip writer | none |

We deliberately avoided Apache POI (~30 MB compressed dependency for DOCX
alone) by writing the five XML parts a `.docx` needs into a `ZipOutputStream`
ourselves. The implementation is verified against Microsoft Word 365,
LibreOffice Writer, and Google Docs.

## What we explicitly *didn't* build (and why)

- **Translation history.** Persistence is interesting but not core to the
  humanitarian use case (a refugee centre uses the app once with each
  intake), and it widens the data-leakage surface in a way that conflicts
  with the offline-aid framing.
- **Account system.** Same reasoning, doubled.
- **Legacy `.doc` format.** The Office Open XML era is two decades old.
  Supporting `.doc` would require Apache POI's HWPFDocument or a similarly
  large dependency; we'd rather keep the APK lean.
- **Cloud sync.** Same reasoning as history.

## Performance

LiteRT-LM ships CPU (XNNPack) and GPU (ML Drift) backends. We ship CPU only
for the MVP because reports indicate the GPU backend's OpenCL initialisation
fails on some devices in the current LiteRT-LM release; a `Use GPU (beta)`
toggle is a stretch goal.

Approximate numbers from the published Gemma 4 E2B LiteRT-LM benchmarks
(Pixel 8 Pro class, Snapdragon 8 Gen 3):

| Backend | Prefill | Decode |
|---|---|---|
| CPU 4-thread | 195 tok/s | 17.7 tok/s |
| GPU (ML Drift) | 1,293 tok/s | 22.1 tok/s |

For our typical short-phrase translation prompts, the user experiences
~2–4 seconds for a sentence on a flagship and ~5–10 seconds on a mid-range
phone. We hide this behind a `CircularProgressIndicator` and intentionally
chose not to use the streaming `sendMessage` API yet — the streaming path
in the early LiteRT-LM release is reportedly flaky.

## Repository layout

See [README.md](../README.md) for the file-by-file layout and the developer
quick-start.
