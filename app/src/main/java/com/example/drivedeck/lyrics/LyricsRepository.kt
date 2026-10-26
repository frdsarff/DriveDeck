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

    private const val TAG = "DriveDeckLyrics"
    private const val BASE = "https://lrclib.net/api"
    private const val UA = "DriveDeck/1.0 (https://github.com/frdsarff/DriveDeck)"

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
    private val TS = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    suspend fun load(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        val key = "${normalizedArtist.lowercase()}|${normalizedTitle.lowercase()}"
        cache[key]?.let { return it }

        val result = withContext(Dispatchers.IO) {
            runCatching { fetch(normalizedTitle, normalizedArtist, durationMs) }
                .onFailure { Log.e(TAG, "Lyrics lookup failed for title='$normalizedTitle'", it) }
                .getOrDefault(emptyList())
        }
        cache[key] = result
        Log.d(TAG, "Lookup result: title='$normalizedTitle', artist='$normalizedArtist', lines=${result.size}")
        return result
    }

    private fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        if (title.isBlank()) return emptyList()
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0
        val clean = cleanTitle(title)
        val titles = listOf(title, clean).distinct()

        // Prefer exact matches before broad search.
        if (artist.isNotBlank()) {
            for (candidate in titles) {
                getExact(candidate, artist, seconds)?.let { return it }
            }
        }

        // Search with artist, then title only. Some players provide album artist,
        // multiple artists, or inconsistent artist metadata.
        if (artist.isNotBlank()) {
            for (candidate in titles) {
                search(candidate, artist, seconds)?.let { return it }
            }
        }
        for (candidate in titles) {
            search(candidate, "", seconds)?.let { return it }
        }
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
            Log.w(TAG, "Unable to parse exact-match response", e)
            null
        }
    }

    private fun search(title: String, artist: String, seconds: Int): List<LyricLine>? {
        val url = buildString {
            append("$BASE/search?track_name=${enc(title)}")
            if (artist.isNotBlank()) append("&artist_name=${enc(artist)}")
        }
        val body = httpGet(url) ?: return null
        return try {
            val arr = JSONArray(body)
            var bestLines: List<LyricLine>? = null
            var bestScore = Long.MAX_VALUE

            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.isNull("syncedLyrics")) continue
                val lines = parseLrc(item.optString("syncedLyrics"))
                if (lines.isEmpty()) continue

                val candidateDuration = item.optInt("duration", 0)
                val durationDiff = if (seconds > 0 && candidateDuration > 0) {
                    abs(candidateDuration - seconds).toLong()
                } else {
                    120L
                }
                val candidateArtist = item.optString("artistName")
                val artistPenalty = if (artist.isBlank() ||
                    candidateArtist.contains(artist, ignoreCase = true) ||
                    artist.contains(candidateArtist, ignoreCase = true)) 0L else 30L
                val score = durationDiff + artistPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestLines = lines
                }
            }
            bestLines
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse search response", e)
            null
        }
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "LRCLIB returned HTTP $code for ${url.substringBefore('?')}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network request failed for ${url.substringBefore('?')}", e)
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
            for (match in stamps) {
                val min = match.groupValues[1].toLongOrNull() ?: continue
                val sec = match.groupValues[2].toLongOrNull() ?: continue
                if (sec >= 60) continue
                val frac = match.groupValues[3]
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
