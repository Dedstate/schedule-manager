package schedule

private fun Session.typeLabel(): String = when (this) {
    is Lecture -> "LECTURE"
    is Practice -> "PRACTICE"
    is Exam -> "EXAM"
    is Cancelled -> "CANCELLED"
}

fun formatSessionForPeriod(session: Session): String {
    val location = if (session is ActiveSession) session.location else "-"
    return "${session.date} | ${session.typeLabel()} | ${session.topic} | $location"
}

fun formatSessionForType(session: Session): String {
    val teachers = if (session is ActiveSession) session.teachers.joinToString(", ") else "-"
    return "${formatSessionForPeriod(session)} | $teachers"
}

fun formatParseFailure(failure: ParseResult.Failure): String =
    formatRejectionReason(failure.lineNumber, failure.reason)

fun formatRejectionReason(lineNumber: Long, reason: RejectionReason): String {
    val detail = when (reason) {
        RejectionReason.InvalidFieldSeparator ->
            "invalid field separator"

        is RejectionReason.UnexpectedFieldCount ->
            "got ${reason.actual} fields, expected ${reason.expected}"
        is RejectionReason.EmptyRequiredField ->
            "empty required field \"${reason.field.label}\""
        is RejectionReason.InvalidDate ->
            "invalid date \"${escapeForMessage(reason.rawDate)}\""
        is RejectionReason.UnknownSessionType ->
            "unknown session type \"${escapeForMessage(reason.rawType)}\""
        is RejectionReason.InsufficientExamTeachers ->
            "type \"EXAM\" requires at least 2 teachers, got ${reason.actualCount}"
        is RejectionReason.ControlCharacters ->
            "control characters in field \"${reason.field.label}\": \"${escapeForMessage(reason.value)}\""
        is RejectionReason.OuterWhitespace ->
            "outer whitespace in field \"${reason.field.label}\": \"${escapeForMessage(reason.value)}\""
        is RejectionReason.InvalidCancelledField ->
            "field \"${reason.field.label}\" must be \"-\" for \"CANCELLED\", got \"${escapeForMessage(reason.value)}\""
        is RejectionReason.InvalidLocation ->
            "invalid location \"${escapeForMessage(reason.value)}\""
        is RejectionReason.InvalidTeacherName ->
            "invalid teacher name \"${escapeForMessage(reason.value)}\""
        is RejectionReason.DuplicateTeacher ->
            "duplicate teacher \"${escapeForMessage(reason.name)}\""
    }
    return "line $lineNumber: $detail"
}

fun escapeForMessage(value: String): String = buildString(value.length) {
    for (ch in value) {
        when {
            ch == '\\' -> append("\\\\")
            ch == '"' -> append("\\\"")
            ch.isISOControl() || ch.code < 0x20 || ch.code > 0x7E ->
                append("\\u").append(ch.code.toString(16).uppercase().padStart(4, '0'))
            else -> append(ch)
        }
    }
}
