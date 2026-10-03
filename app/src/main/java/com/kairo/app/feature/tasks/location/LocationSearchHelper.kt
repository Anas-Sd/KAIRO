package com.kairo.app.feature.tasks.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class LocationSearchResult(
    val title: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null,
    val formattedDistance: String? = null
)

internal object LocationSearchHelper {

    private const val TAG = "LocationSearchHelper"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun calculateDistance(userLat: Double, userLng: Double, destLat: Double, destLng: Double): Float {
        val dist = FloatArray(1)
        android.location.Location.distanceBetween(userLat, userLng, destLat, destLng, dist)
        return dist[0]
    }

    fun formatDistance(meters: Float?): String? {
        if (meters == null) return null
        return if (meters < 1000f) {
            "${meters.toInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
        }
    }

    suspend fun getUserLocation(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.d(TAG, "Location permission not granted")
            return@withContext null
        }

        // 1. Try Google FusedLocationProviderClient lastLocation
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val lastLoc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
            if (lastLoc != null) {
                Log.d(TAG, "User location obtained via FusedLocationProviderClient: ${lastLoc.latitude}, ${lastLoc.longitude}")
                return@withContext Pair(lastLoc.latitude, lastLoc.longitude)
            }

            // 2. Try getCurrentLocation with balanced power accuracy
            val currentLoc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                val cts = com.google.android.gms.tasks.CancellationTokenSource()
                fusedClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
            if (currentLoc != null) {
                Log.d(TAG, "Live user location obtained via getCurrentLocation: ${currentLoc.latitude}, ${currentLoc.longitude}")
                return@withContext Pair(currentLoc.latitude, currentLoc.longitude)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused location error: ${e.message}")
        }

        // 3. Fallback to Android LocationManager
        getLastKnownLocation(context)
    }

    private fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        return try {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
            val providers = locManager.getProviders(true)
            var bestLocation: android.location.Location? = null
            for (provider in providers) {
                val l = locManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            if (bestLocation != null) {
                Pair(bestLocation.latitude, bestLocation.longitude)
            } else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchLocations(context: Context, query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val userLoc = getUserLocation(context)
        val results = mutableListOf<LocationSearchResult>()

        // 1. Try Native Android Geocoder first with location bias
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = if (userLoc != null) {
                    val localAddrs = try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(trimmed, 8, userLoc.first - 1.5, userLoc.second - 1.5, userLoc.first + 1.5, userLoc.second + 1.5)
                    } catch (_: Exception) { null }

                    if (!localAddrs.isNullOrEmpty()) {
                        localAddrs
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(trimmed, 8)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(trimmed, 8)
                }

                addresses?.forEach { addr ->
                    val feature = addr.featureName ?: addr.thoroughfare ?: trimmed
                    val full = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.joinToString(", ")
                    val dist = if (userLoc != null) calculateDistance(userLoc.first, userLoc.second, addr.latitude, addr.longitude) else null
                    results.add(
                        LocationSearchResult(
                            title = feature,
                            fullAddress = if (full.isNotBlank()) full else "${addr.locality ?: ""}, ${addr.adminArea ?: ""}".trim(',', ' '),
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            distanceMeters = dist,
                            formattedDistance = formatDistance(dist)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Native geocoder error: ${e.message}")
        }

        // 2. Query Photon OpenStreetMap API with lat/lon proximity bias
        if (results.size < 5) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
                val biasParam = if (userLoc != null) "&lat=${userLoc.first}&lon=${userLoc.second}" else ""
                val request = Request.Builder()
                    .url("https://photon.komoot.io/api/?q=$encodedQuery$biasParam&limit=10")
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
                                val dist = if (userLoc != null) calculateDistance(userLoc.first, userLoc.second, lat, lng) else null
                                results.add(
                                    LocationSearchResult(
                                        title = name,
                                        fullAddress = fullAddress,
                                        latitude = lat,
                                        longitude = lng,
                                        distanceMeters = dist,
                                        formattedDistance = formatDistance(dist)
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

        // 3. Proximity Sort: strictly by distance to user!
        if (userLoc != null && results.isNotEmpty()) {
            results.sortBy { it.distanceMeters ?: Float.MAX_VALUE }
        }

        results
    }
}
