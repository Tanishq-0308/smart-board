package com.smartboard.teach.feature.whiteboard

import java.util.Locale

/** Counting down to zero, or up from it. */
enum class TimerMode { COUNTDOWN, STOPWATCH }

/**
 * The lesson timer's logic, with no Android types and no clock of its own.
 *
 * Time is passed IN by the caller rather than read from `System` here, so the
 * arithmetic that decides when a class's five minutes are up is unit-testable
 * — and cannot drift because a recomposition happened to tick it twice.
 *
 * Immutable: every transition returns a new state, so the composable holds one
 * value and Compose sees a real change.
 */
data class TimerState(
    val mode: TimerMode = TimerMode.COUNTDOWN,
    /** What a countdown starts from, and what Reset returns to. */
    val presetMs: Long = DEFAULT_PRESET_MS,
    /** Milliseconds ELAPSED since the timer was started or resumed. */
    val elapsedMs: Long = 0L,
    val isRunning: Boolean = false,
    /**
     * True once a countdown has reached zero.
     *
     * Held as state rather than derived, so the alarm can fire exactly once on
     * the transition — a derived `remaining == 0` would be true on every tick
     * after that and ring forever.
     */
    val hasFinished: Boolean = false,
) {

    /** What the panel displays, in milliseconds. */
    val displayMs: Long
        get() = when (mode) {
            TimerMode.COUNTDOWN -> (presetMs - elapsedMs).coerceAtLeast(0L)
            TimerMode.STOPWATCH -> elapsedMs
        }

    /** True when a countdown has time left, or always for a stopwatch. */
    val canStart: Boolean
        get() = mode == TimerMode.STOPWATCH || displayMs > 0L

    /**
     * Advances by [deltaMs].
     *
     * A countdown STOPS at zero rather than going negative: "-00:00:12" tells
     * a class nothing, and a running timer past zero would keep re-arming the
     * alarm.
     */
    fun tick(deltaMs: Long): TimerState {
        if (!isRunning || deltaMs <= 0L) return this
        val advanced = elapsedMs + deltaMs

        if (mode == TimerMode.COUNTDOWN && advanced >= presetMs) {
            return copy(elapsedMs = presetMs, isRunning = false, hasFinished = true)
        }
        return copy(elapsedMs = advanced)
    }

    fun start(): TimerState =
        if (!canStart) this else copy(isRunning = true, hasFinished = false)

    fun pause(): TimerState = copy(isRunning = false)

    fun toggle(): TimerState = if (isRunning) pause() else start()

    /** Back to the start, stopped — the preset survives. */
    fun reset(): TimerState = copy(elapsedMs = 0L, isRunning = false, hasFinished = false)

    /**
     * Sets the countdown duration.
     *
     * Also resets: leaving elapsed time behind after changing the preset gives
     * a timer that starts part-way through the new duration.
     */
    fun withPreset(millis: Long): TimerState =
        copy(presetMs = millis.coerceIn(0L, MAX_PRESET_MS), elapsedMs = 0L, hasFinished = false)

    /** Switching mode starts clean; the two are not measuring the same thing. */
    fun withMode(newMode: TimerMode): TimerState =
        if (newMode == mode) this else copy(mode = newMode).reset()

    /** Consumes the finished flag, so the alarm rings once per countdown. */
    fun alarmAcknowledged(): TimerState = copy(hasFinished = false)

    companion object {
        /** Five minutes: the most common "you have until…" in a lesson. */
        const val DEFAULT_PRESET_MS = 5 * 60 * 1000L

        /** 99:59:59, the largest the HH:MM:SS display can honestly show. */
        const val MAX_PRESET_MS = (99 * 3600 + 59 * 60 + 59) * 1000L

        val PRESETS_MS = listOf(
            60_000L,
            2 * 60_000L,
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L,
        )
    }
}

/**
 * HH:MM:SS, matching the reference panel.
 *
 * Always three fields, even under an hour: a readout that changes shape as it
 * crosses an hour boundary jumps around on the board, and a class watching the
 * clock notices.
 */
fun formatTimer(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        total / 3600,
        (total % 3600) / 60,
        total % 60,
    )
}

/**
 * Parses "1", "130", "12345" as trailing HH MM SS digits, as a numeric keypad
 * fills a clock field from the right.
 *
 * Digits beyond six are DROPPED FROM THE LEFT rather than rejected, so a
 * teacher who mistypes just keeps going instead of hunting for backspace.
 */
fun parseTimerDigits(digits: String): Long {
    val clean = digits.filter { it.isDigit() }.takeLast(6).padStart(6, '0')
    val hours = clean.substring(0, 2).toLong()
    val minutes = clean.substring(2, 4).toLong()
    val seconds = clean.substring(4, 6).toLong()
    // Minutes and seconds are clamped, not carried: "0:99" is a typo, and
    // silently turning it into 1:39 hides the mistake.
    return (hours * 3600 + minutes.coerceAtMost(59) * 60 + seconds.coerceAtMost(59)) * 1000L
}
