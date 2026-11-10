package app.cutekind.sync

import app.cutekind.data.AppState
import app.cutekind.data.DayAnswers
import app.cutekind.data.Drawing
import app.cutekind.data.Envelope
import app.cutekind.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncEvent {
    data class NewDrawing(val drawing: Drawing) : SyncEvent
    data class Nudge(val name: String) : SyncEvent
    data class Answer(val name: String) : SyncEvent
    data class Joined(val name: String) : SyncEvent
}

const val HISTORY_LIMIT = 40

class SyncEngine(
    private val repo: Repo,
    private val relay: Relay,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    fun envelope(type: String, build: (Envelope) -> Envelope = { it }): Envelope {
        val s = repo.state.value
        return build(Envelope(id = Wire.newId(), from = s.deviceId, name = s.myName, ts = now(), type = type))
    }

    /** Queue a message; it is delivered now if possible, otherwise on the next sync. */
    suspend fun send(env: Envelope): List<SyncEvent> {
        repo.update { it.copy(outbox = it.outbox + env) }
        return sync()
    }

    /** Push anything queued, then pull and apply whatever the partner sent. */
    suspend fun sync(): List<SyncEvent> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val start = repo.state.value
            val code = start.code ?: return@withLock emptyList()
            val keys = Wire.keys(code)
            val events = ArrayList<SyncEvent>()
            try {
                for (env in start.outbox) {
                    Wire.encode(env, keys).forEach { relay.publish(start.relay, keys.topic, it) }
                    repo.update { s -> s.copy(outbox = s.outbox.filterNot { it.id == env.id }) }
                }
                val messages = relay.poll(start.relay, keys.topic, start.cursor)
                for (m in messages) {
                    val chunk = Wire.parseChunk(m.body)
                    if (chunk != null) {
                        val key = "${chunk.id}|${chunk.count}"
                        val have = (repo.state.value.partials[key] ?: emptyMap()) + (chunk.index.toString() to chunk.data)
                        if (have.size == chunk.count) {
                            val text = (0 until chunk.count).joinToString("") { have[it.toString()]!! }
                            repo.update { it.copy(partials = it.partials - key) }
                            Wire.decode(text, keys)?.let { env ->
                                val event = apply(env)
                                event?.let(events::add)
                                // answer a first hello once so the new partner learns our name too
                                if (event is SyncEvent.Joined && env.text != "re") {
                                    val reply = envelope("hello") { it.copy(text = "re", anniv = repo.state.value.anniversary) }
                                    Wire.encode(reply, keys).forEach { relay.publish(start.relay, keys.topic, it) }
                                }
                            }
                        } else {
                            repo.update { s ->
                                // keep only the most recent few half-received messages
                                val kept = (s.partials - key).entries.toList().takeLast(8).associate { it.key to it.value }
                                s.copy(partials = kept + (key to have))
                            }
                        }
                    }
                    repo.update { it.copy(cursor = m.id) }
                }
                repo.update { it.copy(lastSyncTs = now(), lastError = null) }
            } catch (e: Exception) {
                val msg = (e as? RelayException)?.message ?: "No connection. Will retry."
                repo.update { it.copy(lastError = msg) }
            }
            events
        }
    }

    private fun apply(env: Envelope): SyncEvent? {
        var event: SyncEvent? = null
        repo.update { s ->
            if (env.from == s.deviceId || env.id in s.seen) return@update s
            val firstContact = s.partnerName == null
            var n: AppState = s.copy(partnerName = env.name.ifBlank { s.partnerName }, seen = (s.seen + env.id).takeLast(300))
            when (env.type) {
                "draw" -> env.drawing?.let { d ->
                    n = n.copy(received = (listOf(d) + n.received.filterNot { it.id == d.id }).sortedByDescending { it.ts }.take(HISTORY_LIMIT))
                    event = SyncEvent.NewDrawing(d)
                }
                "answer" -> if (env.day != null && env.text != null) {
                    val cur = n.days[env.day] ?: DayAnswers(qid = env.qid ?: 0)
                    n = n.copy(days = (n.days + (env.day to cur.copy(partner = env.text, partnerQid = env.qid))).entries
                        .sortedBy { it.key }.takeLast(60).associate { it.key to it.value })
                    event = SyncEvent.Answer(env.name)
                }
                "nudge" -> {
                    n = n.copy(lastNudgeTs = env.ts, nudgesReceived = n.nudgesReceived + 1)
                    event = SyncEvent.Nudge(env.name)
                }
                "hello" -> {
                    if (env.anniv != null) n = n.copy(anniversary = env.anniv)
                    if (firstContact) event = SyncEvent.Joined(env.name)
                }
            }
            n
        }
        return event
    }
}
