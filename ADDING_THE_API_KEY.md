# Adding the OpenAI API key

The snapshot → AI → notes pipeline is **fully built and wired**. The only
missing piece is the key. There is exactly **one place** to put it.

## The one place

Open `local.properties` in the project root (it is gitignored and never
committed) and set:

```properties
OPENAI_API_KEY=sk-proj-your-real-key-here
```

Then rebuild:

```powershell
.\gradlew.bat :app:installDebug
```

That is the whole change. No code edits, no other files.

## What happens without a key

The app builds and runs normally. Tapping the snapshot button:

1. flattens the board and **writes the JPEG to disk first**
2. records the note with status `FAILED_PENDING_RETRY`
3. shows a dialog headed **"Board saved"** explaining that the image is safe
   and only the summary is missing

Nothing is lost. Once a key is added, the **Retry** button on that note in the
Notes screen re-runs summarization against the snapshot already stored — so
even boards captured before the key existed can be turned into notes.

## What happens with a key

`OpenAiClient` posts the board image to `POST /v1/chat/completions` using
`gpt-4o-mini` with **structured outputs** (`json_schema`, `strict: true`), so
the model is forced to return the exact notes shape rather than prose that
needs parsing. The response is rendered to Markdown locally and written to:

```
filesDir/notes/{noteId}/note.md
filesDir/notes/{noteId}/snapshot.jpg
```

## Security — read before distributing the APK

`BuildConfig.OPENAI_API_KEY` is compiled into the APK and is **trivially
extractable**. R8 does not hide string constants; `apktool` reveals them in
minutes. Obfuscation, NDK storage and string-splitting are all theatre here.

For Phase 1 this is an accepted, deliberate trade-off. Mitigate it
operationally:

- use a **dedicated key** used for nothing else
- set a **hard monthly spend cap** and usage alerts on it
- rotate it if the APK is distributed beyond your own devices

**Phase 2 must remove the key from the device.** `NotesAiService` exists as an
interface for exactly this: implement it against a single endpoint on the ERP
backend that accepts the image and returns the notes JSON, with the key held
server-side and the teacher's session token as auth. Then change one line in
`di/RepositoryModule.kt`:

```kotlin
// Phase 1
abstract fun bindNotesAiService(impl: OpenAiClient): NotesAiService
// Phase 2
abstract fun bindNotesAiService(impl: ProxyNotesAiService): NotesAiService
```

Nothing above that interface changes.

## Changing the model

`data/remote/openai/NotesPrompt.kt` holds `DEFAULT_MODEL`, the system prompt,
and the JSON schema. `gpt-4o-mini` was chosen because it is vision-capable and
cheap enough to run every few minutes throughout a teaching day. If handwriting
transcription proves weak on your actual board, raise it to a stronger vision
model there — it is a one-line change.
