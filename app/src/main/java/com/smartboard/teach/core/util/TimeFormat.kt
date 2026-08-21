package com.smartboard.teach.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_12H: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm:ss a", Locale.getDefault())

private val TIME_24H: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())

private val HEADER_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

private val LIST_DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

private val FRIENDLY_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

fun LocalDateTime.formatHeaderTime(use24Hour: Boolean): String =
    format(if (use24Hour) TIME_24H else TIME_12H)

fun LocalDateTime.formatHeaderDate(): String = format(HEADER_DATE)

fun LocalDateTime.formatListDateTime(): String = format(LIST_DATE_TIME)

fun LocalDate.formatFriendly(): String = format(FRIENDLY_DATE)

/**
 * Attendance dates are stored as ISO local-date STRINGS, never epoch millis:
 * boards frequently have a wrong clock or no NTP, and an epoch-derived "today"
 * can silently roll into the wrong day.
 */
fun LocalDate.toIsoDate(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

fun parseIsoDate(value: String): LocalDate = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)

fun epochMillisToDateTime(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
