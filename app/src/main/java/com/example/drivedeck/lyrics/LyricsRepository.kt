package com.example.drivedeck.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

object LyricsRepository {

    private const val BASE = "https://lrclib.net/api"
    private const val UA = "DriveDeck/1.0 (https://github.com/frdsarff/DriveDeck)"

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    private val TS = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    suspend fun load(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        cache[key]?.let { return it }

        val result = withContext(Dispatchers.IO) { fetch(title, artist, durationMs) }
        cache[key] = result
        return result
    }

    private fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0
        val clean = cleanTitle(title)

        // 1. Exact match
        getExact(title, artist, seconds)?.let { return it }

        // 2. Exact match with cleaned title (buang "(feat. ...)", "- Remastered")
        if (clean != title) {
            getExact(clean, artist, seconds)?.let { return it }
        }

        // 3. Fuzzy search
        search(clean, artist, seconds)?.let { return it }

        return emptyList()
    }

    private fun getExact(title: String, artist: String, seconds: Int): List<LyricLine>? {
        val url = buildString {
            append("$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}")
            if (seconds > 0) append("&duration=$seconds")
        }
        val body = httpGet(url) ?: return null
        return try {
            val obj = JSONObject(body)
            if (obj.isNull("syncedLyrics")) null
            else parseLrc(obj.getString("syncedLyrics")).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun search(title: String, artist: String, seconds: Int): List<LyricLine>? {
        val url = "$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val body = httpGet(url) ?: return null
        return try {
            val arr = JSONArray(body)
            var best: String? = null
            var bestDiff = Int.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.isNull("syncedLyrics")) continue
                val diff = if (seconds > 0) abs(o.optInt("duration", seconds) - seconds) else 0
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = o.getString("syncedLyrics")
                }
            }
            best?.let { parseLrc(it).takeIf { l -> l.isNotEmpty() } }
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = TS.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1_000 + ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""\s*[\(\[].*?[\)\]]"""), "")
            .replace(Regex("""\s+-\s+.*$"""), "")
            .trim()
            .ifBlank { title }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
