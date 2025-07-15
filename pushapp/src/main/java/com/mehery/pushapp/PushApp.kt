package com.mehery.pushapp

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

import android.app.Application
import android.app.Activity
import android.os.Bundle
import java.lang.ref.WeakReference

class PushApp private constructor() {

    private var initialized = false
    private lateinit var context: Context
    private var serverUrl: String = ""
    private var tenant: String = ""
    private var channelId: String = ""
    private var userId: String? = null
    private var guestId: String? = null

    var lastNotificationData: Map<String, String>? = null

    private var webSocketManager: WebSocketManager? = null
    private var currentActivityRef: WeakReference<Activity>? = null


    companion object {
        @Volatile
        private var instance: PushApp? = null

        fun getInstance(): PushApp =
            instance ?: synchronized(this) {
                instance ?: PushApp().also { instance = it }
            }
    }

    fun initialize(context: Context, identifier: String, sandbox: Boolean = false) {
        if (initialized) return
        initialized = true
        this.context = context.applicationContext

        val parts = identifier.split("#")
        if (parts.size == 2) {
            tenant = parts[0]
            channelId = parts[1]
        } else {
            Log.e("PushApp", "Invalid identifier format, expected tenant#channelId")
            return
        }

        if (sandbox) {
            serverUrl = "https://$tenant.mehery.com"
        } else {
            serverUrl = "https://$tenant.mehery.com"
        }
        Log.d("PushApp", "Server URL set to: $serverUrl")

//        serverUrl = "https://7430c39312c5.ngrok-free.app"
        Log.d("PushApp", "Server URL: $serverUrl")
        Log.d("PushApp", "Channel ID: $channelId")

        try {
            FirebaseApp.initializeApp(this.context)
        } catch (e: Exception) {
            Log.d("PushApp", "Firebase initialization failed or already done: ${e.message}")
        }

        registerDeviceToken()
    }

    fun setPageName(activity: Activity?,name: String) {
        currentActivityRef = WeakReference(activity)
        sendEvent("page_open", mapOf("page" to name))
    }

    fun destroyPageName(name: String) {
        currentActivityRef = null
        sendEvent("page_closed", mapOf("page" to name))
    }


    private fun registerDeviceToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("PushApp", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("PushApp", "FCM Token: $token")
            token?.let { sendTokenToServer("android", it) }
        }
    }

    fun handleDeviceToken(token: String) {
        Log.d("PushApp", "Handling device token: $token")
        sendTokenToServer("android", token)
    }

    private fun sendTokenToServer(platform: String, token: String) {
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val url = "$serverUrl/pushapp/api/register"

        val json = JSONObject()
        json.put("platform", platform)
        json.put("token", token)
        json.put("device_id", deviceId)
        json.put("channel_id", channelId)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Failed to send token to server: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val responseJson = JSONObject(responseBody ?: "{}")
                        val device = responseJson.optJSONObject("device")
                        guestId = device?.optString("user_id")
                        Log.d("PushApp", "Guest ID: $guestId")
                        flushBufferedEvents()
                        sendEvent("app_open", emptyMap())
                    }
                }
            }
        })
    }

    fun login(userId: String) {
        this.userId = userId
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val url = "$serverUrl/pushapp/api/register/user"

        val json = JSONObject()
        json.put("user_id", userId)
        json.put("device_id", deviceId)
        json.put("channel_id", channelId)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Login API failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                flushBufferedEvents()
                connectSocket()
            }
        })
    }

    fun sendEvent(eventName: String, eventData: Map<String, Any>) {
        val userIdToUse = userId ?: guestId
        if (userIdToUse == null) {
            bufferEvent(eventName, eventData)
            return
        }

        Log.d("PushApp", "Event Triggered: $eventName")
        Log.d("PushApp", "Event Data: $eventData")

        val url = "$serverUrl/pushapp/api/events"

        val json = JSONObject()
        json.put("user_id", userIdToUse)
        json.put("channel_id", channelId)
        json.put("event_name", eventName)
        json.put("event_data", JSONObject(eventData))

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Event send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("PushApp", "Event sent successfully")
                } else {
                    Log.e("PushApp", "Failed to send event: ${response.code}")
                }
                response.close()
            }
        })
    }

    private fun bufferEvent(eventName: String, eventData: Map<String, Any>) {
        val sharedPrefs = context.getSharedPreferences("pushapp", Context.MODE_PRIVATE)
        val bufferJsonArray = JSONArray(sharedPrefs.getString("event_buffer", "[]"))
        val eventJson = JSONObject()
        eventJson.put("event_name", eventName)
        eventJson.put("event_data", JSONObject(eventData))
        bufferJsonArray.put(eventJson)
        sharedPrefs.edit().putString("event_buffer", bufferJsonArray.toString()).apply()
        Log.d("PushApp", "Buffered event: $eventName")
    }

    private fun flushBufferedEvents() {
        val userIdToUse = userId ?: guestId ?: return
        val sharedPrefs = context.getSharedPreferences("pushapp", Context.MODE_PRIVATE)
        val bufferJsonArray = JSONArray(sharedPrefs.getString("event_buffer", "[]"))
        if (bufferJsonArray.length() == 0) return

        Log.d("PushApp", "Flushing ${bufferJsonArray.length()} buffered events")

        for (i in 0 until bufferJsonArray.length()) {
            val item = bufferJsonArray.getJSONObject(i)
            val eventName = item.getString("event_name")
            val eventData = item.getJSONObject("event_data")

            val map = mutableMapOf<String, Any>()
            eventData.keys().forEach { key ->
                map[key] = eventData.get(key)
            }

            sendEvent(eventName, map)
        }

        sharedPrefs.edit().remove("event_buffer").apply()
    }

    fun handleNotification(data: Map<String, String>) {
        Log.d("PushApp", "Handling notification data: $data")
        // Your in-app notification or callback handling here
    }


    fun handleSocketNotification(data: Map<String, String>) {
        Log.d("PushApp", "Handling notification data: $data")

        val activity = currentActivityRef?.get()
        if (activity == null) {
            Log.w("PushApp", "No active activity available to show in-app notification")
            return
        }

        // Try parsing the in-app structure from flat map
        val notificationType = data["type"]
        val rawDataJson = data["data"]

        if (notificationType == "in_app" && rawDataJson != null) {
            try {
                val fullData = JSONObject(rawDataJson)

                val finalData = mapOf(
                    "data" to fullData.toMap(), // Flattened map
                    "type" to fullData.optString("type") // Ensure 'type' is outside as well
                )

                activity.runOnUiThread {
                    try {
                        val inAppDisplay = InAppDisplay(activity)
                        inAppDisplay.showInApp(finalData)
                    } catch (e: Exception) {
                        Log.e("PushApp", "Error showing in-app notification: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PushApp", "Failed to parse in-app data: ${e.message}")
            }
        } else {
            Log.d("PushApp", "Notification is not in-app or data is missing")
        }
    }


    private fun connectSocket() {
        val id = userId ?: guestId ?: run {
            Log.d("PushApp", "No userId or guestId available to connect socket")
            return
        }

        if (webSocketManager != null) {
            webSocketManager?.disconnect()
            webSocketManager = null
        }

        webSocketManager = WebSocketManager(id, tenant) { data ->
            Log.d("PushApp", "WebSocket received data: $data")
            handleSocketMessage(data)
        }
        webSocketManager?.connect()
    }

    private fun handleSocketMessage(data: Map<String, Any>) {
        val messageType = data["message_type"] as? String
        if (messageType == "rule_triggered") {
            val ruleId = data["rule_id"] as? String
            ruleId?.let { pollForInApp(it) }
        } else {
            // Other notifications or messages
            // You can convert to Map<String, String> for your existing handleNotification()
            val stringMap = data.mapValues { it.value.toString() }
            handleSocketNotification(stringMap)
        }
    }

    private fun pollForInApp(ruleId: String) {
        val url = "$serverUrl/pushapp/api/poll/in-app"
        val json = JSONObject()
        json.put("rule_id", ruleId)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Poll in-app failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val success = jsonResponse.optBoolean("success", false)
                                if (success) {
                                    val notificationData = jsonResponse.optJSONObject("data")
                                    if (notificationData != null) {
                                        // Run on UI thread
                                        currentActivityRef?.get()?.runOnUiThread {
                                            try {
                                                // Initialize InAppDisplay with current activity
                                                val inAppDisplay = InAppDisplay(currentActivityRef?.get()!!)
                                                // Show in-app UI
                                                inAppDisplay.showInApp(notificationData.toMap())
                                            } catch (e: Exception) {
                                                Log.e("PushApp", "Error showing in-app: ${e.message}")
                                            }
                                        }
                                    } else {
                                        Log.e("PushApp", "No in-app notification data found")
                                    }
                                } else {
                                    Log.e("PushApp", "Poll in-app success=false")
                                }
                            } catch (e: Exception) {
                                Log.e("PushApp", "Error parsing poll in-app response: ${e.message}")
                            }
                        } else {
                            Log.e("PushApp", "Empty poll in-app response body")
                        }
                    } else {
                        Log.e("PushApp", "Poll in-app response not successful: ${response.code}")
                    }
                }
            }
        })
    }

    // Extension function to convert JSONObject to Map<String, Any>
    private fun JSONObject.toMap(): Map<String, Any> = keys().asSequence().associateWith { key ->
        when (val value = this[key]) {
            is JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    list.add(value[i])
                }
                list
            }
            is JSONObject -> value.toMap()
            else -> value
        }
    }


}
