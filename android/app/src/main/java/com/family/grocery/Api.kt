package com.family.grocery

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : Exception(message)

/** Простой HTTP-клиент без внешних библиотек. Вызывать только из фонового потока. */
class Api(private val baseUrl: String, private val key: String) {

    fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection не поддерживает PATCH — шлём POST с заголовком-заменой.
            val realMethod = if (method == "GET") "GET" else "POST"
            conn.requestMethod = realMethod
            if (method != "GET" && method != "POST") {
                conn.setRequestProperty("X-HTTP-Method-Override", method)
            }
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("X-Family-Key", key)
            conn.setRequestProperty("Accept", "application/json")
            if (realMethod == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getString("error") }.getOrNull()
                throw ApiException(msg ?: "Ошибка сервера ($code)")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
