package app.cutekind

import app.cutekind.data.AppState
import app.cutekind.data.Repo
import app.cutekind.sync.NtfyRelay
import app.cutekind.sync.SyncEngine
import app.cutekind.sync.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end against the real public relay. Skipped unless CUTEKIND_LIVE=1, because it needs
 * the internet and uses the relay's per-network message allowance.
 */
class LiveRelayTest {
    @Test fun realRelayDeliversADrawingWithinFiveSeconds() = runBlocking {
        assumeTrue(System.getenv("CUTEKIND_LIVE") == "1")
        val code = Wire.newCode()
        fun device(id: String, name: String): Pair<Repo, SyncEngine> {
            val repo = Repo(null, AppState(deviceId = id, myName = name, code = code))
            return repo to SyncEngine(repo, NtfyRelay())
        }
        val (a, ea) = device("a", "Sam")
        val (b, eb) = device("b", "Maya")
        val listening = CoroutineScope(Dispatchers.IO).launch { eb.listen { } }
        withTimeout(20_000) { while (!b.state.value.live) delay(50) }

        val rnd = java.util.Random(5)
        val strokes = (0 until 40).map { Samples.heartStrokes[0].copy(p = List(200) { rnd.nextInt(2000) }) }
        val sentAt = System.currentTimeMillis()
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("real", "Sam", sentAt, strokes = strokes)) })
        check(a.state.value.outbox.isEmpty()) { "relay refused the send: ${a.state.value.lastError}" }
        println("LIVE sent in ${System.currentTimeMillis() - sentAt} ms")
        withTimeout(5_000 - (System.currentTimeMillis() - sentAt)) { while (b.state.value.received.none { it.id == "real" }) delay(20) }
        println("LIVE delivered end to end in ${System.currentTimeMillis() - sentAt} ms")
        listening.cancelAndJoin()
    }
}
