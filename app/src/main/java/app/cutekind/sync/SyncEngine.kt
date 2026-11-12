package app.cutekind.sync

import app.cutekind.data.AppState
import app.cutekind.data.DayAnswers
import app.cutekind.data.Drawing
import app.cutekind.data.Envelope
import app.cutekind.data.Repo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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

    /**
     * Queue a message; it is delivered now if possible, otherwise on the next sync.
     * Only the newest drawing matters, so a new one replaces any that is still waiting to go out.
     */
    suspend fun send(env: Envelope): List<SyncEvent> {
        repo.update { s -> s.copy(outbox = s.outbox.filterNot { env.type == "draw" && it.type == "draw" } + env) }
        return sync()
    }

    /** Push anything queued, then pull and apply whatever the partner sent. */
    suspend fun sync(): List<SyncEvent> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val start = repo.state.value
            val code = start.code ?: return@withLock emptyList()
            val keys = Wire.keys(code)
            val events = ArrayList<SyncEvent>()

            // Sending and receiving fail independently: a message that cannot go out
            // must never stop the partner's drawings from coming in.
            var error = flush(start.relay, keys)
            try {
                relay.poll(start.relay, keys.topic, start.cursor).forEach { m -> process(m, keys)?.let(events::add) }
                repo.update { it.copy(lastSyncTs = now()) }
            } catch (e: Exception) {
                error = error ?: ("Could not check for new drawings: " + describe(e))
            }
            if (error == null) error = flush(start.relay, keys) // replies queued while receiving
            repo.update { it.copy(lastError = error) }
            events
        }
    }

    /**
     * Instant delivery: holds a stream open to the relay and applies each message the moment it
     * arrives. Runs until cancelled, reconnecting with a growing pause when the connection drops.
     */
    suspend fun listen(onEvents: suspend (List<SyncEvent>) -> Unit) = withContext(Dispatchers.IO) {
        var pause = 1_000L
        while (isActive) {
            val start = repo.state.value
            val code = start.code ?: return@withContext
            val keys = Wire.keys(code)
            var stream: RelayStream? = null
            // a blocked read cannot be cancelled, so a watcher closes the connection under it
            val closer = launch { try { awaitCancellation() } finally { stream?.close() } }
            try {
                stream = relay.open(start.relay, keys.topic, start.cursor)
                repo.update { it.copy(live = true) }
                while (isActive) {
                    val m = stream.next() ?: break
                    pause = 1_000L
                    val event = mutex.withLock {
                        process(m, keys).also {
                            flush(start.relay, keys)
                            repo.update { s -> s.copy(lastSyncTs = now()) }
                        }
                    }
                    if (event != null) onEvents(listOf(event))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // dropped connection: fall through to the pause and reconnect
            } finally {
                closer.cancel()
                stream?.close()
                repo.update { it.copy(live = false) }
            }
            delay(pause)
            pause = (pause * 2).coerceAtMost(60_000L)
        }
    }

    /** Sends everything waiting, oldest first. Returns a description of the problem, or null if all went out. */
    private fun flush(server: String, keys: Keys): String? {
        for (env in repo.state.value.outbox) {
            try {
                Wire.encode(env, keys).forEach { relay.publish(server, keys.topic, it) }
                repo.update { s -> s.copy(outbox = s.outbox.filterNot { it.id == env.id }) }
            } catch (e: Exception) {
                return "Not sent yet: " + describe(e)
            }
        }
        return null
    }

    /** Takes one relay message: collects chunks, and once a message is whole, decrypts and applies it. */
    private fun process(m: RelayMessage, keys: Keys): SyncEvent? {
        var event: SyncEvent? = null
        val chunk = Wire.parseChunk(m.body)
        if (chunk != null) {
            val key = "${chunk.id}|${chunk.count}"
            val have = (repo.state.value.partials[key] ?: emptyMap()) + (chunk.index.toString() to chunk.data)
            if (have.size == chunk.count) {
                val text = (0 until chunk.count).joinToString("") { have[it.toString()]!! }
                repo.update { it.copy(partials = it.partials - key) }
                Wire.decode(text, keys)?.let { env ->
                    event = apply(env)
                    // answer a first hello once so the new partner learns our name too
                    if (event is SyncEvent.Joined && env.text != "re") {
                        val reply = envelope("hello") { it.copy(text = "re", anniv = repo.state.value.anniversary) }
                        repo.update { it.copy(outbox = it.outbox + reply) }
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
        return event
    }

    private fun describe(e: Exception): String = (e as? RelayException)?.message ?: "no connection. Will retry."

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
