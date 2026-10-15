package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

fun runPeriod(
    command: Command.Period,
    output: (String) -> Unit = ::println,
): Int {
    var matchedCount = 0L
    var rejectedCount = 0L

    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> {
                val session = result.session
                if (session.date in command.from..command.to) {
                    output(formatSessionForPeriod(session))
                    matchedCount++
                }
            }
            is ParseResult.Failure -> rejectedCount++
        }
    }

    output("Total: $matchedCount")
    return if (rejectedCount == 0L) 0 else 1
}

fun runType(
    command: Command.ByType,
    output: (String) -> Unit = ::println,
): Int {
    var matchedCount = 0L
    var rejectedCount = 0L

    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> {
                val session = result.session
                if (session.matchesCliType(command.type)) {
                    output(formatSessionForType(session))
                    matchedCount++
                }
            }
            is ParseResult.Failure -> rejectedCount++
        }
    }

    output("Total: $matchedCount")
    return if (rejectedCount == 0L) 0 else 1
}

fun runErrors(
    command: Command.Errors,
    output: (String) -> Unit = ::println,
): Int {
    var totalLines = 0L
    var rejectedCount = 0L

    forEachScheduleLine(command.file) { number, line ->
        totalLines++
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Success -> Unit
            is ParseResult.Failure -> {
                rejectedCount++
                output(formatParseFailure(result))
            }
        }
    }

    output("Rejected: $rejectedCount of $totalLines")
    return if (rejectedCount == 0L) 0 else 1
}

private fun Session.matchesCliType(type: SessionType): Boolean =
    when (type) {
        SessionType.LECTURE -> this is Lecture
        SessionType.PRACTICE -> this is Practice
        SessionType.EXAM -> this is Exam
        SessionType.CANCELLED -> this is Cancelled
    }
