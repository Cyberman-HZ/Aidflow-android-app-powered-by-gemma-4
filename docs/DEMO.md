# Demo script (60–90 seconds)

Optimised for the hackathon's "short video demonstrating real-world use"
requirement. Record on a real device with airplane mode visible in the
status bar throughout.

## Setup

- Pre-install the APK and pre-download the Gemma 4 model so the demo doesn't
  spend 30 seconds on the download screen. Show one screenshot of the
  download UI at the start instead.
- Have these props ready:
  - A Spanish-language prescription label (or any printed Spanish text)
  - A short English phrase to speak aloud for voice translation

## Beats

1. **(5 s)** Title card: "AidFlow Pro — offline aid translation, powered by
   Gemma 4."
2. **(5 s)** Show the airplane-mode toggle and a screenshot of the model
   setup screen with the explanation that this is downloaded once and never
   again.
3. **(20 s)** **Scan flow.** Open Home → Scan → pick a Spanish prescription
   photo. Watch ML Kit extract the text. Tap "Clean with Gemma" — show the
   cleaned version. Set target language to English. Tap "Translate" — show
   the translation. Tap "Export" → choose PDF → open the resulting PDF.
4. **(20 s)** **Voice flow.** Back to Home → Translate. Set source = English,
   target = Arabic. Tap the mic. Speak "Where is the nearest hospital?". Show
   live transcript and the Arabic translation. Tap the speaker icon — Arabic
   TTS plays.
5. **(10 s)** **Humanitarian framing voiceover.** "200,000+ refugee intake
   workers around the world don't have reliable internet. AidFlow Pro puts
   Gemma 4 in their pocket — offline, multimodal, fast enough to be useful."
6. **(5 s)** End card: GitHub repo URL + Kaggle handle.

## Things to remember while recording

- Airplane mode icon visible the WHOLE time.
- Speak slowly enough that the on-device ASR captures cleanly.
- For the export demo, pre-clear the Downloads folder so the new file is
  obvious.
- The model setup screen download progress is visually slow — don't film it
  live; use a still or a 2x speed cut.
