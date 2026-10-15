package schedule

import java.util.Collections

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

class InMemorySessionRepository : SessionRepository {
    private val storage = mutableListOf<Session>()

    override fun add(session: Session) {
        storage.add(session)
    }

    override fun addAll(sessions: List<Session>) {
        storage.addAll(sessions)
    }

    override fun all(): List<Session> = Collections.unmodifiableList(ArrayList(storage))
}

data class AuditEvent(
    val operation: String,
    val count: Int,
) {
    init {
        require(operation.isNotBlank()) { "Operation name must not be blank" }
        require(count >= 0) { "Count must not be negative, got $count" }
    }
}

class AuditedSessionRepository(
    private val delegate: SessionRepository = InMemorySessionRepository(),
) : SessionRepository by delegate {
    private val auditLog = mutableListOf<AuditEvent>()

    var addedCount: Int = 0
        private set

    val events: List<AuditEvent>
        get() = Collections.unmodifiableList(ArrayList(auditLog))

    override fun add(session: Session) {
        delegate.add(session)
        addedCount += 1
        auditLog.add(AuditEvent(operation = "add", count = 1))
    }

    override fun addAll(sessions: List<Session>) {
        val snapshot = Collections.unmodifiableList(ArrayList(sessions))
        delegate.addAll(snapshot)
        addedCount += snapshot.size
        auditLog.add(AuditEvent(operation = "addAll", count = snapshot.size))
    }

    fun formatStats(): String =
        "Sessions: ${all().size}\nAdded: $addedCount\nAudit events: ${auditLog.size}"
}
