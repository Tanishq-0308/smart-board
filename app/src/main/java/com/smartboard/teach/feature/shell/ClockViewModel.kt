package com.smartboard.teach.feature.shell

import java.time.LocalDateTime

/**
 * The moment the clock island is displaying.
 *
 * There is no ViewModel behind this any more: [ClockIsland] renders in
 * AppRoot, outside the NavHost, where there is no backstack entry to scope a
 * Hilt ViewModel to. A one-second ticker inside the composable is both simpler
 * and correct for what is ultimately one string on screen.
 */
data class ClockState(
    val dateTime: LocalDateTime = LocalDateTime.now(),
)
