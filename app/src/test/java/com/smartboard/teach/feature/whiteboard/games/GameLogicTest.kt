package com.smartboard.teach.feature.whiteboard.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameLogicTest {

    @Test
    fun everyoneGetsATurnBeforeAnyRepeat() {
        val names = listOf("Asha", "Ravi", "Meena", "Kabir")
        val random = Random(7)
        var picked = emptySet<String>()
        val firstRound = List(4) {
            val (name, next) = GameLogic.pickName(names, picked, avoidRepeats = true, random = random)!!
            picked = next
            name
        }
        assertEquals(names.toSet(), firstRound.toSet())
        // The fifth draw starts a new round rather than getting stuck.
        val (_, afterRestart) = GameLogic.pickName(names, picked, avoidRepeats = true, random = random)!!
        assertEquals(1, afterRestart.size)
    }

    @Test
    fun noNamesMeansNoPick() {
        assertNull(GameLogic.pickName(listOf(" ", ""), emptySet(), avoidRepeats = true))
    }

    @Test
    fun parsesLinesAndCommas() {
        assertEquals(listOf("A", "B", "C"), GameLogic.parseNames("A, B\n\n C "))
    }

    @Test
    fun spinLandsOnTheWinnerAndGoesForward() {
        val random = Random(42)
        var rotation = 123f
        repeat(50) {
            val segments = 2 + it % 9
            val (winner, target) = GameLogic.spinTarget(segments, rotation, random)
            assertTrue(target > rotation + 360f * 4)
            assertEquals(winner, GameLogic.segmentAt(segments, target))
            rotation = target
        }
    }

    @Test
    fun diceStayInRange() {
        val rolls = GameLogic.rollDice(300, Random(1))
        assertTrue(rolls.all { it in 1..6 })
        assertEquals(6, rolls.toSet().size)
    }
}
