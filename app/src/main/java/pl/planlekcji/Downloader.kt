package pl.planlekcji

import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL

class DownloadException(msg: String) : Exception(msg)

/** Pobiera plik .xlsx z udostępnionego linku (SharePoint / OneDrive / Google Sheets / bezpośredni URL). */
object Downloader {

    private const val MAX_BYTES = 25 * 1024 * 1024
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun b64Url(s: String): String =
        Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun withDownloadParam(l: String): String = when {
        l.contains("download=1") -> l
        l.contains('?') -> "$l&download=1"
        else -> "$l?download=1"
    }

    /** Lista adresów do wypróbowania (od najbardziej prawdopodobnego). */
    fun candidates(link: String): List<String> {
        val l = link.trim()
        val out = LinkedHashSet<String>()
        val google = Regex("""docs\.google\.com/spreadsheets/d/([\w-]+)""").find(l)
        if (google != null) {
            out.add("https://docs.google.com/spreadsheets/d/${google.groupValues[1]}/export?format=xlsx")
            return out.toList()
        }
        val host = try { Uri.parse(l).host ?: "" } catch (e: Exception) { "" }
        out.add(withDownloadParam(l))
        if (host.endsWith("sharepoint.com")) {
            out.add("https://$host/_api/v2.0/shares/u!${b64Url(l)}/driveitem/content")
        }
        if (host.endsWith("1drv.ms") || host.endsWith("onedrive.live.com")) {
            out.add("https://api.onedrive.com/v1.0/shares/u!${b64Url(l)}/root/content")
        }
        out.add(l)
        return out.toList()
    }

    private fun isXlsx(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    private fun readLimited(input: InputStream): ByteArray? {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            bos.write(buf, 0, n)
            if (bos.size() > MAX_BYTES) return null
        }
        return bos.toByteArray()
    }

    private fun get(url: String): ByteArray {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 40_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.use { readLimited(it) } ?: throw IOException("Plik jest zbyt duży")
        } finally {
            conn?.disconnect()
        }
    }

    /** Zwraca (bajty pliku, użyty adres). Rzuca DownloadException z opisem po polsku. */
    fun fetch(link: String, preferred: String?): Pair<ByteArray, String> {
        if (link.isBlank()) throw DownloadException("Nie podano linku do pliku. Wejdź w Ustawienia i wklej link.")
        if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager())

        val list = candidates(link).toMutableList()
        if (preferred != null && preferred in list) {
            list.remove(preferred)
            list.add(0, preferred)
        }
        var lastError = ""
        var gotHtml = false
        for (u in list) {
            try {
                val b = get(u)
                if (isXlsx(b)) return b to u
                gotHtml = true
            } catch (e: IOException) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        if (gotHtml) {
            throw DownloadException(
                "Link nie zwraca pliku Excela (prawdopodobnie wymaga logowania). " +
                    "Ustaw udostępnianie na „Każda osoba z linkiem może wyświetlać”."
            )
        }
        throw DownloadException("Nie udało się pobrać pliku ($lastError). Sprawdź połączenie z internetem.")
    }
}
