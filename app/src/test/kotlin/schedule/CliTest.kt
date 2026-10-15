package schedule

import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CliTest {
    @Test
    fun parsesAllCommandsAndOptionOrders() {
        assertEquals(
            Command.Period("samples/clean.txt", LocalDate.of(2026, 9, 28), LocalDate.of(2027, 5, 13)),
            parseCommand(arrayOf("period", "--to", "2027-05-13", "--file", "samples/clean.txt", "--from", "2026-09-28")),
        )
        for (type in SessionType.entries) {
            assertEquals(
                Command.ByType("a file.txt", type),
                parseCommand(arrayOf("type", "--type", type.name, "--file", "a file.txt")),
            )
        }
        assertEquals(Command.Errors("./--data"), parseCommand(arrayOf("errors", "--file", "./--data")))
        assertEquals(
            Command.Period("x", LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)),
            parseCommand(arrayOf("period", "--file", "x", "--from", "0001-01-01", "--to", "9999-12-31")),
        )
    }

    @Test
    fun rejectsInvalidArgumentsBeforeCallingAHandler() {
        val cases = listOf(
            emptyArray(),
            arrayOf("other"),
            arrayOf("errors"),
            arrayOf("errors", "--file"),
            arrayOf("errors", "--file", ""),
            arrayOf("errors", "--file", " "),
            arrayOf("errors", "--file", "--data"),
            arrayOf("errors", "--file=x"),
            arrayOf("errors", "--unknown", "x"),
            arrayOf("errors", "--file", "x", "--extra", "y"),
            arrayOf("type", "--file", "x", "--file", "y"),
            arrayOf("type", "--type", "exam", "--file", "x"),
            arrayOf("type", "--type", "SEMINAR", "--file", "x"),
        ) + listOf("0000-01-01", "10000-01-01", "2026-02-29", "2026-13-01", "2026-1-01", " 2026-01-01")
            .map { arrayOf("period", "--file", "x", "--from", it, "--to", "2027-05-13") } +
            listOf(arrayOf("period", "--file", "x", "--from", "2027-01-01", "--to", "2026-01-01"))
        for (args in cases) {
            val handler = RecordingHandlers()
            val output = mutableListOf<String>()
            assertEquals(2, runApplication(args, handler, output::add), args.contentToString())
            assertEquals(listOf("Invalid arguments"), output)
            assertEquals(null, handler.called)
        }
    }

    @Test
    fun dispatchesToTheMatchingHandlerAndPreservesItsCode() {
        val cases = listOf(
            arrayOf("period", "--file", "x", "--from", "2024-02-29", "--to", "2024-02-29"),
            arrayOf("type", "--file", "x", "--type", "EXAM"),
            arrayOf("errors", "--file", "x"),
        )
        for ((code, args) in cases.withIndex()) {
            val handlers = RecordingHandlers(code)
            val output = mutableListOf<String>()
            assertEquals(code, runApplication(args, handlers, output::add))
            assertEquals(parseCommand(args), handlers.called)
            assertEquals(emptyList(), output)
        }
    }

    @Test
    fun convertsReadingFailureButDoesNotSwallowProgrammingErrors() {
        val output = mutableListOf<String>()
        val handlers = RecordingHandlers(failure = IOException("unreadable"))
        assertEquals(2, runApplication(arrayOf("errors", "--file", "x"), handlers, output::add))
        assertEquals(listOf("Cannot read file"), output)
        assertFailsWith<IllegalStateException> {
            runApplication(
                arrayOf("errors", "--file", "x"),
                RecordingHandlers(failure = IllegalStateException("bug")),
                output::add,
            )
        }
    }

    @Test
    fun readsLfCrLfBlankAndEmptyFiles(@TempDir directory: Path) {
        for ((text, expected) in listOf(
            "first\nsecond\n" to listOf(1L to "first", 2L to "second"),
            "first\r\n\r\nlast" to listOf(1L to "first", 2L to "", 3L to "last"),
            "\n" to listOf(1L to ""),
            "" to emptyList(),
        )) {
            val file = directory.resolve("input.txt")
            Files.writeString(file, text)
            val actual = mutableListOf<Pair<Long, String>>()
            forEachScheduleLine(file.toString()) { number, line -> actual.add(number to line) }
            assertEquals(expected, actual)
        }
        assertFailsWith<IOException> { forEachScheduleLine(directory.resolve("missing.txt").toString()) { _, _ -> } }
    }
}

private class RecordingHandlers(
    private val code: Int = 0,
    private val failure: Exception? = null,
) : CommandHandlers {
    var called: Command? = null

    private fun record(command: Command): Int {
        called = command
        failure?.let { throw it }
        return code
    }

    override fun period(command: Command.Period): Int = record(command)
    override fun byType(command: Command.ByType): Int = record(command)
    override fun errors(command: Command.Errors): Int = record(command)
}


class ScheduleTest {
    private val date = LocalDate.of(2026, 10, 5)

    @Test
    fun createsAllFourSessionTypes() {
        assertIs<Lecture>(Lecture(date, "Topic", "Room 1", listOf("Ivanov")))
        assertIs<Practice>(Practice(date, "Topic", "Room 1", listOf("Ivanov")))
        assertIs<Exam>(Exam(date, "Topic", "Room 1", listOf("Ivanov", "Petrov")))
        assertIs<Cancelled>(Cancelled(date, "Topic"))
        assertTrue(Cancelled::class.java.declaredFields.none { it.name == "location" || it.name == "teachers" })
    }

    @Test
    fun constructorsEnforceDateAndTextRules() {
        for (invalidYear in listOf(0, 10000)) {
            assertFailsWith<IllegalArgumentException> {
                Cancelled(LocalDate.of(invalidYear, 1, 1), "Topic")
            }
        }
        for (invalidTopic in listOf("", " ", " Topic", "Topic ", "Topic\nMore")) {
            assertFailsWith<IllegalArgumentException> { Cancelled(date, invalidTopic) }
        }
        for (invalidLocation in listOf("", " ", "-", " Room", "Room ", "Room\t1")) {
            assertFailsWith<IllegalArgumentException> {
                Lecture(date, "Topic", invalidLocation, listOf("Ivanov"))
            }
        }
        assertEquals("A B", Cancelled(date, "A B").topic)
    }

    @Test
    fun teacherListRulesAreCheckedByConstructors() {
        assertFailsWith<IllegalArgumentException> { Lecture(date, "Topic", "Room", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Practice(date, "Topic", "Room", listOf("-")) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room", listOf("Ivanov", "Ivanov")) }
        for (name in listOf("", " ", " Petrov", "Petrov ", "Petrov\n")) {
            assertFailsWith<IllegalArgumentException> { Lecture(date, "Topic", "Room", listOf(name)) }
        }
        assertEquals(listOf("Ivanov", "ivanov"), Exam(date, "Topic", "Room", listOf("Ivanov", "ivanov")).teachers)
    }

    @Test
    fun sessionProtectsInputAndPublishedTeacherLists() {
        val original = mutableListOf("Ivanov", "Petrov")
        val exam = Exam(date, "Topic", "Room", original)
        original.clear()
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
        assertFailsWith<UnsupportedOperationException> {
            (exam.teachers as MutableList<String>).clear()
        }
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
    }

    @Test
    fun copyingCannotBypassValidation() {
        val lecture = Lecture(date, "Topic", "Room", listOf("Ivanov"))
        assertFailsWith<IllegalArgumentException> { lecture.copy(topic = " ") }
        assertFailsWith<IllegalArgumentException> { lecture.copy(teachers = emptyList()) }
        assertFailsWith<IllegalArgumentException> { lecture.copy(location = "-") }
        val exam = Exam(date, "Exam", "Room", listOf("Ivanov", "Petrov"))
        assertFailsWith<IllegalArgumentException> { exam.copy(teachers = listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Cancelled(date, "Topic").copy(topic = "\t") }
        assertEquals(lecture, lecture.copy())
        assertEquals(lecture.hashCode(), lecture.copy().hashCode())
    }

    @Test
    fun repositoryKeepsDuplicatesAndReturnsIndependentSnapshots() {
        val repository = InMemorySessionRepository()
        val session = Cancelled(date, "Topic")
        repository.addAll(listOf(session, session))
        val snapshot = repository.all()
        repository.add(session)
        assertEquals(listOf(session, session), snapshot)
        assertEquals(3, repository.all().size)
        assertFailsWith<UnsupportedOperationException> {
            (snapshot as MutableList<Session>).clear()
        }
        assertEquals(3, repository.all().size)
    }

    @Test
    fun auditedRepositoryRecordsOnlyCompletedAdds() {
        val repository = AuditedSessionRepository()
        val session = Cancelled(date, "Topic")
        repository.add(session)
        repository.addAll(listOf(session, session))
        repository.addAll(emptyList())
        repository.all()
        assertEquals(3, repository.addedCount)
        assertEquals(3, repository.all().size)
        assertEquals(
            listOf(AuditEvent("add", 1), AuditEvent("addAll", 2), AuditEvent("addAll", 0)),
            repository.events,
        )
        val events = repository.events
        repository.add(session)
        assertEquals(3, events.size)
        assertFailsWith<UnsupportedOperationException> { (events as MutableList<AuditEvent>).clear() }
        assertEquals("Sessions: 4\nAdded: 4\nAudit events: 4", repository.formatStats())
    }

    @Test
    fun unsuccessfulDelegateCallDoesNotCreateAuditEvent() {
        val delegate = object : SessionRepository {
            override fun add(session: Session): Unit = throw IllegalStateException("failed")
            override fun addAll(sessions: List<Session>): Unit = throw IllegalStateException("failed")
            override fun all(): List<Session> = emptyList()
        }
        val repository = AuditedSessionRepository(delegate)
        assertFailsWith<IllegalStateException> { repository.add(Cancelled(date, "Topic")) }
        assertFailsWith<IllegalStateException> { repository.addAll(emptyList()) }
        assertEquals(0, repository.addedCount)
        assertEquals(emptyList(), repository.events)
    }

    @Test
    fun parseFailuresFollowTheRequiredPrecedence() {
        val cases = listOf(
            "no separators" to RejectionReason.InvalidFieldSeparator,
            "|bad" to RejectionReason.UnexpectedFieldCount(2),
            "2026-13-45|SEMINAR|Topic|Room|Ivanov" to RejectionReason.InvalidDate("2026-13-45"),
            "2026-10-05|LECTURE| |Room|Ivanov" to RejectionReason.EmptyRequiredField(ScheduleField.TOPIC),
            "2026-10-05|SEMINAR|Topic|Room|Ivanov" to RejectionReason.UnknownSessionType("SEMINAR"),
            "2026-10-05|EXAM|Topic|Room|Ivanov,-" to RejectionReason.InsufficientExamTeachers(1),
            "2026-10-05|LECTURE|A\tB| Room|Ivanov,Ivanov" to
                RejectionReason.ControlCharacters(ScheduleField.TOPIC, "A\tB"),
            "2026-10-05|LECTURE|Topic| Room|Ivanov,Ivanov" to
                RejectionReason.OuterWhitespace(ScheduleField.LOCATION, " Room"),
            "2026-10-05|CANCELLED|Topic|Room|Ivanov" to
                RejectionReason.InvalidCancelledField(ScheduleField.LOCATION, "Room"),
            "2026-10-05|LECTURE|Topic|-|Ivanov,Ivanov" to RejectionReason.InvalidLocation("-"),
            "2026-10-05|LECTURE|Topic|Room|Ivanov,-,Ivanov" to
                RejectionReason.InvalidTeacherName("-"),
            "2026-10-05|EXAM|Topic|Room|Ivanov,Ivanov" to RejectionReason.DuplicateTeacher("Ivanov"),
            "2026-10-05|LECTURE|Topic|Room|Ivanov, ,Petrov" to
                RejectionReason.OuterWhitespace(ScheduleField.TEACHERS, " "),
        )
        for ((line, expected) in cases) {
            val failure = assertIs<ParseResult.Failure>(parseScheduleLine(7, line))
            assertEquals(7L, failure.lineNumber)
            assertEquals(expected, failure.reason, line)
        }
    }

    @Test
    fun parserHandlesEmptyLastFieldAndEscapesMessages() {
        val result = assertIs<ParseResult.Failure>(parseScheduleLine(1, "2026-10-05|LECTURE|Topic|Room|"))
        assertEquals(RejectionReason.EmptyRequiredField(ScheduleField.TEACHERS), result.reason)
        assertEquals("line 1: empty required field \"teachers\"", formatParseFailure(result))
        assertEquals("\\\\\\\"\\u000A\\u0416", escapeForMessage("\\\"\nЖ"))
        assertFailsWith<IllegalArgumentException> { parseScheduleLine(0, "anything") }
    }

    @Test
    fun periodAndTypeFormattingUseTheSameFieldOrder() {
        val lecture = Lecture(date, "Topic", "Room", listOf("Ivanov", "Petrov"))
        assertEquals("2026-10-05 | LECTURE | Topic | Room", formatSessionForPeriod(lecture))
        assertEquals("2026-10-05 | LECTURE | Topic | Room | Ivanov, Petrov", formatSessionForType(lecture))
        assertEquals("2026-10-05 | CANCELLED | Topic | - | -", formatSessionForType(Cancelled(date, "Topic")))
    }

    @Test
    fun commandHandlersFilterDatesInclusivelyAndReportAllErrors(@TempDir directory: Path) {
        val file = directory.resolve("short.txt")
        Files.writeString(file, listOf(
            "2026-10-04|LECTURE|Before|Room|Ivanov",
            "2026-10-05|LECTURE|On start|Room|Ivanov",
            "2026-10-06|EXAM|On end|Room|Ivanov,Petrov",
            "2026-10-07|LECTURE|After|Room|Ivanov",
            "bad record",
        ).joinToString("\n"))
        val periodOutput = mutableListOf<String>()
        val periodCode = runPeriod(Command.Period(file.toString(), date, date.plusDays(1)), periodOutput::add)
        assertEquals(1, periodCode)
        assertEquals(listOf(
            "2026-10-05 | LECTURE | On start | Room",
            "2026-10-06 | EXAM | On end | Room",
            "Total: 2",
        ), periodOutput)

        val typeOutput = mutableListOf<String>()
        assertEquals(1, runType(Command.ByType(file.toString(), SessionType.EXAM), typeOutput::add))
        assertEquals(listOf("2026-10-06 | EXAM | On end | Room | Ivanov, Petrov", "Total: 1"), typeOutput)

        val errorOutput = mutableListOf<String>()
        assertEquals(1, runErrors(Command.Errors(file.toString()), errorOutput::add))
        assertEquals(listOf("line 5: invalid field separator", "Rejected: 1 of 5"), errorOutput)

        val emptyFile = directory.resolve("empty.txt")
        Files.writeString(emptyFile, "")
        val emptyOutput = mutableListOf<String>()
        assertEquals(0, runErrors(Command.Errors(emptyFile.toString()), emptyOutput::add))
        assertEquals(listOf("Rejected: 0 of 0"), emptyOutput)
    }

    @Test
    fun rescheduleCreatesNewSessionAndRejectsCancellation() {
        val original = Practice(date, "Topic", "Room", listOf("Ivanov"))
        val moved = assertIs<RescheduleResult.Success>(original.reschedule(date.plusDays(3))).session
        assertIs<Practice>(moved)
        assertEquals(date, original.date)
        assertEquals(date.plusDays(3), moved.date)
        assertEquals(original.topic, moved.topic)
        assertEquals(original.teachers, moved.teachers)
        val cancelled = Cancelled(date, "Topic")
        assertIs<RescheduleResult.CancelledCannotBeRescheduled>(cancelled.reschedule(date))
        val invalidDate = LocalDate.of(10000, 1, 1)
        assertFailsWith<IllegalArgumentException> { original.reschedule(invalidDate) }
        assertFailsWith<IllegalArgumentException> { cancelled.reschedule(invalidDate) }
    }
}
