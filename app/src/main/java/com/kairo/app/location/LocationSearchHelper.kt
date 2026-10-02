package com.kairo.app.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LocationSearchResult(
    val title: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double
)

object LocationSearchHelper {

    private const val TAG = "LocationSearchHelper"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun searchLocations(context: Context, query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<LocationSearchResult>()

        // 1. Try Native Android Geocoder first
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = geocoder.getFromLocationName(trimmed, 5)
                    addresses?.forEach { addr ->
                        val feature = addr.featureName ?: addr.thoroughfare ?: trimmed
                        val full = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.joinToString(", ")
                        results.add(
                            LocationSearchResult(
                                title = feature,
                                fullAddress = if (full.isNotBlank()) full else "${addr.locality ?: ""}, ${addr.adminArea ?: ""}".trim(',', ' '),
                                latitude = addr.latitude,
                                longitude = addr.longitude
                            )
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(trimmed, 5)
                    addresses?.forEach { addr ->
                        val feature = addr.featureName ?: addr.thoroughfare ?: trimmed
                        val full = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.joinToString(", ")
                        results.add(
                            LocationSearchResult(
                                title = feature,
                                fullAddress = if (full.isNotBlank()) full else "${addr.locality ?: ""}, ${addr.adminArea ?: ""}".trim(',', ' '),
                                latitude = addr.latitude,
                                longitude = addr.longitude
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Native geocoder error: ${e.message}")
        }

        // 2. If native geocoder returns few or no results, query Photon OpenStreetMap API
        if (results.size < 3) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
                val request = Request.Builder()
                    .url("https://photon.komoot.io/api/?q=$encodedQuery&limit=6")
                    .header("User-Agent", "KairoApp/1.0")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val parsed = json.parseToJsonElement(body).jsonObject
                    val features = parsed["features"]?.jsonArray
                    features?.forEach { featElement ->
                        val featObj = featElement.jsonObject
                        val geometry = featObj["geometry"]?.jsonObject
                        val coordinates = geometry?.get("coordinates")?.jsonArray
                        val props = featObj["properties"]?.jsonObject

                        if (coordinates != null && coordinates.size >= 2 && props != null) {
                            val lng = coordinates[0].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                            val lat = coordinates[1].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                            val name = props["name"]?.jsonPrimitive?.content ?: trimmed
                            val street = props["street"]?.jsonPrimitive?.content
                            val city = props["city"]?.jsonPrimitive?.content ?: props["state"]?.jsonPrimitive?.content
                            val country = props["country"]?.jsonPrimitive?.content

                            val addressParts = listOfNotNull(street, city, country).filter { it.isNotBlank() }
                            val fullAddress = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else name

                            // Avoid exact duplicate coordinates
                            if (results.none { Math.abs(it.latitude - lat) < 0.0001 && Math.abs(it.longitude - lng) < 0.0001 }) {
                                results.add(
                                    LocationSearchResult(
                                        title = name,
                                        fullAddress = fullAddress,
                                        latitude = lat,
                                        longitude = lng
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Photon geocoding fallback error: ${e.message}")
            }
        }

        results
    }
}
