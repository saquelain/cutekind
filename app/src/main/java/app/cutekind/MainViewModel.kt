package app.cutekind

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cutekind.data.AppState
import app.cutekind.data.DayAnswers
import app.cutekind.data.Days
import app.cutekind.data.Drawing
import app.cutekind.data.Envelope
import app.cutekind.data.Feature
import app.cutekind.data.Questions
import app.cutekind.data.Repo
import app.cutekind.data.Stroke
import app.cutekind.sync.HISTORY_LIMIT
import app.cutekind.sync.Sync
import app.cutekind.sync.Wire
import app.cutekind.ui.PAPERS
import app.cutekind.widget.PartnerWidget
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo.get(app)
    val state = repo.state

    // the drawing in progress survives tab switches and rotation
    val strokes = mutableStateListOf<Stroke>()
    var paper by mutableIntStateOf(PAPERS[0])
    var note by mutableStateOf("")

    private fun send(type: String, build: (Envelope) -> Envelope = { it }) {
        val env = Sync.engine(getApplication()).envelope(type, build)
        viewModelScope.launch { Sync.run(getApplication(), env) }
    }

    fun refresh() = viewModelScope.launch { Sync.run(getApplication()) }

    fun createCouple(name: String) = pair(name, Wire.newCode(), hello = false)
    fun join(name: String, code: String) = pair(name, Wire.normalize(code), hello = true)

    private fun pair(name: String, code: String, hello: Boolean) {
        repo.update { it.copy(myName = name, code = code, cursor = null) }
        Sync.schedule(getApplication())
        if (hello) send("hello") else refresh()
    }

    fun sendDrawing() {
        if (strokes.isEmpty()) return
        val s = state.value
        val d = Drawing(Wire.newId(), s.deviceId, s.myName, System.currentTimeMillis(), paper, strokes.toList(), note.trim())
        repo.update { it.copy(sent = (listOf(d) + it.sent).take(HISTORY_LIMIT)) }
        send("draw") { it.copy(drawing = d) }
        strokes.clear(); note = ""
    }

    fun answer(text: String) {
        val now = System.currentTimeMillis()
        val day = Days.key(now)
        val q = Questions.forDay(Days.epochDay(now), state.value.on(Feature.SPICY))
        repo.update { s -> s.copy(days = s.days + (day to (s.days[day] ?: DayAnswers(q.id)).copy(qid = q.id, mine = text))) }
        send("answer") { it.copy(day = day, qid = q.id, text = text) }
    }

    fun nudge() = send("nudge")

    fun setFeature(f: Feature, on: Boolean) {
        repo.update { it.copy(features = it.features + (f.name to on)) }
        if (f == Feature.BACKGROUND_SYNC) Sync.schedule(getApplication())
        PartnerWidget.updateAll(getApplication())
    }

    fun setName(name: String) { repo.update { it.copy(myName = name) } }
    fun setRelay(url: String) { repo.update { it.copy(relay = url, cursor = null) } }

    fun setAnniversary(day: Long?) {
        repo.update { it.copy(anniversary = day) }
        PartnerWidget.updateAll(getApplication())
        if (day != null) send("hello") { it.copy(text = "re", anniv = day) }
    }

    fun unpair() {
        repo.update { AppState(deviceId = it.deviceId, myName = it.myName, features = it.features, relay = it.relay) }
        Sync.schedule(getApplication())
        PartnerWidget.updateAll(getApplication())
    }
}
