package com.smartboard.teach.feature.notes

import com.smartboard.teach.domain.model.AssignmentQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizLogicTest {

    private fun mcq(answer: String) = AssignmentQuestion(
        id = "q", section = "A", text = "?", type = "mcq",
        options = listOf("1/2", "3/4", "2/3", "1/4"), marks = 1, answer = answer,
    )

    @Test fun answerAsLetterInAnyForm() {
        listOf("b", "B", "(b)", "b)", "b.", "(b) 3/4", "b 3/4").forEach {
            assertEquals(it, 1, QuizLogic.correctOption(mcq(it)))
        }
    }

    @Test fun answerAsOptionText() = assertEquals(2, QuizLogic.correctOption(mcq("2/3")))

    @Test fun noMatchShowsTheAnswerInstead() {
        assertNull(QuizLogic.correctOption(mcq("seven")))
        assertNull(QuizLogic.correctOption(mcq("z")))
        assertNull(QuizLogic.correctOption(mcq("b").copy(options = emptyList())))
    }

    @Test fun sectionsComeInNcertOrder() {
        val qs = listOf("E", "A", "C", "A").mapIndexed { i, s -> mcq("a").copy(id = "$i", section = s) }
        assertEquals(listOf("A", "A", "C", "E"), QuizLogic.ordered(qs).map { it.section })
    }
}
