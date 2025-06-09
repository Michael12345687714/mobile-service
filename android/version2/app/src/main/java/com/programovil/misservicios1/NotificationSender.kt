package com.programovil.misservicios1

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class NotificationSender {

    private val client = OkHttpClient()
    private val serverKey = "AAAAevijv1M:APA91bHSt7sbkykstJQ02tXWVy_0-Os-gKqw7XTPrSSAWYLH60nQ5Zok0hpsQpGiEsDthQa1wvHXG4AQLtdBsE2Ihysg9ZH8EY-Y-XFtoz4ZuTIZmcAHzccZpys4GNbkmNotLjpOh1pz"

    fun sendNotification(token: String, title: String, body: String) {
        Log.d("NOTIFICATION", "Enviando notificación a token: $token")

        val json = JSONObject().apply {
            put("to", token)
            put("notification", JSONObject().apply {
                put("title", title)
                put("body", body)
                put("sound", "default")
            })
            put("priority", "high")
        }

        Log.d("NOTIFICATION", "JSON: $json")

        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .post(requestBody)
            .addHeader("Authorization", "key=$serverKey")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NOTIFICATION", "Error enviando notificación", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("NOTIFICATION", "Respuesta FCM: $responseBody")
                response.close()
            }
        })
    }
}