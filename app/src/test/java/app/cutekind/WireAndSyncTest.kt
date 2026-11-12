package app.cutekind

import app.cutekind.data.AppState
import app.cutekind.data.Days
import app.cutekind.data.Feature
import app.cutekind.data.Questions
import app.cutekind.data.Repo
import app.cutekind.draw.simplify
import app.cutekind.sync.NtfyRelay
import app.cutekind.sync.Relay
import app.cutekind.sync.RelayException
import app.cutekind.sync.RelayMessage
import app.cutekind.sync.RelayStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import app.cutekind.sync.SyncEngine
import app.cutekind.sync.SyncEvent
import app.cutekind.sync.Wire
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeRelay : Relay {
    val log = ArrayList<RelayMessage>()
    var down = false
    var refusePublish = false // what a rate-limited network looks like: reading works, sending does not
    override fun publish(server: String, topic: String, body: String) {
        if (down) throw IOException("offline")
        if (refusePublish) throw RelayException(429, "the relay server is limiting this network (daily message quota reached). Will keep retrying.")
        check(body.length <= 4096) { "ntfy rejects bodies over 4096 bytes" }
        val m = RelayMessage("m${log.size + 1}", body)
        synchronized(this) { log.add(m); streams.forEach { it.put(m) } }
    }

    // live subscriptions: backlog first, then every new message as it is published
    private val streams = java.util.concurrent.CopyOnWriteArrayList<java.util.concurrent.LinkedBlockingQueue<RelayMessage>>()
    private val end = RelayMessage("", "")
    var opened = 0
    override fun open(server: String, topic: String, since: String?): RelayStream {
        if (down) throw IOException("offline")
        val q = java.util.concurrent.LinkedBlockingQueue<RelayMessage>()
        synchronized(this) { opened++; log.drop(log.indexOfFirst { it.id == since } + 1).forEach(q::put); streams.add(q) }
        return object : RelayStream {
            override fun next(): RelayMessage? = q.take().takeIf { it !== end }
            override fun close() { streams.remove(q); q.put(end) }
        }
    }
    /** Simulates the network dropping every open connection. */
    fun dropConnections() = streams.toList().forEach { streams.remove(it); it.put(end) }
    override fun poll(server: String, topic: String, since: String?): List<RelayMessage> {
        if (down) throw IOException("offline")
        return log.drop(log.indexOfFirst { it.id == since } + 1)
    }
}

class WireAndSyncTest {
    private val code = "K7QM2XHD9TRB"
    private fun device(id: String, name: String, relay: Relay): Pair<Repo, SyncEngine> {
        val repo = Repo(null, AppState(deviceId = id, myName = name, code = code))
        return repo to SyncEngine(repo, relay) { Samples.NOW }
    }

    @Test fun codesNormalizeAndValidate() {
        assertTrue(Wire.isValid(Wire.newCode()))
        assertTrue(Wire.isValid("k7qm-2xhd-9trb"))
        assertFalse(Wire.isValid("K7QM-2XHD"))
        assertEquals("K7QM-2XHD-9TRB", Wire.pretty("k7qm2xhd9trb"))
        assertEquals(Wire.keys("k7qm-2xhd-9trb").topic, Wire.keys(code).topic)
    }

    @Test fun inviteLinksCarryTheCode() {
        assertEquals("https://saquelain.github.io/cutekind/join/#K7QM-2XHD-9TRB", Wire.link(code))
        assertEquals(code, Wire.codeFromLink(Wire.link(code)))
        assertEquals(code, Wire.codeFromLink("cutekind://join?code=K7QM-2XHD-9TRB"))
        assertEquals(code, Wire.codeFromLink("cutekind://join?code=k7qm2xhd9trb&x=1"))
        assertNull(Wire.codeFromLink("https://saquelain.github.io/cutekind/join/"))
        assertNull(Wire.codeFromLink("https://saquelain.github.io/cutekind/join/#K7QM"))
        assertNull(Wire.codeFromLink("cutekind://join?code=K7QM-2XHD-9TRB-EXTRA-STUFF"))
    }

    @Test fun bigDrawingIsChunkedEncryptedAndRestored() {
        val keys = Wire.keys(code)
        val rnd = java.util.Random(7)
        val strokes = (0 until 60).map { Samples.heartStrokes[0].copy(p = List(240) { rnd.nextInt(2000) }) }
        val (_, engine) = device("a", "Sam", FakeRelay())
        val env = engine.envelope("draw") { it.copy(drawing = Samples.drawing("big", "Sam", Samples.NOW, strokes = strokes)) }
        val lines = Wire.encode(env, keys)
        assertTrue("expected several chunks, got ${lines.size}", lines.size > 1)
        assertTrue(lines.all { it.length <= 4096 })
        assertFalse("payload must not be readable JSON", lines.joinToString("").contains("\""))
        val text = lines.map { Wire.parseChunk(it)!! }.sortedBy { it.index }.joinToString("") { it.data }
        assertEquals(env, Wire.decode(text, keys))
        assertNull("another couple's key must not decrypt", Wire.decode(text, Wire.keys("AAAABBBBCCCC")))
    }

    @Test fun drawingTravelsFromOnePhoneToTheOther() = runBlocking {
        val relay = FakeRelay()
        val (a, ea) = device("a", "Sam", relay)
        val (b, eb) = device("b", "Maya", relay)

        // Maya joins: both learn each other's name
        eb.send(eb.envelope("hello"))
        assertEquals(listOf(SyncEvent.Joined("Maya")), ea.sync())
        eb.sync()
        assertEquals("Maya", a.state.value.partnerName)
        assertEquals("Sam", b.state.value.partnerName)

        val d = Samples.drawing("d1", "Sam", Samples.NOW, note = "hi")
        assertTrue(ea.send(ea.envelope("draw") { it.copy(drawing = d) }).isEmpty())
        assertEquals(listOf(SyncEvent.NewDrawing(d)), eb.sync())
        assertEquals(d, b.state.value.received.first())
        assertTrue("own drawing must not echo back", a.state.value.received.isEmpty())
        assertTrue("nothing new on a second sync", eb.sync().isEmpty())
        assertEquals(1, b.state.value.received.size)

        eb.send(eb.envelope("nudge"))
        assertEquals(listOf(SyncEvent.Nudge("Maya")), ea.sync())
        assertEquals(1, a.state.value.nudgesReceived)

        val day = Days.key(Samples.NOW)
        eb.send(eb.envelope("answer") { it.copy(day = day, qid = 3, text = "pizza") })
        ea.sync()
        assertEquals("pizza", a.state.value.days[day]?.partner)
    }

    @Test fun offlineSendIsQueuedAndDeliveredLater() = runBlocking {
        val relay = FakeRelay()
        val (a, ea) = device("a", "Sam", relay)
        val (b, eb) = device("b", "Maya", relay)
        relay.down = true
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("d1", "Sam", Samples.NOW)) })
        assertEquals(1, a.state.value.outbox.size)
        assertNotNull(a.state.value.lastError)
        relay.down = false
        ea.sync()
        assertTrue(a.state.value.outbox.isEmpty())
        assertNull(a.state.value.lastError)
        eb.sync()
        assertEquals("d1", b.state.value.received.single().id)
    }

    @Test fun aFailedSendNeverBlocksReceiving() = runBlocking {
        val relay = FakeRelay()
        val (a, ea) = device("a", "Sam", relay)
        val (b, eb) = device("b", "Maya", relay)
        eb.send(eb.envelope("draw") { it.copy(drawing = Samples.drawing("fromMaya1", "Maya", Samples.NOW)) })

        relay.refusePublish = true
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("stuck", "Sam", Samples.NOW)) })
        assertEquals("Maya's drawing still arrives", "fromMaya1", a.state.value.received.single().id)
        assertTrue(a.state.value.lastError!!.startsWith("Not sent yet"))
        assertTrue(a.state.value.lastError!!.contains("daily message quota reached"))

        // a newer drawing replaces the one that never went out; hearts and answers stay queued
        ea.send(ea.envelope("nudge"))
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("newest", "Sam", Samples.NOW + 1)) })
        assertEquals(listOf("nudge", "draw"), a.state.value.outbox.map { it.type })
        assertEquals("newest", a.state.value.outbox.last().drawing?.id)

        relay.refusePublish = false
        ea.sync()
        assertTrue(a.state.value.outbox.isEmpty())
        assertNull(a.state.value.lastError)
        eb.sync()
        assertEquals("newest", b.state.value.received.single().id)
        assertEquals(1, b.state.value.nudgesReceived)
    }

    private suspend fun until(what: String, ms: Long = 5_000, check: () -> Boolean) =
        runCatching { withTimeout(ms) { while (!check()) delay(10) } }.onFailure { throw AssertionError("timed out waiting for: $what") }

    @Test fun liveConnectionDeliversInstantlyAndReconnects() = runBlocking {
        val relay = FakeRelay()
        val (a, ea) = device("a", "Sam", relay)
        val (b, eb) = device("b", "Maya", relay)
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("beforeListening", "Sam", Samples.NOW)) })

        val events = java.util.concurrent.CopyOnWriteArrayList<SyncEvent>()
        val listening = CoroutineScope(Dispatchers.IO).launch { eb.listen { events.addAll(it) } }
        until("backlog delivered on connect") { b.state.value.received.any { it.id == "beforeListening" } }
        assertTrue(b.state.value.live)

        // a multi-chunk drawing sent now shows up without Maya's phone polling at all
        val rnd = java.util.Random(3)
        val big = (0 until 60).map { Samples.heartStrokes[0].copy(p = List(240) { rnd.nextInt(2000) }) }
        val sentAt = System.nanoTime()
        ea.send(ea.envelope("draw") { it.copy(drawing = Samples.drawing("live", "Sam", Samples.NOW + 1, strokes = big)) })
        until("live drawing") { b.state.value.received.firstOrNull()?.id == "live" }
        assertTrue("arrived in well under a second", (System.nanoTime() - sentAt) / 1_000_000 < 1_000)
        until("event raised") { events.any { it is SyncEvent.NewDrawing && it.drawing.id == "live" } }

        // the connection drops; it comes back by itself and nothing sent meanwhile is lost
        relay.dropConnections()
        until("marked disconnected") { !b.state.value.live }
        ea.send(ea.envelope("nudge"))
        until("reconnected and caught up", 8_000) { b.state.value.nudgesReceived == 1 }
        assertTrue(relay.opened >= 2)
        assertEquals("no duplicates after reconnecting", 2, b.state.value.received.size)

        listening.cancelAndJoin()
        assertFalse(b.state.value.live)
    }

    @Test fun simplifyKeepsTheShapeWithFarFewerPoints() {
        val line = (0..200).flatMap { listOf(it * 10, 500) }
        assertEquals(listOf(0, 500, 2000, 500), simplify(line))
        val corner = (0..100).flatMap { listOf(it * 10, 0) } + (1..100).flatMap { listOf(1000, it * 10) }
        assertEquals(listOf(0, 0, 1000, 0, 1000, 1000), simplify(corner))
        val heart = Samples.heartStrokes[0].p
        val slim = simplify(heart)
        assertTrue(slim.size < heart.size && slim.size >= 20)
        assertEquals(heart.take(2), slim.take(2)); assertEquals(heart.takeLast(2), slim.takeLast(2))
        assertEquals(listOf(5, 5), simplify(listOf(5, 5)))
    }

    @Test fun ntfyRelaySpeaksTheRealProtocol() {
        val stored = ArrayList<String>()
        // a tiny stand-in for an ntfy server: POST /topic stores, GET /topic/json?poll=1&since= lists
        val socket = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            while (true) {
                val c = try { socket.accept() } catch (e: IOException) { break }
                c.use {
                    val input = c.getInputStream().bufferedReader()
                    val (method, target) = input.readLine().split(' ')
                    var length = 0
                    while (true) {
                        val h = input.readLine()
                        if (h.isNullOrEmpty()) break
                        if (h.startsWith("content-length", true)) length = h.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length).also { var n = 0; while (n < length) n += input.read(it, n, length - n) }.concatToString()
                    val (status, out) = when {
                        target.startsWith("/busy") -> 429 to """{"code":42908,"http":429,"error":"limit reached: daily message quota reached"}"""
                        method == "POST" -> {
                            stored.add(body)
                            200 to """{"id":"x${stored.size}","time":1,"event":"message","topic":"t","message":"ok"}"""
                        }
                        else -> {
                            val since = target.substringAfter("since=")
                            val from = if (since == "all") 0 else since.removePrefix("x").toInt()
                            200 to """{"id":"o","time":1,"event":"open","topic":"t"}""" + "\n" +
                                stored.drop(from).mapIndexed { i, m -> """{"id":"x${from + i + 1}","time":1,"event":"message","topic":"t","message":"$m"}""" }.joinToString("\n")
                        }
                    }
                    c.getOutputStream().write("HTTP/1.1 $status X\r\nContent-Length: ${out.toByteArray().size}\r\nConnection: close\r\n\r\n$out".toByteArray())
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            val url = "http://127.0.0.1:${socket.localPort}/"
            val relay = NtfyRelay()
            relay.publish(url, "t", "ck1|a|0|1|AAA")
            relay.publish(url, "t", "ck1|b|0|1|BBB")
            assertEquals(listOf("ck1|a|0|1|AAA", "ck1|b|0|1|BBB"), relay.poll(url, "t", null).map { it.body })
            assertEquals(listOf("x2"), relay.poll(url, "t", "x1").map { it.id })
            val busy = runCatching { relay.publish(url, "busy", "x") }.exceptionOrNull()
            assertEquals(429, (busy as RelayException).code)
            assertTrue(busy.message!!.contains("daily message quota reached"))
        } finally { socket.close(); thread.join(2000) }
    }

    @Test fun questionsAndCounters() {
        val day = Days.epochDay(Samples.NOW)
        assertEquals(Questions.forDay(day, false), Questions.forDay(day, false))
        assertFalse((0L..200L).any { Questions.forDay(it, false).spicy })
        assertTrue((0L..200L).any { Questions.forDay(it, true).spicy })
        (0L..200L).forEach { d -> Questions.forDay(d, true).let { assertEquals(it, Questions.byId(it.id)) } }
        assertEquals(214L, Days.daysTogether(Samples.paired.anniversary, Samples.NOW))
        assertEquals(3, Days.streak(Samples.paired, Samples.NOW))
        assertTrue(Feature.entries.all { AppState().on(it) == it.default })
        assertFalse(AppState(features = mapOf("NUDGES" to false)).on(Feature.NUDGES))
    }
}
