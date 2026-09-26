# Evaluation: Stage 3.4 "Record a class"

**Summary / recommendation.** Record the **canvas timeline + mic audio** (option b): a
timestamped log of stroke, page and board events plus AAC audio from `MediaRecorder`.
It needs no per-session system prompt, works fully offline, costs nothing, adds no
dependencies, uses about **30–35 MB per hour** (vs roughly 1–2 GB/hour for 1080p screen
capture), and replays inside the app as live ink. Its known gap is anything that is not board
ink: 3D Maths, the web search pane, video playback, and dialogs are not captured. We log
them as events ("opened 3D Maths: cube") so replay can show a placeholder card. Rendering to
MP4 for the student app should run **server-side** (our backend), not on the 2 GB boards.
Keep MediaProjection (option a) as a possible later "record whole screen" add-on for teachers
who need the 3D/web content. It uses only platform APIs, so it needs no approval, but it does
need the Android 14 consent and foreground-service handling described below. Nothing here
needs a paid SDK, a GPL licence, or a dependency over 5 MB.

## Comparison

| | (a) MediaProjection screen capture + mic | (b) Canvas timeline + mic (AAC), MP4 rendered later |
|---|---|---|
| Size per hour | ~0.9 GB at 2 Mbps H.264, ~1.8 GB at 4 Mbps (1080p), plus ~29 MB audio | ~29 MB audio (64 kbps mono) + ~2–6 MB event log ≈ **30–35 MB** |
| Offline | Yes (capture and encode on device) | Yes (capture); MP4 render needs backend, or on-device when idle |
| Licence | Android platform APIs (Apache 2.0 AOSP), no extra dependency | Platform APIs only (`MediaRecorder`, `MediaCodec`, `MediaMuxer`) |
| Cost | Free | Free on device; server render CPU for MP4 (small: vector replay at low fps) |
| Fidelity | Everything on screen: 3D Maths, web pane, video, UI chrome. No internal audio unless `AudioPlaybackCapture` is added | Pixel-perfect ink at any resolution, and each stroke can be searched and replayed. **Misses** video playback, 3D Maths viewport, web pane, and dialogs; it only logs that they were opened |
| Effort | Medium: consent flow, FGS, VirtualDisplay → `MediaCodec` surface, muxing audio+video, recovering a crashed MP4 | Medium: event schema, recorder hooks in the ink engine, replay player, and a renderer (server) |
| 2 GB RAM risk | **High**: continuous 1080p hardware encode alongside the ink engine; low-end SoCs drop frames or throttle, and ink latency can rise | **Low**: an append-only file plus one AAC encoder; the replay renderer only runs later |
| Per-session prompt | Yes, a system dialog every session (Android 14+) | Only the one-time `RECORD_AUDIO` runtime permission |

Size maths: AAC 64 kbps mono = 64,000 / 8 × 3600 ≈ **28.8 MB/hour**. Screen at N Mbps
= N × 450 MB/hour (2 Mbps ≈ 0.9 GB, 4 Mbps ≈ 1.8 GB, 8 Mbps ≈ 3.6 GB). Board content is
mostly static, so 2 Mbps at 1080p/15 fps is usually readable, but text-heavy frames can
still smear. Event-log estimate: about 2,000 strokes/hour × ~150 points × 12 bytes
(x, y, pressure floats, as in `Stroke.points`) ≈ 3.6 MB raw, which compresses well.

## Option (a): MediaProjection details

Rules for apps targeting Android 14+ (this app targets SDK 36):

- **Consent before every capture session.** Each `MediaProjection` instance may call
  `createVirtualDisplay` once, and the token cannot be cached or reused. Every class needs the
  system dialog, which a teacher mid-lesson will find disruptive. The dialog also offers
  "single app" vs "entire screen" on newer builds.
  ([Media projection guide](https://developer.android.com/media/grow/media-projection),
  [Android 14 behaviour changes](https://developer.android.com/about/versions/14/behavior-changes-14))
- **Foreground service type `mediaProjection`.** Declare
  `android:foregroundServiceType="mediaProjection"` plus `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_MEDIA_PROJECTION`. Without them the app gets a
  `MissingForegroundServiceTypeException`. Start the service after the user consents and
  before `getMediaProjection()`. Mic capture inside the same service also needs type
  `microphone` + `FOREGROUND_SERVICE_MICROPHONE`.
  ([FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required),
  [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types))
- Register a `MediaProjection.Callback` before capture and handle `onStop()`, because the
  system or user can end the projection at any time.
- Generic panel ROMs sometimes ship a broken or missing SystemUI capture dialog. Test on the
  actual panels before promising this feature.

## Option (b): timeline recording details

Event log: an append-only file per session, one record per line or length-prefixed binary,
flushed every few seconds so a crash loses at most seconds. Timestamps are milliseconds
from session start, taken from the same monotonic clock (`SystemClock.elapsedRealtime`)
that the audio start is anchored to.

- `strokeBegin(id, tool, style, containerId, t)`, `points(id, [x,y,p,dt]…)`,
  `strokeEnd(id, t)`. `Stroke` has no per-point time today. Add a `dt` column in the recorder
  only, not in the stored board model, so the ink engine stays unchanged.
- `erase(ids)`, `undo/redo`, `transform(ids, matrix)`, `pageAdded/pageSwitched(pageId)`,
  `viewport(x, y, zoom)` (throttled to about 10 Hz), `containerCreated/edited`.
- `external(kind = maths3d | webPane | video | pdf, label)` open/close. Replay shows a card
  such as "Teacher opened 3D Maths: cube". Option: save a single `PixelCopy` snapshot of the
  3D viewport on open. That still needs no MediaProjection, because it is our own window.
- Audio: `MediaRecorder` with `AudioSource.MIC`, `OutputFormat.MPEG_4`,
  `AudioEncoder.AAC`, 64 kbps, mono, 44.1 or 16 kHz. `MediaRecorder.pause()/resume()`
  exist from API 24, so pausing is covered on minSdk 28.
- Replay in app: seek = rebuild the board state up to t (keep a snapshot every 60 s for
  fast seeking), then play the events forward in sync with audio position.
- MP4 export: render the frames with the existing ink renderer into a `MediaCodec` input
  surface (H.264, 720p, 10–15 fps is plenty for ink) and mux with the AAC track through
  `MediaMuxer`. **Do this on the backend** (upload log + AAC, which is small; the server
  renders). On-device rendering is possible as a fallback, but only while the board is idle
  or charging, never during a class.

## Recording-state UI requirements

- **Always-visible indicator** while recording: a red dot + "REC 12:34" pill in the floating
  chrome, which cannot be hidden, including in full-screen/presentation mode. It must also
  appear when the board is mirrored or projected, because students need to see it.
- A **persistent notification** from the foreground service (required anyway), with
  Pause and Stop actions.
- Controls: Start → (Pause ⇄ Resume) → Stop. Stop asks for confirmation ("Save recording?
  Save / Discard"). A paused state shows an amber "PAUSED" pill, and no audio or events are
  written while paused. Log pause/resume as events so replay skips the gap.
- Auto-stop and save on: app process death recovery (finalise the partial file on next
  launch), board lock or sign-out, a class-end timer (for example the period length +
  10 min), or the storage floor (below).
- Microphone level meter (small) so teachers can see the mic is working. This avoids the
  most common complaint about silent recordings.
- After stop: show the length, size, and "Uploading… / Queued (offline) / Uploaded", and
  let the teacher delete it before upload.

## Low-storage thresholds

Check `StatFs(filesDir).availableBytes` at start and every 30 s.

| Threshold | (b) timeline + AAC (~35 MB/h) | (a) screen 1080p @ 2 Mbps (~0.95 GB/h) |
|---|---|---|
| Block start | < 300 MB free | < 3 GB free |
| Warn at start ("space for about N min") | < 1 GB free | < 6 GB free |
| Warn during ("≈ 15 min left") | < 150 MB free | < 500 MB free |
| Auto-stop and finalise | < 100 MB free | < 300 MB free |

The auto-stop floor leaves room for `MediaMuxer`/`MediaRecorder` to write the `moov` atom
and for Room to keep working. A recording stopped at the floor must still be playable.
Uploaded recordings are deleted locally after the server confirms, which keeps boards from
filling up over a term.

## Privacy under India's DPDP Act 2023 (and the DPDP Rules 2025)

*Practical engineering notes, not legal advice. Get the school contract and notice text
reviewed by counsel.*

Timing: the DPDP Rules 2025 were notified on **13 Nov 2025**. Most substantive obligations
(notice, consent, children's data, security, processor duties) apply from **13 May 2027**,
the 18-month mark. Build for them now anyway.
([SAM & Co.](https://www.amsshardul.com/insight/enforcement-of-the-dpdp-act-and-notification-of-the-dpdp-rules/),
[DPDP Rules timeline](https://protectcomply.com/blog/dpdp-rules-2025-timeline))

- **Students' voices are personal data.** A class recording captures identifiable voices
  and names, so the whole file is personal data, much of it children's.
- **Who is who.** The **school is the Data Fiduciary**: it decides to record and why. **We
  (the company) are the Data Processor** for recordings made on the school's instruction
  and delivered to its students. That holds only while we process on the school's behalf.
  If we reuse recordings for our own purposes (for example model training, analytics or
  marketing), we become a fiduciary for that use, which we should not do. Put this in a
  written data-processing agreement with each school (Sec. 8(2) requires a contract to
  engage a processor).
- **Children's data and verifiable parental consent.** Sec. 9 and Rule 10 require
  verifiable parental consent before processing a child's (under 18) data, and forbid
  tracking/behavioural monitoring and targeted ads aimed at children. The **Fourth Schedule
  exempts educational institutions** for processing that is limited to their educational
  activities and student safety, but the exemption is narrow and purpose-bound.
  ([Rule 10](https://dpdprules.org/rules/10),
  [Fourth Schedule](https://dpdprules.org/rules/fourth-schedule),
  [Storyboard18 on school exemptions](https://www.storyboard18.com/digital/dpdp-rules-carve-out-key-exemptions-for-healthcare-providers-schools-and-childcare-services-processing-childrens-data-84208.htm))
  Practical stance: recording is off by default per school, and a school admin enables it
  only after confirming it has a parental consent or notice mechanism (for example a clause
  in the enrolment form). We never use recordings for profiling, scoring or ads.
- **Notice.** Rule 3 requires a clear, standalone, itemised notice. Give the school a
  template: what is recorded (teacher's voice, classroom audio, board content), why (revision
  for enrolled students), who can access it (the class's students and teachers), how long it
  is kept, and how to request deletion.
- **Purpose limitation.** Use recordings only for revision by that class. Do not reuse them
  for transcription-based analytics or AI training unless there is a separate, explicit
  purpose and consent. Transcription (Stage 4.1) sends audio to a cloud processor, which is
  another processing step that has to be named in the notice.
- **Retention and deletion.** Sec. 8(7) says to erase once the purpose is served. Default:
  keep for the academic year (configurable by the school, for example 90 days to 1 year),
  then auto-delete on the server and in the student app. Delete the local board copy after
  upload. Support deleting an individual recording on request by a teacher, admin or parent,
  and pass deletion through to any sub-processor (such as the STT vendor). Keep access logs
  for one year for security (Rule 6).
- **Processor obligations on upload to the student app.** Use TLS in transit and encryption
  at rest. Give access only to authenticated students enrolled in that class, through
  short-lived signed URLs rather than public links. Prefer streaming to downloads, keep audit
  logs, report breaches to the school promptly so it can notify the Board and affected
  people (Rule 7), and list sub-processors (hosting, STT) to the school. Host in India where
  practical.
- **Visible recording indicator.** Keep the REC pill and notification above. Also consider
  a short spoken or visual "This class is being recorded" banner when recording starts, and
  a "stop recording" control any teacher can reach.
- **Minimise.** Record audio from the teacher's mic only, with no camera. Option (b) also
  stores less than a screen capture would (no incidental notifications, no web pages).
