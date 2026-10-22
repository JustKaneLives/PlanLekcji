package pl.planlekcji

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class UiState(
    val settings: Settings = Settings(),
    val lessons: List<Lesson> = emptyList(),
    val history: List<Change> = emptyList(),
    val loading: Boolean = false,
    val lastOk: Long = 0L,
    val lastTry: Long = 0L,
    val error: String? = null,
)

/** Jedno źródło prawdy: pobieranie, parsowanie, porównywanie, zapis na dysku i powiadomienia. */
object Repo {
    private var appCtx: Context? = null
    private lateinit var prefs: Prefs
    private val mutex = Mutex()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var lastHash: String = ""

    @Synchronized
    fun init(ctx: Context) {
        if (appCtx != null) return
        val app = ctx.applicationContext
        appCtx = app
        prefs = Prefs(app)
        var lessons: List<Lesson> = emptyList()
        var history: List<Change> = emptyList()
        var lastOk = 0L
        try {
            val f = File(app.filesDir, "snapshot.json")
            if (f.exists()) {
                val o = JSONObject(f.readText())
                lessons = jsonToLessons(o.getJSONArray("lessons"))
                lastHash = o.optString("hash")
                lastOk = o.optLong("ts")
            }
        } catch (e: Exception) { /* uszkodzony zapis – zaczynamy od nowa */ }
        try {
            val f = File(app.filesDir, "history.json")
            if (f.exists()) history = jsonToChanges(org.json.JSONArray(f.readText()))
        } catch (e: Exception) { }
        _state.value = UiState(settings = prefs.load(), lessons = lessons, history = history, lastOk = lastOk)
        Notifier.ensureChannel(app)
    }

    private fun ctx(): Context = appCtx ?: throw IllegalStateException("Repo nie zainicjowane")

    fun updateSettings(f: (Settings) -> Settings) {
        val old = _state.value.settings
        val new = f(old)
        if (new == old) return
        prefs.save(new)
        _state.value = _state.value.copy(settings = new)
        if (new.intervalMin != old.intervalMin) SyncWorker.schedule(ctx(), new.intervalMin, replace = true)
    }

    /** Zmiana linku = nowy plik bazowy (bez lawiny powiadomień o "zmianach"). */
    fun setUrl(url: String) {
        val clean = url.trim()
        if (clean == _state.value.settings.url) return
        updateSettings { it.copy(url = clean) }
        lastHash = ""
        prefs.preferredUrl = null
        File(ctx().filesDir, "snapshot.json").delete()
        _state.value = _state.value.copy(lessons = emptyList(), lastOk = 0L, error = null)
    }

    fun clearHistory() {
        File(ctx().filesDir, "history.json").delete()
        _state.value = _state.value.copy(history = emptyList())
    }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Pobiera plik, porównuje z poprzednią wersją i wysyła powiadomienie o zmianach. Nie rzuca wyjątków. */
    suspend fun sync() {
        mutex.withLock {
            val app = ctx()
            val s0 = _state.value.settings
            _state.value = _state.value.copy(loading = true)
            val now = System.currentTimeMillis()
            try {
                val (bytes, used) = withContext(Dispatchers.IO) { Downloader.fetch(s0.url, prefs.preferredUrl) }
                prefs.preferredUrl = used
                val hash = sha256(bytes)
                val cur = _state.value
                if (hash == lastHash && cur.lessons.isNotEmpty()) {
                    _state.value = cur.copy(loading = false, lastTry = now, lastOk = now, error = null)
                    return
                }
                val lessons = withContext(Dispatchers.Default) { ScheduleParser.parse(bytes) }
                val prev = cur.lessons
                if (prev.isNotEmpty() && lessons.size < prev.size * 0.3) {
                    throw IllegalStateException("Nowy plik wygląda na uszkodzony lub niepełny – pominięto aktualizację.")
                }
                val changes = if (prev.isEmpty()) emptyList() else Diff.compute(prev, lessons, now)
                val history = (changes + cur.history).take(300)
                withContext(Dispatchers.IO) {
                    File(app.filesDir, "snapshot.json").writeText(
                        JSONObject().put("ts", now).put("hash", hash).put("lessons", lessons.toJsonArray()).toString()
                    )
                    File(app.filesDir, "history.json").writeText(history.changesToJson().toString())
                }
                lastHash = hash
                _state.value = _state.value.copy(
                    lessons = lessons, history = history, loading = false,
                    lastTry = now, lastOk = now, error = null,
                )
                if (changes.isNotEmpty() && s0.notify) {
                    val relevant = if (s0.onlyPinned && s0.pinned.isNotEmpty())
                        changes.filter { it.klassId in s0.pinned } else changes
                    Notifier.notifyChanges(app, relevant)
                }
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(loading = false)
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false, lastTry = now,
                    error = e.message ?: "Nieznany błąd podczas odświeżania.",
                )
            }
        }
    }
}
