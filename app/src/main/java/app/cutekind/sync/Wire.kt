package app.cutekind.sync

import app.cutekind.data.AppJson
import app.cutekind.data.Envelope
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The couple code is the only secret: it gives both the relay topic and the encryption key. */
class Keys(val topic: String, val key: ByteArray)

object Wire {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val PREFIX = "ck1"
    const val CHUNK = 3000
    private val random = SecureRandom()
    private val cache = HashMap<String, Keys>()

    fun newCode(): String = (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    fun normalize(code: String): String = code.uppercase().filter { it in ALPHABET }
    fun isValid(code: String): Boolean = normalize(code).length == 12
    fun pretty(code: String): String = normalize(code).chunked(4).joinToString("-")
    const val LINK_BASE = "https://saquelain.github.io/cutekind/join/"

    /** The tap-to-pair link. The code sits after '#', so it is never sent to the web server. */
    fun link(code: String): String = LINK_BASE + "#" + pretty(code)

    /** Pulls a couple code out of a pairing link (https or cutekind://), or null if there is none. */
    fun codeFromLink(link: String): String? {
        val tail = when {
            "code=" in link -> link.substringAfter("code=")
            "#" in link -> link.substringAfter('#')
            else -> return null
        }
        val raw = tail.takeWhile { it.isLetterOrDigit() || it == '-' }
        return normalize(raw).takeIf { raw.length <= 14 && isValid(raw) }
    }

    fun newId(): String = ByteArray(9).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    @Synchronized
    fun keys(code: String): Keys = cache.getOrPut(normalize(code)) {
        val spec = PBEKeySpec(normalize(code).toCharArray(), "cutekind.v1".toByteArray(), 60_000, 52 * 8)
        val out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        Keys("ck" + out.take(20).joinToString("") { "%02x".format(it) }, out.copyOfRange(20, 52))
    }

    /** Envelope -> JSON -> deflate -> AES-256-GCM -> base64url -> relay-sized lines. */
    fun encode(env: Envelope, keys: Keys): List<String> {
        val plain = ByteArrayOutputStream().also { bos ->
            DeflaterOutputStream(bos, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(AppJson.encodeToString(env).toByteArray()) }
        }.toByteArray()
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.key, "AES"), GCMParameterSpec(128, iv))
        val text = Base64.getUrlEncoder().withoutPadding().encodeToString(iv + cipher.doFinal(plain))
        val parts = text.chunked(CHUNK)
        return parts.mapIndexed { i, part -> "$PREFIX|${env.id}|$i|${parts.size}|$part" }
    }

    class Chunk(val id: String, val index: Int, val count: Int, val data: String)

    fun parseChunk(line: String): Chunk? {
        val f = line.split('|', limit = 5)
        if (f.size != 5 || f[0] != PREFIX) return null
        val i = f[2].toIntOrNull() ?: return null
        val n = f[3].toIntOrNull() ?: return null
        if (n !in 1..64 || i !in 0 until n) return null
        return Chunk(f[1], i, n, f[4])
    }

    fun decode(text: String, keys: Keys): Envelope? = runCatching {
        val raw = Base64.getUrlDecoder().decode(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.key, "AES"), GCMParameterSpec(128, raw, 0, 12))
        val plain = cipher.doFinal(raw, 12, raw.size - 12)
        val json = InflaterInputStream(plain.inputStream()).use { it.readBytes() }.decodeToString()
        AppJson.decodeFromString<Envelope>(json)
    }.getOrNull()
}
