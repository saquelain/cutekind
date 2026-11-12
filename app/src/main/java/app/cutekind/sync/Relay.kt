package app.cutekind.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RelayMessage(val id: String, val body: String)
class RelayException(val code: Int, message: String) : IOException(message)

/** An open subscription. [next] blocks until a message arrives; null means the server ended the stream. */
interface RelayStream : java.io.Closeable {
    fun next(): RelayMessage?
}

interface Relay {
    fun publish(server: String, topic: String, body: String)
    fun poll(server: String, topic: String, since: String?): List<RelayMessage>
    fun open(server: String, topic: String, since: String?): RelayStream
}

/**
 * Talks to an ntfy-compatible relay (https://ntfy.sh by default, or a self-hosted one).
 * The relay only ever sees an unguessable topic name and encrypted text.
 */
class NtfyRelay : Relay {
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "CuteKind")
        }

    private fun HttpURLConnection.check() {
        val code = responseCode
        if (code !in 200..299) {
            // ntfy explains refusals in a small JSON body, e.g. "daily message quota reached"
            val said = runCatching {
                Json.parseToJsonElement(errorStream.bufferedReader().readText()).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()
            disconnect()
            throw RelayException(
                code,
                when (code) {
                    429 -> "the relay server is limiting this network (${said ?: "too many requests"}). Will keep retrying."
                    413 -> "that drawing is too big for the relay server."
                    else -> "the relay server answered $code${said?.let { " ($it)" } ?: ""}. Will retry."
                },
            )
        }
    }

    override fun publish(server: String, topic: String, body: String) {
        val c = open("${server.trimEnd('/')}/$topic")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "text/plain")
        c.outputStream.use { it.write(body.toByteArray()) }
        c.check()
        c.inputStream.use { it.readBytes() }
    }

    private fun parse(line: String): RelayMessage? = runCatching {
        val o = Json.parseToJsonElement(line).jsonObject
        if (o["event"]?.jsonPrimitive?.content != "message") null
        else RelayMessage(o["id"]!!.jsonPrimitive.content, o["message"]?.jsonPrimitive?.content ?: "")
    }.getOrNull()

    override fun poll(server: String, topic: String, since: String?): List<RelayMessage> {
        val c = open("${server.trimEnd('/')}/$topic/json?poll=1&since=${since ?: "all"}")
        c.check()
        return c.inputStream.bufferedReader().useLines { lines -> lines.mapNotNull(::parse).toList() }
    }

    override fun open(server: String, topic: String, since: String?): RelayStream {
        val c = open("${server.trimEnd('/')}/$topic/json?since=${since ?: "all"}")
        // the server sends a keepalive line every 45 seconds; two missed ones mean the link is dead
        c.readTimeout = 100_000
        c.check()
        val reader = c.inputStream.bufferedReader()
        return object : RelayStream {
            override fun next(): RelayMessage? {
                while (true) {
                    val line = reader.readLine() ?: return null
                    parse(line)?.let { return it }
                }
            }
            override fun close() { runCatching { c.disconnect() } }
        }
    }
}
