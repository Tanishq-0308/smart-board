# Smart Board: Features

A teaching app for interactive smart boards: large landscape Android panels, often sideloaded and often without Google Play Services. A teacher can walk up and write straight away without signing in. Signing in adds their classes, attendance and study material.

- **Phase 1 (current):** everything runs on the board, using local data.
- **Phase 2 (upcoming):** connects to the school's ERP/LMS.

Status key: ✅ available now · 🔜 planned · 💡 idea, not yet scheduled

---

## Access levels

| | Guest | Signed-in teacher |
|---|---|---|
| Whiteboard | ✅ | ✅ |
| 3D Maths | ✅ | ✅ |
| Notes (AI snapshot notes) | ✅ | ✅ |
| Settings | ✅ | ✅ |
| My Classes | 🔒 | ✅ |
| Attendance | 🔒 | ✅ |
| Study Material | 🔒 | ✅ |

In the sidebar, locked items show dimmed with a lock icon. Tapping one goes to Sign In.

---

## 1. Whiteboard ✅

### Writing and drawing
- **Pen types.** Each pen type remembers its own colour and thickness.
  - **Pen:** an even line that responds to pressure.
  - **Marker:** thick and flat.
  - **Highlighter:** see-through.
  - **Fountain:** a strong pressure response.
  - **Brush:** soft, for shading.
  - **Text pen:** handwriting turns into typed text a moment after you stop. The English model downloads on first use, then works offline.
  - **Shape pen:** rough circles, rectangles, lines and polygons snap to clean shapes. Works offline. Only the Shape pen snaps; every other pen leaves ink exactly as drawn.
- **Colours.** 12 preset colours, plus a custom colour picker (hue bar with a saturation/brightness square).
  - Picked colours are saved under **Extras** (the last 5, newest first).
  - Hold an Extras colour to remove it.
- **Thickness slider** with a live stroke preview.
- **Eraser** with an adjustable size. The stylus's eraser end also erases (can be turned off in Settings).
- **Palm rejection.** The stylus takes priority over fingers, only one contact writes at a time, and a quick second touch (a palm) is ignored. Settings also has a Stylus-only mode.

### Shapes
- **Lines:** line, dashed line, arrow, dashed arrow.
- **Triangles:** triangle, isosceles, right-angled.
- **Quadrilaterals:** diamond, parallelogram, trapezoid, rectangle, rounded rectangle.
- **Round shapes:** circle, ellipse, semicircle.
- **Polygons:** pentagon, hexagon, star.
- **3D figures** with dashed hidden edges: cube, pyramid, prism, tetrahedron, cylinder, cone, sphere.

### Select and edit
- Select with a marquee or by tapping. Works on ink, text, images, tables, videos, mindmaps and the page background.
- **Move, resize, rotate, duplicate, delete.**
  - Images and videos keep their proportions when resized.
  - Tables stretch freely, and the writing inside them scales too.
  - Containers (images, tables, videos) cannot be rotated.
- **Look up** a selected region with AI. The AI explains it (text, equation, diagram, chemistry, geometry) and suggests related terms.
  - The result panel offers **Search with Lens**, **Search the web** and **Save to notes**. Save to notes keeps the explanation, what was read off the board, the related terms and the cropped image as a note in Notes.
  - **Search with Lens** works even without an AI key. It is hidden when no app on the board can receive an image, and **Search the web** is hidden when there is no browser.
- **Save a selection** as a PNG or PDF.
- **Export a whole lesson** as one PDF (Insert → Lessons → Export as PDF): every page, including imported PDF pages and pictures under the ink, one PDF page each. Large lessons are exported one page at a time, so they don't run a 2 GB board out of memory.
- **Undo / redo** for every action, including moving and resizing objects.

### Insert
- **Image**, from a file or from the in-app web search. Long-press an image in the search results to place it.
- **Table.** Pick up to 10 columns on a size grid, add rows or columns from any edge, and write inside cells.
- **Geometry instruments:**
  - **Ruler** and **set squares** (45° and 30/60/90): draw along the edge for straight lines.
  - **Protractor** for measuring angles.
  - **Compass:** open it to a spread, then sweep an arc.
  - Several can be on the board at once. They keep their real-world size at any zoom and never appear in exports.
- **Text box:** type directly on the board.
- **Mindmap:** add child and sibling nodes, delete branches. It re-lays itself out automatically.
- **PDF:** import a whole PDF as board pages.
- **Video:** placed as a poster frame. It plays full screen with a scrubber, and **Capture this frame** puts the current frame on the board.
- **Timer:** a movable HH:MM:SS timer with play/pause, reset, full screen and an on-screen number pad.
- **Background:** paper colour, and grid styles (none, thin, mix, square, dotted, lined, rangoli). You can also use an image or a PDF page as the backdrop.
- **Lessons:** New, Open, Save, Save as and Delete named lessons, stored on the board.

### Pages and view
- Infinite canvas with pan and pinch-zoom, plus zoom buttons. Tap the percentage to go back to 100%.
- **Pages:** add, delete, previous and next.
- **Split view:** up to 6 panes, each showing and editing a different page of the same lesson.
- A live clock sits in the top-right corner.

### Tools drawer (right edge)
- **Web:** web search docked beside the board. Hidden on boards without a working WebView.
- **Snapshot → AI notes:** turns the whole board into structured lesson notes.
- **3D Maths:** opens the 3D Maths screen (see section 2).

---

## 2. 3D Maths ✅

This is the HTML/three.js prototype rebuilt natively in the app. The 3D view is drawn by the app itself, so it works on boards with an old or missing WebView.

- **Draw a shape** on a chalk grid (1 box = 1 cm). The app recognises circles, squares and rectangles (including tilted ones), triangles (equilateral, isosceles, right-angled, scalene), rhombuses, parallelograms, trapeziums, polygons up to 8 sides, free shapes, and open profile lines.
- **Turn it into a solid:** cube or cuboid, prism, pyramid, cylinder, cone or sphere. Revolving the shape around the axis gives a torus, a vase or a bowl.
- **Presets:** Cube, Cuboid, Cylinder, Cone, Sphere, Pyramid, Tri prism, Vase.
- **Multi mode:** draw several shapes and see them all in 3D together. Tap a shape to select it.
- **3D view:** rotate in every direction, including from below. Pinch to zoom. Also available:
  - toggles for auto-rotate, wireframe, transparent and labels
  - Reset view
  - a **Grow** animation that shows the solid forming
- **Height slider**, with dimension labels drawn on the solid.
- **Worked maths:** volume, curved, lateral and total surface area, slant height and diagonal, each with the formula and the substituted numbers.
  - Also covers Euler's formula (F + V − E = 2), the disk method, and Pappus's theorem.
  - π can be switched between 3.14 and 22⁄7.
- **Insert on board:** puts a snapshot of the solid on the whiteboard, with a caption giving its name, volume and total surface area.
- Opens from the sidebar or from the right-edge tools drawer.

---

## 3. Notes ✅
- AI-generated notes from a board snapshot:
  - title and summary
  - topics and key points
  - definitions and formulas
  - follow-up questions
- Notes list with delete, and **Retry** for notes whose AI request failed. The snapshot is saved before the request is sent, so it isn't lost if the request fails.
- Formatted note view (headings, bullets, code, bold).
- Export a note as Markdown, for example to a USB drive.

## 4. My Classes 🔒 ✅
- A class list, and a class detail page listing its students.
- **Take attendance** straight from a class.

## 5. Attendance 🔒 ✅
- Large **P / A / L** (Present / Absent / Late) buttons for each student. Status is always shown as a letter as well as a colour.
- Previous and next day, **Mark all present**, **Clear all**.

## 6. Study Material 🔒 ✅
- A list of materials, and a PDF viewer with page navigation.
- **Annotate on board:** opens the current page as a whiteboard background.

## 7. Sign in ✅
- Username and password sign-in, currently against local demo accounts.

## 8. Settings ✅
- **Pen and touch:** Stylus only, Pressure sensitivity, Pen eraser button, Pointer debug overlay (for setting up new hardware).
- **Device diagnostics** (for installers): Android version, memory, storage, screen, declared touch points, stylus, Google Play Services, WebView and network, each marked OK or warning. A **touch test pad** shows how many contacts the panel really tracks, which input types it sends (finger, stylus, eraser) and whether pressure varies.
- **Display:** 24-hour clock.
- **AI notes:** shows whether an AI key is configured and which model is used.
- **Storage:** Clear board data. Removes pages and backgrounds; keeps notes and classes.
- **About:** app version.

---

## Upcoming

### 🔜 Phase 2: school ERP/LMS integration
The app already has a single place where each on-device data source will be swapped for the school's system (`di/RepositoryModule.kt`). No screen changes should be needed.
- **Sign in with the school ERP**, using tokens instead of local demo accounts.
- **Class rosters** fetched from the ERP. The refresh action already exists but does nothing yet.
- **Attendance sync** to the ERP. Each record already stores whether it has been synced.
- **Study material** downloaded from the LMS instead of bundled files.
- **Move the OpenAI key behind a school server.** It currently ships inside the app, where it can be extracted. It is mitigated by a dedicated, spend-capped key.
- Boards, lessons and notes stay on the device.

### 💡 Ideas and gaps, not yet scheduled
- **Lesson sharing:** cloud upload, scan, email. These were left out on purpose for Phase 1.
- **Multi-writer touch.** Palm rejection currently allows one writer at a time.
- **3D Maths:**
  - a proper depth-buffer renderer (OpenGL ES), if the current shortcut shows glitches on deep concave solids
  - optional Hindi names for solids
  - the rest of the prototype's Hinglish teaching text
- **Board clear button.** The action is wired up in code but has no button.

## Compatibility
Runs on Android 9 and later, with or without Google Play Services. Tested on a 2 GB Android 9 image without Play Services. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for what was tested and how features degrade.

## Known issues
- **Pages losing content (fixed, still being watched).** Two causes were found and fixed:
  - **Save as** moved the original lesson's ink and text into the copy, leaving tables and images behind.
  - **Split view** could open one page in two panes, and the later save overwrote the other's ink.

  Every page save and load is now logged under the `BoardPersist` tag. A save that removes more than half of a page's ink logs a warning with a stack trace, so any remaining cause will leave evidence.
