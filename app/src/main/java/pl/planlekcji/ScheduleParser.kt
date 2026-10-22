package pl.planlekcji

/**
 * Struktura arkusza (każdy dzień = osobny arkusz):
 *  - wiersz 1: nagłówki klas od kolumny C, np. "4TIE (17)" = klasa + liczba uczniów,
 *  - kolumna B: godziny "8:00 - 8:45" (komórka scalona na 4 wiersze),
 *  - każda lekcja to blok 4 wierszy: [sala] [budynek] [przedmiot] [nauczyciel],
 *  - żółte wypełnienie = zajęcia łączone, przedmiot zaczynający się od "zastępstwo" = zastępstwo.
 */
object ScheduleParser {

    private val TIME = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})\s*$""")
    private val HEADER = Regex("""^(.*?)\s*\(\s*(\d+)\s*\)\s*$""")
    private val SUBSTITUTION = Regex("""^zast[eę]pstwo\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)

    fun dayIndex(sheetName: String): Int? {
        val n = sheetName.trim().lowercase()
        return when {
            n.startsWith("poniedz") -> 0
            n.startsWith("wt") -> 1
            n.startsWith("śr") || n.startsWith("sr") -> 2
            n.startsWith("czw") -> 3
            n.startsWith("pi") -> 4
            else -> null
        }
    }

    private fun clean(s: String): String =
        s.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    fun parse(bytes: ByteArray): List<Lesson> {
        val sheets = XlsxReader.read(bytes)
        val lessons = ArrayList<Lesson>()
        val usedDays = HashSet<Int>()
        for (sheet in sheets) {
            val day = dayIndex(sheet.name) ?: continue
            if (!usedDays.add(day)) continue
            parseSheet(sheet, day, lessons)
        }
        if (lessons.isEmpty()) {
            throw XlsxException("Nie rozpoznano planu w pliku (brak arkuszy z dniami tygodnia lub lekcji).")
        }
        return lessons
    }

    private fun parseSheet(sheet: Sheet, day: Int, out: MutableList<Lesson>) {
        // kolumna z godzinami (zwykle B)
        var timeCol = 2
        var best = -1
        for (c in 1..3) {
            var n = 0
            for (r in 1..sheet.maxRow) if (TIME.matches(clean(sheet.text(r, c)))) n++
            if (n > best) { best = n; timeCol = c }
        }
        if (best <= 0) return

        // wiersz nagłówków (zwykle 1)
        var headerRow = 1
        for (r in 1..3) {
            var n = 0
            for (c in (timeCol + 1)..sheet.maxCol) if (clean(sheet.text(r, c)).isNotEmpty()) n++
            if (n >= 3) { headerRow = r; break }
        }
        class Head(val col: Int, val name: String, val students: Int?)
        val heads = ArrayList<Head>()
        for (c in (timeCol + 1)..sheet.maxCol) {
            val t = clean(sheet.text(headerRow, c))
            if (t.isEmpty()) continue
            val m = HEADER.find(t)
            if (m != null) heads.add(Head(c, m.groupValues[1].trim(), m.groupValues[2].toIntOrNull()))
            else heads.add(Head(c, t, null))
        }

        val timeRows = (headerRow + 1..sheet.maxRow).filter { TIME.matches(clean(sheet.text(it, timeCol))) }
        for ((slot, r) in timeRows.withIndex()) {
            val m = TIME.find(clean(sheet.text(r, timeCol)))!!
            val start = "${m.groupValues[1].toInt()}:${m.groupValues[2]}"
            val end = "${m.groupValues[3].toInt()}:${m.groupValues[4]}"
            for (h in heads) {
                val room = clean(sheet.text(r, h.col))
                val building = clean(sheet.text(r + 1, h.col))
                var subject = clean(sheet.text(r + 2, h.col))
                val teacher = clean(sheet.text(r + 3, h.col))
                if (room.isEmpty() && building.isEmpty() && subject.isEmpty() && teacher.isEmpty()) continue

                var substitution = false
                val sm = SUBSTITUTION.find(subject)
                if (sm != null) {
                    substitution = true
                    subject = subject.substring(sm.value.length).trim()
                        .replaceFirstChar { it.uppercase() }
                }
                val yellow = (0..3).any { i ->
                    sheet.fill(r + i, h.col)?.let { ColorUtil.isYellow(it) } == true
                }
                out.add(
                    Lesson(
                        day = day, slot = slot, start = start, end = end,
                        klass = h.name, students = h.students,
                        room = room.trimEnd('.'), building = building,
                        subject = subject, teacher = teacher,
                        substitution = substitution, yellow = yellow,
                    )
                )
            }
        }
    }
}
