package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class ScheduleField(val label: String) {
    DATE("date"),
    TYPE("type"),
    TOPIC("topic"),
    LOCATION("location"),
    TEACHERS("teachers"),
}

sealed interface RejectionReason {
    data object InvalidFieldSeparator : RejectionReason
    data class UnexpectedFieldCount(val actual: Int, val expected: Int = 5) : RejectionReason
    data class EmptyRequiredField(val field: ScheduleField) : RejectionReason
    data class InvalidDate(val rawDate: String) : RejectionReason
    data class UnknownSessionType(val rawType: String) : RejectionReason
    data class InsufficientExamTeachers(val actualCount: Int) : RejectionReason
    data class ControlCharacters(val field: ScheduleField, val value: String) : RejectionReason
    data class OuterWhitespace(val field: ScheduleField, val value: String) : RejectionReason
    data class InvalidCancelledField(val field: ScheduleField, val value: String) : RejectionReason
    data class InvalidLocation(val value: String) : RejectionReason
    data class InvalidTeacherName(val value: String) : RejectionReason
    data class DuplicateTeacher(val name: String) : RejectionReason
}

sealed interface ParseResult {
    val lineNumber: Long

    data class Success(
        override val lineNumber: Long,
        val session: Session,
    ) : ParseResult

    data class Failure(
        override val lineNumber: Long,
        val reason: RejectionReason,
    ) : ParseResult
}

private val DATE_FORMAT_REGEX = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

fun parseScheduleLine(lineNumber: Long, line: String): ParseResult {
    require(lineNumber > 0) { "Line number must be positive, got $lineNumber" }

    fun reject(reason: RejectionReason) = ParseResult.Failure(lineNumber, reason)

    // Keep the checks in the order specified by the assignment: one error per line.
    if ('|' !in line) return reject(RejectionReason.InvalidFieldSeparator)

    val fields = line.split('|')
    if (fields.size != 5) return reject(RejectionReason.UnexpectedFieldCount(fields.size))

    for ((index, value) in fields.withIndex()) {
        if (value.isBlank()) return reject(RejectionReason.EmptyRequiredField(ScheduleField.entries[index]))
    }

    val (dateRaw, typeRaw, topic, location, teachersRaw) = fields
    val date = parseScheduleDateOrNull(dateRaw)
        ?: return reject(RejectionReason.InvalidDate(dateRaw))
    val type = SessionType.entries.firstOrNull { it.name == typeRaw }
        ?: return reject(RejectionReason.UnknownSessionType(typeRaw))

    val teachers = teachersRaw.split(',')
    if (type == SessionType.EXAM) {
        val count = teachers.count { it.isNotBlank() && it != "-" }
        if (count < 2) return reject(RejectionReason.InsufficientExamTeachers(count))
    }

    for ((index, value) in fields.withIndex()) {
        if (hasControlChars(value)) {
            return reject(RejectionReason.ControlCharacters(ScheduleField.entries[index], value))
        }
    }

    for ((index, value) in fields.withIndex()) {
        if (hasOuterWhitespace(value)) {
            return reject(RejectionReason.OuterWhitespace(ScheduleField.entries[index], value))
        }
    }
    if (type != SessionType.CANCELLED) {
        val name = teachers.firstOrNull(::hasOuterWhitespace)
        if (name != null) {
            return reject(RejectionReason.OuterWhitespace(ScheduleField.TEACHERS, name))
        }
    }

    if (type == SessionType.CANCELLED) {
        if (location != "-") {
            return reject(RejectionReason.InvalidCancelledField(ScheduleField.LOCATION, location))
        }
        if (teachersRaw != "-") {
            return reject(RejectionReason.InvalidCancelledField(ScheduleField.TEACHERS, teachersRaw))
        }
        return ParseResult.Success(lineNumber, Cancelled(date, topic))
    }

    if (location == "-") return reject(RejectionReason.InvalidLocation(location))

    for (teacher in teachers) {
        if (teacher.isBlank() || teacher == "-") {
            return reject(RejectionReason.InvalidTeacherName(teacher))
        }
    }

    val names = HashSet<String>()
    for (teacher in teachers) {
        if (!names.add(teacher)) return reject(RejectionReason.DuplicateTeacher(teacher))
    }

    val session = when (type) {
        SessionType.LECTURE -> Lecture(date, topic, location, teachers)
        SessionType.PRACTICE -> Practice(date, topic, location, teachers)
        SessionType.EXAM -> Exam(date, topic, location, teachers)
        SessionType.CANCELLED -> error("CANCELLED is handled above")
    }
    return ParseResult.Success(lineNumber, session)
}

private fun parseScheduleDateOrNull(value: String): LocalDate? {
    if (!DATE_FORMAT_REGEX.matches(value)) return null
    val date = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        return null
    }
    if (date.year !in 1..9999) return null
    return date
}
