package com.smartboard.teach.domain.lessonpack

import com.smartboard.teach.domain.model.AssignmentSize
import com.smartboard.teach.domain.model.BackgroundKind
import com.smartboard.teach.domain.model.BoardBackground
import com.smartboard.teach.domain.model.BoardPage
import com.smartboard.teach.domain.model.Container
import com.smartboard.teach.domain.model.ContainerCell
import com.smartboard.teach.domain.model.ContainerKind
import com.smartboard.teach.domain.model.DrawTool
import com.smartboard.teach.domain.model.LessonNotes
import com.smartboard.teach.domain.model.LessonPart
import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.Stroke
import com.smartboard.teach.domain.model.StrokeStyle
import com.smartboard.teach.domain.model.TimetableSlot
import com.smartboard.teach.domain.repository.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class LessonPackLogicTest {

    private fun stroke(containerId: String? = null) = Stroke(
        id = "s", tool = DrawTool.PEN, style = StrokeStyle(0xFF000000.toInt(), 4f),
        points = floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f), containerId = containerId,
    )

    private fun pdfPage(id: String) = Container(
        id = id, kind = ContainerKind.IMAGE, x = 0f, y = 0f,
        cells = listOf(ContainerCell(0f, 0f, 100f, 140f)), mediaPath = "$id.jpg", label = "Chapter 4, page ${id.drop(1)}",
    )

    private fun page(id: String, strokes: List<Stroke> = emptyList(), containers: List<Container> = emptyList(),
                     background: BoardBackground? = null) =
        PageContent(BoardPage(id, "lesson", 0, 100, 100), strokes, emptyList(), background, containers)

    // --- What counts as taught ---

    @Test fun onlyInkedOrWatchedPdfPagesAreSent() {
        // A 100-page chapter: ink on p1, p2 watched 25 s, p3 glanced at for 6 s, the rest never seen.
        val chapter = (1..100).map { pdfPage("p$it") }
        val board = page("b1", strokes = listOf(stroke("p1")), containers = chapter)
        val plan = LessonPartPlanner.plan(listOf(board), mapOf("p2" to 25_000L, "p3" to 6_000L))

        assertEquals(listOf("p1", "p2"), plan.parts.map { it.containerId })
        assertEquals(0, plan.leftOut)
        assertTrue(plan.parts.first().inked)
    }

    @Test fun freeInkIsOneBoardPartPerPage() {
        val plan = LessonPartPlanner.plan(
            listOf(page("b1", strokes = listOf(stroke(), stroke())), page("b2"), page("b3", strokes = listOf(stroke()))),
            emptyMap(),
        )
        assertEquals(listOf(1, 3), plan.parts.map { it.pageNumber })
        assertTrue(plan.parts.all { it.kind == LessonPartPlanner.Kind.BOARD })
    }

    @Test fun aPdfBackdropCountsWhenInkedOrWatched() {
        val bg = BoardBackground("bg", BackgroundKind.PDF_PAGE, "/x/Science_Ch_2.pdf", pdfPageIndex = 4, renderedPath = "")
        val watched = LessonPartPlanner.plan(listOf(page("b1", background = bg)), mapOf("b1" to 30_000L)).parts.single()
        assertTrue(watched.withBackground)
        assertEquals("Science Ch 2" to 5, watched.backdropPdf)

        val skipped = LessonPartPlanner.plan(listOf(page("b1", background = bg)), mapOf("b1" to 5_000L))
        assertTrue(skipped.parts.isEmpty())
    }

    @Test fun capKeepsInkFirstThenLongestWatched() {
        val pages = (1..12).map { pdfPage("p$it") }
        val inked = listOf(stroke("p11"), stroke("p12"))
        val dwell = (1..10).associate { "p$it" to (20_000L + it * 1_000L) } // p10 longest
        val plan = LessonPartPlanner.plan(listOf(page("b1", strokes = inked, containers = pages)), dwell)

        assertEquals(LessonPartPlanner.MAX_PARTS, plan.parts.size)
        assertEquals(4, plan.leftOut)
        val kept = plan.parts.map { it.containerId }.toSet()
        assertTrue("p11" in kept && "p12" in kept)
        assertTrue(listOf("p1", "p2", "p3", "p4").none { it in kept }) // the shortest watched go
    }

    // --- NCERT pattern ---

    @Test fun sizesAddUp() {
        assertEquals(15, NcertPattern.questionCount(AssignmentSize.STANDARD))
        assertEquals(33, NcertPattern.totalMarks(AssignmentSize.STANDARD))
        AssignmentSize.entries.forEach { size ->
            assertTrue(NcertPattern.instructions(size, "Mathematics").length <= 1500)
        }
    }

    @Test fun questionsAreGroupedBySectionTagAndChecked() {
        val qs = NcertPattern.toQuestions(listOf(
            NcertPattern.RawQuestion("[C] Define radius.", "short", emptyList(), 2, "Centre to circle"),
            NcertPattern.RawQuestion("[A] Area of a circle?", "mcq", listOf("pi r", "pi r^2", "2 pi r", "r^2"), 1, "pi r^2"),
            NcertPattern.RawQuestion("[B] True or False: d = r.", "mcq", listOf("True", "False"), 1, "False"),
            NcertPattern.RawQuestion("[A] Pick one", "mcq", listOf("x", "y"), 1, "z"),
            NcertPattern.RawQuestion("[B] The circumference is 2 pi r.", "short", emptyList(), 1, ""),
        ))
        assertEquals(listOf("A", "A", "B", "B", "C"), qs.map { it.section })
        assertEquals("Area of a circle?", qs[0].text)
        assertTrue(qs[0].problems.isEmpty())
        assertTrue(qs[2].problems.isEmpty()) // true/false is a valid two-option MCQ
        assertTrue(NcertPattern.MCQ_OPTIONS in qs[1].problems)
        assertTrue(NcertPattern.ANSWER_NOT_AN_OPTION in qs[1].problems)
        assertTrue(NcertPattern.NO_ANSWER in qs[3].problems)
        assertTrue(NcertPattern.NO_BLANK in qs[3].problems)
    }

    @Test fun anAnswerNamingTheOptionLetterCounts() {
        val q = NcertPattern.toQuestions(listOf(
            NcertPattern.RawQuestion("[A] Which?", "mcq", listOf("one", "two", "three", "four"), 1, "(b) two"),
        )).single()
        assertTrue(q.problems.isEmpty())
    }

    // --- Notes merge ---

    @Test fun mergeKeepsSourcesAndDropsRepeats() {
        val board = LessonNotes("Area of a Circle", "Derived A = pi r^2.", topics = listOf("Area"), formulas = listOf("A = pi r^2"))
        val book = LessonNotes("Mensuration", "Circumference 2 pi r.", topics = listOf("area", "Circumference"),
            formulas = listOf("A = pi r ^2", "C = 2 pi r"))
        val merged = NotesMerge.merge(listOf(LessonPart("Board, page 1", "a", board), LessonPart("Chapter 11, page 2", "b", book)))

        assertEquals("Area of a Circle", merged.title)
        assertEquals("Board, page 1: Derived A = pi r^2.\n\nChapter 11, page 2: Circumference 2 pi r.", merged.summary)
        assertEquals(listOf("Area", "Circumference"), merged.topics)
        assertEquals(listOf("A = pi r^2", "C = 2 pi r"), merged.formulas)
    }

    // --- Class preselection ---

    @Test fun preselectsThePeriodBeingTaught() {
        val monday = LocalDateTime.of(2026, 10, 5, 9, 10) // a Monday
        val slots = listOf(
            TimetableSlot("6A", "maths", "Mathematics", 0, LocalTime.of(8, 45), LocalTime.of(9, 30)),
            TimetableSlot("7B", "sci", "Science", 0, LocalTime.of(9, 30), LocalTime.of(10, 15)),
            TimetableSlot("6A", "maths", "Mathematics", 1, LocalTime.of(9, 0), LocalTime.of(9, 45)),
        )
        assertEquals("6A", PackDefaults.current(slots, monday)?.classId)
        // Just after the bell, the period that ended still counts.
        assertEquals("6A", PackDefaults.current(slots.take(1), monday.withHour(9).withMinute(40))?.classId)
        assertEquals(null, PackDefaults.current(slots, monday.withHour(12)))
    }

    @Test fun subjectsFallBackToTheClassListWithoutIds() {
        val c = SchoolClass("10A", "10", "A", "Mathematics, Science", "t")
        assertEquals(listOf(PackDefaults.Subject(null, "Mathematics"), PackDefaults.Subject(null, "Science")),
            PackDefaults.subjectsOf(c, emptyList()))
    }
}
