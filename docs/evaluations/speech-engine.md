# Evaluation: Stage 4.1 `SpeechEngine`

**Summary / recommendation.** Use two engines behind one `SpeechEngine` interface, because
the two jobs are different.
**(i) Tap-to-talk voice commands:** use **Vosk** offline with the small Indian-English model
and a restricted command grammar. It is Apache-2.0, answers in under a second on weak CPUs,
works offline, and no audio leaves the board. ⚠ **Needs owner approval (size):** the
`vosk-android` AAR is ~12 MB for all ABIs (~3–4 MB per ABI after ABI splits) and the model is
a **36 MB** download (+42 MB if Hindi commands are wanted).
**(ii) Transcribing class recordings:** use **cloud STT via our backend**. Default to **Sarvam
AI** (₹30/hour, built for Indian English, Hindi and code-mixed Hinglish), with **OpenAI
`gpt-4o-mini-transcribe`** ($0.003/min) as a fallback vendor. Long-form on-device
transcription with whisper.cpp is too slow and memory-heavy on 2 GB boards, and the models
that run acceptably there (tiny/base) are poor at Hindi. Android's built-in `SpeechRecognizer`
is not an option: it needs a recognition service, which normally comes from the Google app
and GMS, and these boards usually lack GMS. ⚠ **Privacy:** option (ii) sends classroom audio
(including children's voices) to our backend and on to a third-party processor. See the
privacy section.

## Comparison

| | Cloud STT via backend | Vosk (offline) | whisper.cpp (offline) |
|---|---|---|---|
| Size (download) | 0 on board | small en-in **36 MB**; small hi **42 MB** | tiny 75 MB / base 142 MB / small 466 MB (f16); q5_1 about 31 / 57 / 181 MB |
| Native lib added to APK | 0 (plain HTTPS through our client) | `vosk-android` 0.3.47 AAR ≈ **12.3 MB** all ABIs (~3–4 MB per ABI) ⚠ >5 MB | Built from source with NDK; roughly 1–2 MB per ABI (estimate, measure) |
| Offline | No (school Wi-Fi is reliable; queue uploads when offline) | Yes | Yes |
| Licence | Commercial API terms (vendor) | **Apache-2.0** (code and these models) | **MIT** (code), MIT (Whisper weights) |
| Cost | Sarvam **₹30/h** (₹0.5/min, ₹45/h with diarization); OpenAI mini **$0.003/min**, gpt-4o-transcribe/whisper-1 $0.006/min; Google V2 **$0.016/min** real-time, ~$0.003/min dynamic batch; Azure batch **$0.18/h** | Free | Free |
| Accuracy: Indian English | Good; Sarvam and big vendors are trained on Indian speech | small en-in: **49% WER** (NPTEL); big en-in model: 36% (1 GB, server only). Fine for a closed command grammar, poor for free dictation | base/small are decent on accented English; tiny is weak |
| Accuracy: Hindi | Sarvam Saaras v3: 19.3% WER on IndicVoices (vendor claim); handles code-mix | small hi: 20.9% WER (IITM) | tiny/base effectively unusable (tiny ≈ 100% WER on FLEURS Hindi); needs medium or larger |
| Latency on 2–4 GB boards | Network round trip: ~1–3 s for short clips; long files are processed async on the server | Streaming, **< 0.5 s** after end of speech; ~100–200 MB RAM | Short command: ~1–3 s with tiny on Cortex-A55-class CPUs (30 s window). Long-form: tiny/base near 1× real-time at best, small **slower than real-time**; small needs ~850 MB RAM (f16) |
| Effort | Low on board (upload file); medium on backend (vendor adapter, retries, deletion) | Low: AAR + model download + grammar JSON | Medium: JNI/NDK build, VAD, chunking, thread tuning |

Sources: [Vosk model list](https://alphacephei.com/vosk/models)
([models.md](https://github.com/alphacep/vosk-space/blob/master/models.md)),
[vosk-android on Maven Central](https://search.maven.org/artifact/com.alphacephei/vosk-android),
[vosk-api (Apache-2.0)](https://github.com/alphacep/vosk-api),
[whisper.cpp (MIT, v1.9.4, Sep 2026)](https://github.com/ggml-org/whisper.cpp),
[whisper.cpp model sizes and memory](https://whipscribe.com/tools/whisper-cpp),
[whisper.cpp on Pi 5: tiny/base real-time, small 0.4–0.6×](https://turingpi.com/whisper-cpp-piper-tts-arm64-turing-pi-rk3588/),
[whisper-tiny Hindi FLEURS WER 102.3 per the Whisper paper](https://huggingface.co/Aryan-401/whisper-tiny-finetune-hindi-fleurs),
[Whisper paper](https://arxiv.org/abs/2212.04356),
[Sarvam pricing](https://docs.sarvam.ai/api/getting-started/pricing),
[Saaras v3 benchmark (vendor)](https://www.sarvam.ai/blogs/asr),
[OpenAI transcription pricing](https://costgoat.com/pricing/openai-transcription),
[Google Cloud STT pricing](https://cloud.google.com/speech-to-text/pricing),
[Azure Speech pricing](https://azure.microsoft.com/en-us/pricing/details/speech/).

Treat the numbers as of Sept 2026 and re-check vendor price pages before committing. The
whisper.cpp latency figures for the panels are estimates extrapolated from Pi 5 (Cortex-A76)
results. Classroom panels are typically Cortex-A53/A55 and will be slower, so benchmark with
`whisper-bench` on a real board before relying on them.

## Why not Android's `SpeechRecognizer`

`SpeechRecognizer` is only a client. The recognition itself comes from whatever
`RecognitionService` is installed, which on most devices is the Google app, backed by GMS.
On the GMS-less panels we target, `SpeechRecognizer.isRecognitionAvailable()` is
usually false. `createOnDeviceSpeechRecognizer` needs API 31, but our minSdk is 28, and it
still depends on a vendor-supplied service.
([SpeechRecognizer reference](https://developer.android.com/reference/android/speech/SpeechRecognizer))
We can still try it first at runtime when it is available, but we cannot depend on it.

## (i) Voice commands: Vosk with a grammar

- Commands are a small closed set ("next page", "new page", "undo", "pen red", "open 3D
  maths", "zoom in", "clear page", numbers 1–50). Pass them as a JSON grammar to
  `Recognizer(model, 16000f, grammarJson)`. Small Vosk models support runtime vocabulary
  restriction, which cuts the effective error rate far below the 49% dictation WER.
- Add `"[unk]"` to the grammar and reject low-confidence results. Show the heard text as a
  toast ("Heard: next page") so misfires are visible, and never auto-run destructive commands
  such as "clear page" without an Undo snackbar.
- Tap-to-talk only (no always-listening hotword). This avoids continuous mic use and the
  `microphone` foreground-service type, and keeps CPU free for ink.
- Load the model lazily on the first tap, keep it in memory while the board screen is open
  (~100–200 MB), and release it on `onTrimMemory`.
- Deliver the model as a one-time download from our backend (checksum-verified), not in
  the APK, so the APK stays small.
- Hindi commands: add `vosk-model-small-hi-0.22` (42 MB) only if schools ask for it.
- Alternative worth a spike if Vosk accuracy disappoints: **sherpa-onnx** keyword
  spotting/streaming models ([Apache-2.0, actively released](https://github.com/k2-fsa/sherpa-onnx)).
  It has a similar size profile and the same approval flag.
- Vosk's last tagged GitHub release is 0.3.50 (Apr 2024) and the last Maven Android AAR is
  0.3.47 (2023). The repo is still committed to (Aug 2026), but releases are slow. That is
  fine for a stable command grammar, but note it.

## (ii) Class-recording transcription: cloud via backend

- The board uploads the AAC file from Stage 3.4 (~29 MB/hour) to our backend, which calls
  the STT vendor asynchronously and stores a timestamped transcript. The transcript syncs to
  the replay timeline, so tapping a line seeks the recording. No API keys are stored on the
  board.
- Vendor choice:
  - **Sarvam** (default): ₹30/hour, Indian-first, handles Hindi-English code-mixing, which
    is common in Indian classrooms; diarization costs ₹45/hour.
  - **OpenAI `gpt-4o-mini-transcribe`** (fallback): $0.003/min ≈ ₹15–16/hour at current
    rates; good English, weaker on code-mix.
  - Google V2 batch (~$0.003/min, 24 h turnaround) and Azure batch ($0.18/h) are comparable
    in cost if we already have those accounts. Real-time tiers cost 3–5× more and are not
    needed for recordings.
- Rough cost: 6 periods/day × 200 school days × 45 min ≈ 900 h per board per year →
  **≈ ₹27,000/board/year at Sarvam's list price** if every period is transcribed. Make
  transcription opt-in per recording, or on demand when a student opens it.
- Keep the vendor behind a backend adapter so switching is a config change.
- On-device whisper.cpp stays a possible later **offline fallback** (base q5_1, 57 MB,
  overnight while charging), but only after approval and a benchmark on a real board.

## Privacy: audio leaving the device

- Commands (Vosk): audio stays on the board, nothing is stored, and nothing is sent. Say so in
  the UI ("Voice commands work offline").
- Transcription: classroom audio contains students' (children's) voices, which is personal
  data under the DPDP Act. The school is the Data Fiduciary, we are its Processor, and the STT
  vendor is **our sub-processor**. We need:
  - the vendor named in the school's notice and our processing agreement;
  - a vendor contract with **no training on our audio** and short retention. OpenAI's API
    does not train on API data by default; confirm Sarvam's and each vendor's current terms
    before signing;
  - Indian data residency where possible (Sarvam is India-hosted; ask the others);
  - deletion of the vendor copy once the transcript is returned, and deletion of transcripts
    together with the recording under the same retention rule;
  - no transcripts used for student profiling or analytics (Sec. 9 restrictions on tracking
    children).
  See `class-recording.md` → "Privacy under India's DPDP Act 2023".
