package pl.planlekcji

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("plan_prefs", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        url = sp.getString("url", DEFAULT_URL) ?: DEFAULT_URL,
        intervalMin = sp.getInt("interval", 15),
        sortByClass = sp.getBoolean("sortByClass", false),
        descending = sp.getBoolean("desc", false),
        pinned = sp.getStringSet("pinned", emptySet())?.toSet() ?: emptySet(),
        notify = sp.getBoolean("notify", true),
        onlyPinned = sp.getBoolean("onlyPinned", true),
    )

    fun save(s: Settings) {
        sp.edit()
            .putString("url", s.url)
            .putInt("interval", s.intervalMin)
            .putBoolean("sortByClass", s.sortByClass)
            .putBoolean("desc", s.descending)
            .putStringSet("pinned", HashSet(s.pinned))
            .putBoolean("notify", s.notify)
            .putBoolean("onlyPinned", s.onlyPinned)
            .apply()
    }

    var preferredUrl: String?
        get() = sp.getString("preferred_url", null)
        set(v) { sp.edit().putString("preferred_url", v).apply() }
}
