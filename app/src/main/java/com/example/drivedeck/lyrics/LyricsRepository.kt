package com.example.drivedeck.lyrics

import android.util.Log
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
    private const val TAG = "DriveDeckLyrics"

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
    private val TS = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    suspend fun load(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val cleanTitle = cleanTitle(title)
        val key = "${artist.trim().lowercase()}|${cleanTitle.lowercase()}"
        cache[key]?.takeIf { it.isNotEmpty() }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            try {
                fetch(title.trim(), artist.trim(), durationMs)
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics lookup failed for title='$title', artist='$artist'", e)
                emptyList()
            }
        }

        // Do not cache misses: transient network/API failures can be retried.
        if (result.isNotEmpty()) cache[key] = result
        Log.i(TAG, "Lyrics lookup result: ${result.size} synced lines for '$title' by '$artist'")
        return result
    }

    private fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0
        val clean = cleanTitle(title)

        getExact(title, artist, seconds)?.let { return it }
        if (clean != title) getExact(clean, artist, seconds)?.let { return it }

        // Search endpoint is more tolerant than /get when duration metadata differs.
        search(clean, artist, seconds, includeArtist = true)?.let { return it }
        search(clean, artist, seconds, includeArtist = false)?.let { return it }
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
            val lrc = obj.optString("syncedLyrics").takeIf { it.isNotBlank() } ?: return null
            parseLrc(lrc).takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun search(
        title: String,
        artist: String,
        seconds: Int,
        includeArtist: Boolean
    ): List<LyricLine>? {
        val url = buildString {
            append("$BASE/search?track_name=${enc(title)}")
            if (includeArtist && artist.isNotBlank()) append("&artist_name=${enc(artist)}")
        }
        val body = httpGet(url) ?: return null
        return try {
            val arr = JSONArray(body)
            var bestLines: List<LyricLine>? = null
            var bestScore = Int.MAX_VALUE

            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val lrc = item.optString("syncedLyrics").takeIf { it.isNotBlank() } ?: continue
                val lines = parseLrc(lrc)
                if (lines.isEmpty()) continue

                val duration = item.optInt("duration", 0)
                val durationDiff = if (seconds > 0 && duration > 0) abs(duration - seconds) else 120
                // Prefer matching duration, but don't reject a valid result solely
                // because Spotify/YouTube reports a slightly different duration.
                if (durationDiff < bestScore) {
                    bestScore = durationDiff
                    bestLines = lines
                }
            }
            bestLines
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.d(TAG, "Lyrics API returned HTTP ${conn.responseCode} for ${URL(url).path}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lyrics request failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = TS.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            if (text.isBlank()) continue

            for (m in stamps) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
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
