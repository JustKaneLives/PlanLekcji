package pl.planlekcji

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class Cell(val text: String, val fill: Int?)

class Sheet(val name: String, private val cells: Map<Int, Cell>, val maxRow: Int, val maxCol: Int) {
    fun cell(row: Int, col: Int): Cell? = cells[key(row, col)]
    fun text(row: Int, col: Int): String = cells[key(row, col)]?.text ?: ""
    fun fill(row: Int, col: Int): Int? = cells[key(row, col)]?.fill

    companion object {
        const val STRIDE = 20000
        fun key(row: Int, col: Int) = row * STRIDE + col
    }
}

class XlsxException(msg: String) : Exception(msg)

/** Minimalny czytnik plików .xlsx (bez Apache POI): teksty, kolory wypełnienia, scalone komórki. */
object XlsxReader {

    fun read(bytes: ByteArray): List<Sheet> {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val n = e.name
                    if (!e.isDirectory && (n.endsWith(".xml") || n.endsWith(".rels"))) {
                        val data = zis.readBytes()
                        total += data.size
                        if (total > 60_000_000L) throw XlsxException("Plik jest zbyt duży.")
                        files[n] = data
                    }
                    e = zis.nextEntry
                }
            }
        } catch (e: XlsxException) {
            throw e
        } catch (e: Exception) {
            throw XlsxException("To nie jest poprawny plik Excela (.xlsx).")
        }

        val workbook = files["xl/workbook.xml"] ?: throw XlsxException("Brak xl/workbook.xml – to nie jest plik .xlsx.")
        val rels = parseRels(files["xl/_rels/workbook.xml.rels"])
        val sst = parseSharedStrings(files["xl/sharedStrings.xml"])
        val theme = parseTheme(files["xl/theme/theme1.xml"])
        val xfFills = parseStyles(files["xl/styles.xml"], theme)

        val result = ArrayList<Sheet>()
        for ((name, rid) in parseSheetList(workbook)) {
            val target = rels[rid] ?: continue
            val path = if (target.startsWith("/")) target.substring(1) else "xl/$target"
            val data = files[path] ?: continue
            result.add(parseSheet(name, data, sst, xfFills))
        }
        return result
    }

    // ---------- pomocnicze ----------

    private fun newParser(b: ByteArray): XmlPullParser =
        Xml.newPullParser().also { it.setInput(ByteArrayInputStream(b), null) }

    /** Wartość atrybutu ignorując prefiks przestrzeni nazw (np. "r:id" == "id"). */
    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) {
            val n = getAttributeName(i)
            if (n == name || n.substringAfter(':') == name) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlPullParser.localName(): String = (name ?: "").substringAfter(':')

    private fun parseSheetList(b: ByteArray): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val p = newParser(b)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.localName() == "sheet") {
                val n = p.attr("name")
                val id = p.attr("id")
                if (n != null && id != null) out.add(n to id)
            }
            ev = p.next()
        }
        return out
    }

    private fun parseRels(b: ByteArray?): Map<String, String> {
        if (b == null) return emptyMap()
        val out = HashMap<String, String>()
        val p = newParser(b)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.localName() == "Relationship") {
                val id = p.attr("Id")
                val t = p.attr("Target")
                if (id != null && t != null) out[id] = t
            }
            ev = p.next()
        }
        return out
    }

    private fun parseSharedStrings(b: ByteArray?): List<String> {
        if (b == null) return emptyList()
        val out = ArrayList<String>()
        val p = newParser(b)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var inPh = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.localName()) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "rPh" -> inPh = true
                    "t" -> inT = inSi && !inPh
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.localName()) {
                    "t" -> inT = false
                    "rPh" -> inPh = false
                    "si" -> { out.add(sb.toString()); inSi = false }
                }
            }
            ev = p.next()
        }
        return out
    }

    /** Kolejność indeksów motywu w Excelu: lt1, dk1, lt2, dk2, accent1..6. */
    private fun parseTheme(b: ByteArray?): List<Int> {
        val defaults = listOf(
            0xFFFFFFFF, 0xFF000000, 0xFFE7E6E6, 0xFF44546A, 0xFF4472C4,
            0xFFED7D31, 0xFFA5A5A5, 0xFFFFC000, 0xFF5B9BD5, 0xFF70AD47,
        ).map { it.toInt() }
        if (b == null) return defaults
        val found = HashMap<String, Int>()
        val p = newParser(b)
        var current: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = p.localName()
                when (n) {
                    "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3",
                    "accent4", "accent5", "accent6" -> current = n
                    "hlink", "folHlink" -> current = null
                    "sysClr" -> current?.let { c ->
                        p.attr("lastClr")?.let { v -> parseHex(v)?.let { found[c] = it } }
                    }
                    "srgbClr" -> current?.let { c ->
                        p.attr("val")?.let { v -> parseHex(v)?.let { found[c] = it } }
                    }
                }
            }
            if (ev == XmlPullParser.END_TAG && p.localName() == "clrScheme") current = null
            ev = p.next()
        }
        val order = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6")
        return order.mapIndexed { i, n -> found[n] ?: defaults[i] }
    }

    private fun parseHex(s: String): Int? {
        val v = s.trim()
        return try {
            when (v.length) {
                6 -> (0xFF000000L or v.toLong(16)).toInt()
                8 -> v.toLong(16).toInt()
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Zwraca listę: indeks stylu komórki (xf) -> kolor wypełnienia (ARGB) albo null. */
    private fun parseStyles(b: ByteArray?, theme: List<Int>): List<Int?> {
        if (b == null) return emptyList()
        val fills = ArrayList<Int?>()
        val xfFill = ArrayList<Int>()
        val p = newParser(b)
        var inFills = false
        var inCellXfs = false
        var inFill = false
        var solid = false
        var color: Int? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.localName()) {
                    "fills" -> inFills = true
                    "cellXfs" -> inCellXfs = true
                    "fill" -> if (inFills) { inFill = true; solid = false; color = null }
                    "patternFill" -> if (inFill) solid = p.attr("patternType") == "solid"
                    "fgColor" -> if (inFill && solid) {
                        val tint = p.attr("tint")?.toFloatOrNull() ?: 0f
                        val rgb = p.attr("rgb")
                        val th = p.attr("theme")?.toIntOrNull()
                        val idx = p.attr("indexed")?.toIntOrNull()
                        color = when {
                            rgb != null -> parseHex(rgb)?.let { ColorUtil.applyTint(it, tint) }
                            th != null -> theme.getOrNull(th)?.let { ColorUtil.applyTint(it, tint) }
                            idx != null -> ColorUtil.INDEXED_YELLOWS[idx]
                            else -> null
                        }
                    }
                    "xf" -> if (inCellXfs) xfFill.add(p.attr("fillId")?.toIntOrNull() ?: 0)
                }
                XmlPullParser.END_TAG -> when (p.localName()) {
                    "fills" -> inFills = false
                    "cellXfs" -> inCellXfs = false
                    "fill" -> if (inFill) { fills.add(color); inFill = false }
                }
            }
            ev = p.next()
        }
        return xfFill.map { fills.getOrNull(it) }
    }

    private fun colIndex(ref: String): Int {
        var c = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') c = c * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') c = c * 26 + (ch - 'a' + 1)
            else break
        }
        return c
    }

    private fun rowIndex(ref: String): Int = ref.dropWhile { !it.isDigit() }.toIntOrNull() ?: 0

    private fun parseSheet(name: String, b: ByteArray, sst: List<String>, xfFill: List<Int?>): Sheet {
        val cells = HashMap<Int, Cell>()
        val merges = ArrayList<String>()
        val p = newParser(b)
        var ref = ""
        var type: String? = null
        var style = 0
        val v = StringBuilder()
        val inline = StringBuilder()
        var inV = false
        var inIs = false
        var inIsT = false
        var maxRow = 0
        var maxCol = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.localName()) {
                    "c" -> {
                        ref = p.attr("r") ?: ""
                        type = p.attr("t")
                        style = p.attr("s")?.toIntOrNull() ?: 0
                        v.setLength(0)
                        inline.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inIsT = true
                    "mergeCell" -> p.attr("ref")?.let { merges.add(it) }
                }
                XmlPullParser.TEXT -> {
                    if (inV) v.append(p.text)
                    if (inIsT) inline.append(p.text)
                }
                XmlPullParser.END_TAG -> when (p.localName()) {
                    "v" -> inV = false
                    "t" -> inIsT = false
                    "is" -> inIs = false
                    "c" -> {
                        val text = when (type) {
                            "s" -> sst.getOrNull(v.toString().trim().toIntOrNull() ?: -1) ?: ""
                            "inlineStr" -> inline.toString()
                            else -> v.toString()
                        }
                        val fill = xfFill.getOrNull(style)
                        val r = rowIndex(ref)
                        val c = colIndex(ref)
                        if (r > 0 && c > 0 && (text.isNotEmpty() || fill != null)) {
                            cells[Sheet.key(r, c)] = Cell(text, fill)
                            if (r > maxRow) maxRow = r
                            if (c > maxCol) maxCol = c
                        }
                    }
                }
            }
            ev = p.next()
        }

        // Scalone komórki w obszarze lekcji (kolumna C i dalej): skopiuj wartość do pozostałych komórek.
        for (m in merges) {
            val parts = m.split(":")
            if (parts.size != 2) continue
            val r1 = rowIndex(parts[0]); val c1 = colIndex(parts[0])
            val r2 = rowIndex(parts[1]); val c2 = colIndex(parts[1])
            if (c1 < 3 || r2 < r1 || c2 < c1) continue
            if ((r2 - r1 + 1) * (c2 - c1 + 1) > 400) continue
            val master = cells[Sheet.key(r1, c1)] ?: continue
            for (r in r1..r2) for (c in c1..c2) {
                if (r == r1 && c == c1) continue
                val existing = cells[Sheet.key(r, c)]
                if (existing == null || existing.text.isEmpty()) {
                    cells[Sheet.key(r, c)] = Cell(master.text, existing?.fill ?: master.fill)
                    if (r > maxRow) maxRow = r
                    if (c > maxCol) maxCol = c
                }
            }
        }
        return Sheet(name, cells, maxRow, maxCol)
    }
}
