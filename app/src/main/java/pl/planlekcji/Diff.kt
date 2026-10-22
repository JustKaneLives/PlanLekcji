package pl.planlekcji

/** Porównuje poprzedni i nowy plan i opisuje zmiany po polsku. */
object Diff {

    private fun v(s: String) = if (s.isBlank()) "brak" else s

    private fun describe(l: Lesson): String {
        val parts = ArrayList<String>()
        if (l.subject.isNotBlank()) parts.add(l.subject)
        if (l.room.isNotBlank()) parts.add("sala ${l.room}")
        if (l.teacher.isNotBlank()) parts.add(l.teacher)
        if (l.substitution) parts.add("zastępstwo")
        return parts.joinToString(", ")
    }

    fun compute(old: List<Lesson>, new: List<Lesson>, now: Long): List<Change> {
        val oldMap = old.associateBy { it.key }
        val newMap = new.associateBy { it.key }
        val out = ArrayList<Change>()

        for ((k, n) in newMap) {
            val o = oldMap[k]
            if (o == null) {
                out.add(Change(now, n.day, n.slot, n.start, n.klass, "ADD", "Nowa lekcja: ${describe(n)}"))
                continue
            }
            val d = ArrayList<String>()
            if (o.room != n.room) d.add("sala ${v(o.room)} → ${v(n.room)}")
            if (o.building != n.building) d.add("budynek ${v(o.building)} → ${v(n.building)}")
            if (o.subject != n.subject) d.add("przedmiot ${v(o.subject)} → ${v(n.subject)}")
            if (o.teacher != n.teacher) d.add("nauczyciel ${v(o.teacher)} → ${v(n.teacher)}")
            if (!o.substitution && n.substitution) d.add("zastępstwo")
            if (o.substitution && !n.substitution) d.add("koniec zastępstwa")
            if (!o.yellow && n.yellow) d.add("zajęcia łączone")
            if (o.yellow && !n.yellow) d.add("zajęcia już nie łączone")
            if (d.isNotEmpty()) {
                out.add(Change(now, n.day, n.slot, n.start, n.klass, "MOD", d.joinToString("; ").replaceFirstChar { it.uppercase() }))
            }
        }
        for ((k, o) in oldMap) {
            if (k !in newMap) {
                out.add(Change(now, o.day, o.slot, o.start, o.klass, "DEL", "Lekcja usunięta: ${describe(o)}"))
            }
        }
        return out.sortedWith(compareBy({ it.day }, { it.slot }, { it.klassId }))
    }
}
