package com.smartboard.teach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class InkChoiceTest {

    private val devanagari = Character.UnicodeScript.DEVANAGARI

    @Test fun hindiWrittenInHindiKeepsTheHindiReading() =
        assertEquals("राम है", InkChoice.best(listOf("राम है", "TH a"), devanagari))

    @Test fun englishWrittenInHindiModeUsesTheEnglishReading() {
        // Real readings from the board: the Hindi model's Latin fallback was garbled.
        assertEquals("This is my class of english", InkChoice.best(listOf("Tmmass genglish", "This is my class of english"), devanagari))
        assertEquals("Sharma", InkChoice.best(listOf("shamma", "Sharma"), devanagari))
    }

    @Test fun aMixedLineStaysWithHindi() =
        assertEquals("Force = बल", InkChoice.best(listOf("Force = बल", "Force = ad"), devanagari))

    @Test fun englishModeUsesItsOnlyReading() =
        assertEquals("hello world", InkChoice.best(listOf("hello world"), null))

    @Test fun anEmptyPrimaryFallsBack() =
        assertEquals("Force", InkChoice.best(listOf("", "Force"), devanagari))

    @Test fun nothingReadableIsEmpty() = assertEquals("", InkChoice.best(listOf(" "), null))

    @Test fun unknownCodeFallsBackToEnglish() =
        assertEquals(BoardLanguage.ENGLISH, BoardLanguage.fromCode("xx"))
}
