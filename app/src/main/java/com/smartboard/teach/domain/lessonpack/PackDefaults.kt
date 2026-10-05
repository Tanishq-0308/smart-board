package com.smartboard.teach.domain.lessonpack

import com.smartboard.teach.domain.model.SchoolClass
import com.smartboard.teach.domain.model.TimetableSlot
import java.time.LocalDateTime

/** What the Snapshot dialog offers and preselects. */
object PackDefaults {

    data class Subject(val id: String?, val name: String)

    /** A class's subjects: from the timetable (with ids), else the names the ERP lists for the class. */
    fun subjectsOf(c: SchoolClass, slots: List<TimetableSlot>): List<Subject> {
        val timetabled = slots.filter { it.classId == c.id && it.subjectId != null }
            .distinctBy { it.subjectId }
            .map { Subject(it.subjectId, it.subjectName ?: "") }
        if (timetabled.isNotEmpty()) return timetabled
        return c.subject.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { Subject(null, it) }
    }

    /** The period being taught now (or the one that ended in the last [graceMinutes]), if any. */
    fun current(slots: List<TimetableSlot>, now: LocalDateTime, graceMinutes: Long = 15): TimetableSlot? {
        val day = now.dayOfWeek.value - 1 // ERP: 0 = Monday
        val time = now.toLocalTime()
        return slots.filter { it.dayOfWeek == day && it.start != null && it.end != null }
            .filter { !time.isBefore(it.start) && !time.isAfter(it.end!!.plusMinutes(graceMinutes)) }
            .minByOrNull { if (time.isAfter(it.end!!)) 1 else 0 }
    }
}
