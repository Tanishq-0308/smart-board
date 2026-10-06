package com.smartboard.teach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class InkChoiceTest {

    @Test fun mostConfidentScoreWins() =
        assertEquals("Force", InkChoice.best(listOf("फोर्स" to 4.2f, "Force" to 1.1f)))

    @Test fun withoutScoresTheLanguagesOwnModelWins() =
        assertEquals("बल", InkChoice.best(listOf("बल" to null, "ba" to null)))

    @Test fun anEmptyReadingNeverWins() =
        assertEquals("Force", InkChoice.best(listOf("" to 0.1f, "Force" to 3f)))

    @Test fun nothingReadableIsEmpty() = assertEquals("", InkChoice.best(listOf(" " to null)))

    @Test fun unknownCodeFallsBackToEnglish() =
        assertEquals(BoardLanguage.ENGLISH, BoardLanguage.fromCode("xx"))
}
