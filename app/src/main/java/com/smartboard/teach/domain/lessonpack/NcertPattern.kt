package com.smartboard.teach.domain.lessonpack

import com.smartboard.teach.domain.model.AssignmentQuestion
import com.smartboard.teach.domain.model.AssignmentSize

/**
 * The assignment's shape: the sections of an NCERT/CBSE exercise or question
 * paper, from objective to analytical.
 *
 * The school's generator only knows short, long and MCQ, so every section is
 * expressed as one of those (a true/false is an MCQ of True and False; a blank
 * is a short answer). Each question's text starts with its section tag, which
 * is how the board groups them back.
 */
object NcertPattern {

    data class Section(val letter: String, val title: String, val marks: Int, val type: String, val how: String)

    val SECTIONS = listOf(
        Section("A", "Multiple choice", 1, "mcq",
            "type mcq, exactly 4 options, one correct; answer_guide gives the correct option"),
        Section("B", "Fill in the blanks / True or false", 1, "mcq",
            "a blank as type short with ____ in the sentence, or a true/false as type mcq with options True and False"),
        Section("C", "Very short answer", 2, "short", "type short, answerable in one or two sentences"),
        Section("D", "Short answer", 3, "short", "type short, answerable in about 50 words or a short working"),
        Section("E", "Long answer", 5, "long", "type long, needing an explanation or full working"),
        Section("F", "HOTS / case-based", 4, "long",
            "type long: a short real-life case or situation, then a question that applies the idea"),
    )

    /** Questions per section, in [SECTIONS] order. */
    fun counts(size: AssignmentSize): List<Int> = when (size) {
        AssignmentSize.SHORT -> listOf(3, 2, 2, 1, 1, 1)
        AssignmentSize.STANDARD -> listOf(4, 3, 3, 2, 2, 1)
        AssignmentSize.LONG -> listOf(5, 4, 4, 3, 3, 1)
    }

    fun questionCount(size: AssignmentSize): Int = counts(size).sum()

    fun totalMarks(size: AssignmentSize): Int = counts(size).zip(SECTIONS).sumOf { (n, s) -> n * s.marks }

    /**
     * The generator's `instructions` (at most 1500 characters). It is the only
     * way to steer the school's existing assignment generator, so the whole
     * pattern lives here.
     */
    fun instructions(size: AssignmentSize, subject: String): String = buildString {
        append("Follow the NCERT/CBSE exercise pattern. Begin every question's text with its section tag ")
        append("in square brackets, e.g. [A], and keep the sections in this order:\n")
        counts(size).zip(SECTIONS).forEach { (n, s) ->
            append("[${s.letter}] ${s.title}: $n question${if (n == 1) "" else "s"}, ${s.marks} mark${if (s.marks == 1) "" else "s"} each, ${s.how}.\n")
        }
        append("Ask only about what the reference notes cover: they are what was taught in this lesson. ")
        append("Use the NCERT textbook's terms, notation and units. ")
        append("Answer guides must be exact: for a numerical give the working and the final answer with units. ")
        if (subject.contains("math", ignoreCase = true)) {
            append("Include numerical problems in the style of NCERT exercises. ")
        }
        append("No two questions may test the same fact.")
    }.take(1500)

    private val TAG = Regex("""^\s*\[\s*([A-Fa-f])\s*]\s*""")

    /**
     * Turns the generator's questions into the board's, grouped by section,
     * and notes what the teacher should check before publishing.
     */
    fun toQuestions(raw: List<RawQuestion>): List<AssignmentQuestion> =
        raw.mapIndexed { i, q ->
            val tag = TAG.find(q.text)
            val section = tag?.groupValues?.get(1)?.uppercase() ?: guessSection(q)
            val text = q.text.replace(TAG, "").trim()
            AssignmentQuestion(
                id = "q${i + 1}", section = section, text = text, type = q.type,
                options = q.options, marks = q.marks, answer = q.answer.trim(),
                problems = problems(section, q.type, text, q.options, q.answer, q.marks),
            )
        }.sortedBy { it.section }

    data class RawQuestion(val text: String, val type: String, val options: List<String>, val marks: Int, val answer: String)

    /** Re-checks one question after the teacher edits it. */
    fun recheck(q: AssignmentQuestion): AssignmentQuestion =
        q.copy(problems = problems(q.section, q.type, q.text, q.options, q.answer, q.marks))

    private fun guessSection(q: RawQuestion): String = when {
        q.type == "mcq" && q.options.map { it.lowercase() }.sorted() == listOf("false", "true") -> "B"
        q.type == "mcq" -> "A"
        "____" in q.text -> "B"
        q.type == "long" -> if (q.marks == 4) "F" else "E"
        q.marks >= 3 -> "D"
        else -> "C"
    }

    // Problem codes, turned into words by the screen.
    const val NO_ANSWER = "no_answer"
    const val MCQ_OPTIONS = "mcq_options"
    const val ANSWER_NOT_AN_OPTION = "answer_not_option"
    const val NO_BLANK = "no_blank"
    const val EMPTY_TEXT = "empty_text"

    private fun problems(section: String, type: String, text: String, options: List<String>, answer: String, marks: Int): List<String> {
        val out = mutableListOf<String>()
        if (text.isBlank()) out += EMPTY_TEXT
        if (answer.isBlank()) out += NO_ANSWER
        if (type == "mcq") {
            val trueFalse = options.map { it.trim().lowercase() }.sorted() == listOf("false", "true")
            if (options.size != 4 && !trueFalse) out += MCQ_OPTIONS
            // The answer guide should name an option: its letter or its text.
            val a = answer.trim().lowercase()
            val named = options.withIndex().any { (i, o) ->
                val letter = ('a' + i).toString()
                a == letter || a.startsWith("$letter)") || a.startsWith("($letter)") || a.startsWith("$letter.") ||
                    a.contains(o.trim().lowercase())
            }
            if (answer.isNotBlank() && options.isNotEmpty() && !named) out += ANSWER_NOT_AN_OPTION
        }
        if (section == "B" && type != "mcq" && "__" !in text) out += NO_BLANK
        return out
    }
}
