package app.cutekind.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

/** Single source of truth, persisted as one small JSON file. */
class Repo(private val file: File?, initial: AppState? = null) {
    private val _state = MutableStateFlow(initial ?: load())
    val state: StateFlow<AppState> = _state

    private fun load(): AppState {
        val s = runCatching { AppJson.decodeFromString<AppState>(file!!.readText()) }.getOrNull() ?: AppState()
        return if (s.deviceId.isEmpty()) s.copy(deviceId = UUID.randomUUID().toString().take(12)) else s
    }

    @Synchronized
    fun update(f: (AppState) -> AppState): AppState {
        val next = f(_state.value)
        if (next == _state.value) return next
        _state.value = next
        file?.let {
            val tmp = File(it.parentFile, it.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(next))
            tmp.renameTo(it)
        }
        return next
    }

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(File(context.applicationContext.filesDir, "state.json")).also { instance = it }
        }
    }
}
