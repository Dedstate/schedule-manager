package schedule

import java.time.LocalDate
import java.util.Collections

sealed interface Session {
    val date: LocalDate
    val topic: String
}

sealed interface ActiveSession : Session {
    val location: String
    val teachers: List<String>
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    override val location: String,
    teachers: List<String>,
) : ActiveSession {
    override val teachers: List<String> = copyTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateLocation(location)
        validateTeachers(this.teachers, minCount = 1, typeName = "LECTURE")
    }

    fun copy(
        date: LocalDate = this.date,
        topic: String = this.topic,
        location: String = this.location,
        teachers: List<String> = this.teachers,
    ): Lecture = Lecture(date, topic, location, teachers)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Lecture &&
            date == other.date &&
            topic == other.topic &&
            location == other.location &&
            teachers == other.teachers)

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + teachers.hashCode()
        return result
    }

    override fun toString(): String =
        "Lecture(date=$date, topic=$topic, location=$location, teachers=$teachers)"
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    override val location: String,
    teachers: List<String>,
) : ActiveSession {
    override val teachers: List<String> = copyTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateLocation(location)
        validateTeachers(this.teachers, minCount = 1, typeName = "PRACTICE")
    }

    fun copy(
        date: LocalDate = this.date,
        topic: String = this.topic,
        location: String = this.location,
        teachers: List<String> = this.teachers,
    ): Practice = Practice(date, topic, location, teachers)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Practice &&
            date == other.date &&
            topic == other.topic &&
            location == other.location &&
            teachers == other.teachers)

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + teachers.hashCode()
        return result
    }

    override fun toString(): String =
        "Practice(date=$date, topic=$topic, location=$location, teachers=$teachers)"
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    override val location: String,
    teachers: List<String>,
) : ActiveSession {
    override val teachers: List<String> = copyTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateLocation(location)
        validateTeachers(this.teachers, minCount = 2, typeName = "EXAM")
    }

    fun copy(
        date: LocalDate = this.date,
        topic: String = this.topic,
        location: String = this.location,
        teachers: List<String> = this.teachers,
    ): Exam = Exam(date, topic, location, teachers)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Exam &&
            date == other.date &&
            topic == other.topic &&
            location == other.location &&
            teachers == other.teachers)

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + teachers.hashCode()
        return result
    }

    override fun toString(): String =
        "Exam(date=$date, topic=$topic, location=$location, teachers=$teachers)"
}

class Cancelled(
    override val date: LocalDate,
    override val topic: String,
) : Session {
    init {
        validateCommon(date, topic)
    }

    fun copy(
        date: LocalDate = this.date,
        topic: String = this.topic,
    ): Cancelled = Cancelled(date, topic)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Cancelled &&
            date == other.date &&
            topic == other.topic)

    override fun hashCode(): Int = 31 * date.hashCode() + topic.hashCode()

    override fun toString(): String = "Cancelled(date=$date, topic=$topic)"
}

sealed interface RescheduleResult {
    data class Success(val session: ActiveSession) : RescheduleResult
    data class CancelledCannotBeRescheduled(val session: Cancelled) : RescheduleResult
}

fun Session.reschedule(newDate: LocalDate): RescheduleResult {
    validateYear(newDate)
    return when (this) {
        is Lecture -> RescheduleResult.Success(Lecture(newDate, topic, location, teachers))
        is Practice -> RescheduleResult.Success(Practice(newDate, topic, location, teachers))
        is Exam -> RescheduleResult.Success(Exam(newDate, topic, location, teachers))
        is Cancelled -> RescheduleResult.CancelledCannotBeRescheduled(this)
    }
}

private fun copyTeachers(teachers: List<String>): List<String> {
    val snapshot = ArrayList(teachers)
    return Collections.unmodifiableList(snapshot)
}

private fun validateCommon(date: LocalDate, topic: String) {
    validateYear(date)
    validateTextValue(topic, "topic")
}

private fun validateYear(date: LocalDate) {
    require(date.year in 1..9999) {
        "Year must be in 1..9999, got ${date.year}"
    }
}

private fun validateLocation(location: String) {
    validateTextValue(location, "location")
    require(location != "-") {
        "Active session location must not be \"-\""
    }
}

private fun validateTeachers(teachers: List<String>, minCount: Int, typeName: String) {
    require(teachers.size >= minCount) {
        "Session type $typeName requires at least $minCount teacher(s), got ${teachers.size}"
    }
    val seen = HashSet<String>(teachers.size)
    for (teacher in teachers) {
        validateTextValue(teacher, "teacher name")
        require(teacher != "-") {
            "Teacher name must not be \"-\""
        }
        require(seen.add(teacher)) {
            "Duplicate teacher name: \"$teacher\""
        }
    }
}

internal fun hasControlChars(value: String): Boolean =
    value.any { it.isISOControl() }

internal fun hasOuterWhitespace(value: String): Boolean =
    value.isNotEmpty() && (value.first().isWhitespace() || value.last().isWhitespace())

private fun validateTextValue(value: String, fieldName: String) {
    require(value.isNotEmpty() && value.isNotBlank()) {
        "$fieldName must not be empty or blank"
    }
    require(!hasControlChars(value)) {
        "$fieldName must not contain control characters"
    }
    require(!hasOuterWhitespace(value)) {
        "$fieldName must not have leading or trailing whitespace"
    }
}
