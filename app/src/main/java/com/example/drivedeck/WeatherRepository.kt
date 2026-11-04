package com.example.drivedeck

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WeatherRepository {
    private const val TAG = "DriveDeckWeather"
    private const val PREFS = "drivedeck_settings"
    private val executor = Executors.newSingleThreadExecutor()

    fun refresh(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        val app = context.applicationContext
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onResult?.invoke(false, "Location permission is not enabled")
            return
        }
        try {
            val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            val location = providers.mapNotNull { provider ->
                try { if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null }
                catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
            if (location != null) {
                fetchWeather(app, location, onResult)
                return
            }
            val provider = providers.firstOrNull { try { manager.isProviderEnabled(it) } catch (_: Exception) { false } }
            if (provider == null) {
                onResult?.invoke(false, "Turn on phone location services")
                return
            }
            manager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(value: Location) { fetchWeather(app, value, onResult) }
                @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, Looper.getMainLooper())
        } catch (e: SecurityException) {
            onResult?.invoke(false, "Location permission was denied")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to obtain location", e)
            onResult?.invoke(false, "Unable to obtain phone location")
        }
    }

    private fun fetchWeather(context: Context, location: Location, callback: ((Boolean, String) -> Unit)?) {
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    requestMethod = "GET"
                }
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(payload)
                val current = root.getJSONObject("current")
                val description = weatherDescription(current.optInt("weather_code", -1))
                val place = root.optString("timezone", "").substringAfterLast('/', "").replace('_', ' ').ifBlank { "Current location" }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("weatherReady", true)
                    .putString("weatherTemperature", "${current.optDouble("temperature_2m").toInt()}°")
                    .putString("weatherDescription", description)
                    .putString("weatherLocation", place)
                    .putString("weatherFeelsLike", "${current.optDouble("apparent_temperature").toInt()}°")
                    .putInt("weatherHumidity", current.optInt("relative_humidity_2m"))
                    .putFloat("weatherWind", current.optDouble("wind_speed_10m").toFloat())
                    .putLong("weatherUpdatedAt", System.currentTimeMillis())
                    .apply()
                callback?.invoke(true, "Weather updated for $place")
            } catch (e: Exception) {
                Log.e(TAG, "Weather request failed", e)
                callback?.invoke(false, "Weather update failed. Check internet connection.")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather unavailable"
    }
}
