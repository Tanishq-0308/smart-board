package com.smartboard.teach.feature.whiteboard.games

import kotlin.random.Random

/**
 * The arithmetic behind the classroom games, kept free of Compose so the
 * fairness rules can be unit-tested. The widgets only animate the answer.
 */
object GameLogic {

    /**
     * Picks a name. With [avoidRepeats], names in [alreadyPicked] are skipped
     * until everyone has had a turn, then the round starts again — "not him
     * again!" is a real classroom complaint.
     *
     * @return the name, and the picked set to carry into the next draw.
     */
    fun pickName(
        names: List<String>,
        alreadyPicked: Set<String>,
        avoidRepeats: Boolean,
        random: Random = Random,
    ): Pair<String, Set<String>>? {
        val pool = names.distinct().filter { it.isNotBlank() }
        if (pool.isEmpty()) return null
        if (!avoidRepeats) return pool.random(random) to alreadyPicked
        val remaining = pool.filterNot { it in alreadyPicked }.ifEmpty { pool }
        val restart = remaining.size == pool.size
        val pick = remaining.random(random)
        return pick to (if (restart) setOf(pick) else alreadyPicked + pick)
    }

    /** Splits typed text into names: one per line, or comma-separated. */
    fun parseNames(text: String): List<String> =
        text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Where a spin should end, in degrees of wheel rotation from [current].
     * Always several full turns forward, so the wheel visibly spins, and the
     * winning segment's centre lands under the pointer at the top.
     */
    fun spinTarget(segmentCount: Int, current: Float, random: Random = Random): Pair<Int, Float> {
        require(segmentCount > 0)
        val winner = random.nextInt(segmentCount)
        val sweep = 360f / segmentCount
        // Segment i spans [i*sweep, (i+1)*sweep) clockwise from the pointer at
        // rotation 0, so rotating the wheel by -(centre) brings it to the top.
        val landing = (360f - (winner + 0.5f) * sweep).mod(360f)
        val base = current - current.mod(360f)
        val turns = 5 + random.nextInt(3)
        return winner to base + turns * 360f + landing
    }

    /** Which segment sits under the pointer at wheel [rotation] degrees. */
    fun segmentAt(segmentCount: Int, rotation: Float): Int {
        val sweep = 360f / segmentCount
        return (((360f - rotation.mod(360f)).mod(360f)) / sweep).toInt().coerceIn(0, segmentCount - 1)
    }

    fun rollDice(count: Int, random: Random = Random): List<Int> = List(count) { random.nextInt(1, 7) }
}

/** One team on the scoreboard. */
data class Team(val name: String, val score: Int = 0)
