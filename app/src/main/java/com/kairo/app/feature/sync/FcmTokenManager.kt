package com.kairo.app.feature.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.kairo.app.KairoApplication
import com.kairo.app.data.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"
    private const val PREFS_NAME = "kairo_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private val prefs: SharedPreferences by lazy {
        KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _fcmTokenState = MutableStateFlow<String?>(null)
    val fcmTokenState: StateFlow<String?> = _fcmTokenState.asStateFlow()

    init {
        _fcmTokenState.value = getLocalToken()
    }

    fun getLocalToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    fun saveLocalToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        _fcmTokenState.value = token
    }

    fun syncTokenIfLoggedIn(token: String? = null) {
        val userCode = AuthManager.getUserCode()
        val fcmToken = token ?: getLocalToken()

        if (fcmToken.isNullOrBlank()) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { retrievedToken ->
                Log.d(TAG, "Retrieved FCM token from Firebase: $retrievedToken")
                saveLocalToken(retrievedToken)
                if (!userCode.isNullOrBlank()) {
                    sendTokenToRemote(userCode, retrievedToken)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to retrieve FCM token: ${e.message}", e)
            }
        } else {
            if (!userCode.isNullOrBlank()) {
                sendTokenToRemote(userCode, fcmToken)
            }
        }
    }

    private fun sendTokenToRemote(userCode: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseClient.updateFcmToken(userCode, token)
                Log.d(TAG, "Synced FCM token for user $userCode: $token")
            } catch (e: Exception) {
                Log.w(TAG, "Could not update FCM token in Supabase: ${e.message}")
            }
        }
    }
}
