package pl.planlekcji

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

val DAY_NAMES = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek")
val DAY_SHORT = listOf("Pn", "Wt", "Śr", "Cz", "Pt")

/** Jedna lekcja jednej klasy (jeden blok 4 wierszy w arkuszu). */
data class Lesson(
    val day: Int,
    val slot: Int,
    val start: String,
    val end: String,
    val klass: String,
    val students: Int?,
    val room: String,
    val building: String,
    val subject: String,
    val teacher: String,
    val substitution: Boolean,
    val yellow: Boolean,
) {
    val klassId: String get() = klass.uppercase(Locale.ROOT)
    val key: String get() = "$day|$slot|$klassId"
    val grade: Int get() = klass.takeWhile { it.isDigit() }.toIntOrNull() ?: 99

    fun toJson(): JSONObject = JSONObject()
        .put("day", day).put("slot", slot).put("start", start).put("end", end)
        .put("klass", klass).put("students", students ?: -1)
        .put("room", room).put("building", building).put("subject", subject)
        .put("teacher", teacher).put("sub", substitution).put("yellow", yellow)

    companion object {
        fun fromJson(o: JSONObject) = Lesson(
            day = o.getInt("day"),
            slot = o.getInt("slot"),
            start = o.getString("start"),
            end = o.getString("end"),
            klass = o.getString("klass"),
            students = o.optInt("students", -1).takeIf { it >= 0 },
            room = o.optString("room"),
            building = o.optString("building"),
            subject = o.optString("subject"),
            teacher = o.optString("teacher"),
            substitution = o.optBoolean("sub"),
            yellow = o.optBoolean("yellow"),
        )
    }
}

/** Wykryta zmiana w planie (do powiadomień i historii). */
data class Change(
    val ts: Long,
    val day: Int,
    val slot: Int,
    val start: String,
    val klass: String,
    val kind: String, // ADD, DEL, MOD
    val text: String,
) {
    val klassId: String get() = klass.uppercase(Locale.ROOT)

    fun title(): String = "${DAY_NAMES.getOrElse(day) { "?" }} $start · $klass"

    fun toJson(): JSONObject = JSONObject()
        .put("ts", ts).put("day", day).put("slot", slot).put("start", start)
        .put("klass", klass).put("kind", kind).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = Change(
            ts = o.getLong("ts"), day = o.getInt("day"), slot = o.getInt("slot"),
            start = o.optString("start"), klass = o.optString("klass"),
            kind = o.optString("kind"), text = o.optString("text"),
        )
    }
}

fun List<Lesson>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }
fun List<Change>.changesToJson(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

fun jsonToLessons(a: JSONArray): List<Lesson> = (0 until a.length()).map { Lesson.fromJson(a.getJSONObject(it)) }
fun jsonToChanges(a: JSONArray): List<Change> = (0 until a.length()).map { Change.fromJson(a.getJSONObject(it)) }

/** Ustawienia użytkownika. */
data class Settings(
    val url: String = DEFAULT_URL,
    val intervalMin: Int = 15,
    val sortByClass: Boolean = false,
    val descending: Boolean = false,
    val pinned: Set<String> = emptySet(),
    val notify: Boolean = true,
    val onlyPinned: Boolean = true,
)

const val DEFAULT_URL =
    "https://tebedukacja-my.sharepoint.com/:x:/g/personal/pgembalczyk_teb-edukacja_pl/IQCi1FXYe7RZQqgFtNLSmC3fAW3NxDKHLdH3OS0i9baO38g?e=lSgKkM"
