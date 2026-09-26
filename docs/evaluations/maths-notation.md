# Evaluation: Stage 5.1 Handwritten maths and chemistry notation

**Summary / recommendation.** For **recognition**, send the selected ink region (rendered to
PNG, plus optionally the stroke list) to a **vision LLM via our backend** and ask for LaTeX
(maths) or mhchem `\ce{}` (chemistry). It costs nothing on the board, adds no APK size,
handles messy handwriting and mixed text, and fits the existing backend-only rule. The
downside is that it needs the network, which is acceptable given reliable school Wi-Fi.
**MyScript iink** is the only mature offline option, but it is **paid, with per-device
licensing and no public price** ⚠ and must not be adopted without owner approval. The free
offline models (TexTeller, Pix2Text) are Python/PyTorch-sized and are really **backend**
candidates, not on-board ones. For **rendering**, use **AndroidMath** (`MTMathView`,
gregcockroft). It is **MIT**, released v1.1.0 in Dec 2025 (including the 16 KB page-alignment
fix), has no WebView, and adds ~4.4 MB for all ABIs, roughly 1.5–2 MB after ABI splits and
trimming fonts. It has **no mhchem**, so the backend converts `\ce{…}` to plain LaTeX with the
Apache-2.0 **mhchemParser** before sending it to the board. ⚠ **Licence red flag:** JLaTeXMath
and its Android port (noties/jlatexmath-android) are **GPL-2.0-or-later with a
linking/classpath exception**. The exception probably allows use in a closed-source app, but
it is still a GPL-family licence and **needs the owner's approval**. The Android port is also
archived (last release 2020), and its optional Greek-font artifact is plain GPL-2.

## Recognition

| | Vision LLM via backend | MyScript iink SDK (native, offline) | Free offline models (TexTeller / Pix2Text) |
|---|---|---|---|
| Output | LaTeX, mhchem `\ce{}`, plus plain text around it | LaTeX / MathML; interactive math editing | LaTeX (formulas); Pix2Text also does mixed text + formula |
| Offline | No | **Yes** (licence check-in needed: works 30 days offline, then recognition stops) | Only if hosted by us: they are server-sized models, not on-board |
| Licence | Vendor API terms; no code on board | **Proprietary, paid** ⚠ | TexTeller **Apache-2.0**; Pix2Text **MIT** (check model-weight licences separately) |
| Cost | Per call; a small crop is roughly ₹0.1–1 per recognition depending on model (estimate) | **Not public**: per activated device, via sales quote. Cloud variant: $10 per 1,000 requests beyond the free 2,000/month | Free software; our server CPU/GPU |
| Accuracy | Very good on clean and moderately messy handwriting; handles chemistry, matrices and context. Can hallucinate, so always show a preview for the teacher to confirm | Best-in-class for online ink (uses stroke order/timing); math is strong; chemistry is limited | Good on printed formulas, decent on handwritten (TexTeller trains on handwritten sets); no chemistry |
| Size on board | 0 | Native libs + recognition resources (tens of MB; exact size from SDK) ⚠ >5 MB | 0 (if backend) |
| Effort | Low: crop → PNG → backend prompt → LaTeX → render | Medium–high: SDK integration, licence certificate, its own editor model | Medium: host a Python service and GPU/CPU tuning |
| Maintenance | Model upgrades are free on our side | Active (iink 4.3, Jan 2026) | TexTeller last push Aug 2025; Pix2Text v1.1.7 Aug 2026 |

Sources: [MyScript request pricing (cloud)](https://developer-support.myscript.com/support/discussions/topics/16000030978),
[MyScript offline/licensing: per device, 30-day offline window](https://developer-support.myscript.com/support/discussions/topics/16000030338/page/last),
[MyScript pricing page](https://www.myscript.com/en/pricing),
[iink SDK 3.0 Math Recognizer](https://www.myscript.com/blog/iink-sdk-3-0-handwriting-recognition/),
[TexTeller (Apache-2.0)](https://github.com/OleehyO/TexTeller),
[Pix2Text (MIT)](https://github.com/breezedeus/Pix2Text).

### Recognition design notes

- Input: the teacher lasso-selects ink, and we render the selection to a white-background
  PNG (about 1024 px on the long side), because the ink engine already renders strokes in
  world coordinates. Optionally attach the stroke list (x, y, t) as JSON for extra signal.
- Prompt the backend to return JSON:
  `{kind: "math"|"chem"|"text", latex: "...", confidence: 0..1}`. Chemistry comes back as
  `\ce{…}`, which the backend passes through mhchemParser (below) before returning it.
- The board shows the rendered result next to the ink with **Accept / Edit / Cancel**. Never
  replace ink silently. Keep the original strokes (hidden) so the change can be undone.
- Privacy: board ink is teacher content, not student personal data, so it is low risk, but
  still disable vendor training in the vendor contract.
- MyScript: ⚠ do not adopt without owner approval. If offline recognition becomes a hard
  requirement, request a quote for N devices and note that the licence server check-in
  conflicts with fully air-gapped boards.
- Not considered: Google ML Kit Digital Ink recognises characters/shapes, not maths
  layout, and the Mathpix API (paid, cloud) offers nothing over a vision LLM for our flow.

## Rendering (LaTeX → pixels on the board, no WebView)

| | **AndroidMath** (`MTMathView`, gregcockroft) | **jlatexmath-android** (noties) | **JLaTeXMath** (opencollab, upstream) | MathJax/KaTeX Android wrappers (e.g. jianzhongli/MathView) |
|---|---|---|---|---|
| Licence | **MIT**; bundles FreeType (FTL, BSD-style with credit, or GPL-2; we choose FTL) and fonts under OFL / GUST Font Licence | ⚠ **GPL-2.0-or-later + linking exception** (same text as upstream); Greek-font artifact under plain GPL-2 | ⚠ **GPL-2.0-or-later + linking exception** (exception added in 1.0.4) | Apache-2.0 |
| Last release | **v1.1.0, 22 Dec 2025** (JitPack) | v0.2.0, Apr 2020; **repo archived** | 1.0.7, Apr 2018 (Maven Central); last push Feb 2022 | Last push Jul 2022 |
| mhchem `\ce` | No (iosMath port) → convert on backend | No | No ([open request, issue #40](https://github.com/opencollab/jlatexmath/issues/40)) | Yes via MathJax extension |
| LaTeX coverage | Core maths: fractions, roots, sub/sup, big operators, matrices/cases, Greek, accents. Test `\xrightarrow`, `\overset` and similar for mhchem output | Broad: amsmath, amssymb, arrays, colour, `\newcommand` | Same as port | Full |
| APK size | AAR **4.4 MB** (native FreeType for all ABIs + 3 OTF fonts ~1.8 MB). With ABI split and one font, ~1.5–2 MB | AAR 0.65 MB (+0.18 MB Greek fonts) | JAR 0.67 MB, but needs `java.awt`, which **does not exist on Android** | Tiny, but **needs WebView** ❌ |
| Engine | Kotlin + JNI FreeType, draws on Canvas | Java, fake `java.awt` over Android Canvas | Java AWT | WebView + JS |
| Fit for us | ✅ | ⚠ approval + unmaintained | ❌ not usable directly on Android | ❌ violates the no-WebView constraint |

Sources: [AndroidMath (MIT)](https://github.com/gregcockroft/AndroidMath),
[AndroidMath release v1.1.0](https://github.com/gregcockroft/AndroidMath/releases/tag/v1.1.0),
[jlatexmath-android (archived)](https://github.com/noties/jlatexmath-android)
and [its README licence note](https://github.com/noties/jlatexmath-android/blob/android/README-ORIGINAL.md),
[JLaTeXMath LICENSE (GPL-2.0+ with exception)](https://github.com/opencollab/jlatexmath/blob/master/LICENSE),
[JLaTeXMath on Maven Central](https://search.maven.org/artifact/org.scilab.forge/jlatexmath),
[SPDX: GPL-2.0-with-classpath-exception](https://spdx.org/licenses/GPL-2.0-with-classpath-exception.html),
[jianzhongli/MathView](https://github.com/jianzhongli/MathView),
[mhchemParser (Apache-2.0)](https://www.npmjs.com/package/mhchemparser).
Sizes were measured from the published Maven Central and JitPack artifacts (Sept 2026).

### ⚠ JLaTeXMath licence, in plain words

JLaTeXMath's LICENSE is **GPL v2 "or (at your option) any later version"**, followed by the
standard *classpath-style linking exception*: we may link the **unmodified** library into
an app under our own terms, as long as we meet the GPL for the library itself (ship its
licence, offer its source). If we **modify** the library, and the noties port is already a
modified fork, the exception is optional for our modified version and we must keep it
attached. The separate **Greek font** artifact is plain GPL-2 with no exception. This is
very likely usable in a closed-source APK, but it is a GPL-family licence, so per project
rules it **needs the owner's explicit approval** (and ideally legal sign-off) before we take
the dependency. Given that the Android port is archived and unmaintained since 2020, it
is not worth the approval request unless AndroidMath fails on coverage.

### Recommended rendering pipeline

1. The backend returns LaTeX. For chemistry, the backend runs **mhchemParser.toTex()** on
   the `\ce{…}` string and sends plain LaTeX (`\mathrm{…}`, arrows, sub/superscripts) to
   the board, so the board never needs mhchem support.
2. The board renders with **AndroidMath** `MTMathView`, or draws its display list into a
   bitmap so the formula can be placed on the canvas as a board object. Store the LaTeX
   source on the object and re-render when zoom changes, so it stays sharp on the infinite
   canvas.
3. If AndroidMath rejects a construct (it throws `MathDisplayException` on parse errors),
   ask the backend for a **simplified-LaTeX retry**. As a last resort, the backend renders
   SVG with MathJax (full mhchem) and the board draws it. That would need an SVG renderer
   such as AndroidSVG (Apache-2.0, small, but last release 1.4 in 2019), so treat it as a
   fallback and do not add it by default.
4. Housekeeping for AndroidMath:
   - Keep only `arm64-v8a` and `armeabi-v7a` ABIs. Ship one font (Latin Modern Math,
     ~0.7 MB) unless a second style is wanted.
   - Add FreeType (FTL) and font licence credits to the About/licences screen.
   - It is published on **JitPack only** (not Maven Central). Either accept a JitPack
     repository in Gradle or vendor the MIT source into a module (it is small), which also
     protects against JitPack outages.
   - v1.1.0 fixed the 16 KB page-size alignment of its native `.so` files, which matters
     for newer Android builds. Past issues report "Error initializing FreeType" on some
     devices, so smoke-test on the actual panels.
