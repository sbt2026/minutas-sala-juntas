package com.contexto.minutas

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Cliente de la API de Gemini (Google AI Studio, capa gratuita). */
object GeminiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun generateMinutes(
        apiKey: String, model: String,
        proyecto: String, coordinador: String, tema: String, lugar: String,
        fecha: String, participantes: String, transcript: String,
        onResult: (minuta: String?, error: String?) -> Unit
    ) {
        executor.execute {
            try {
                val prompt = MinutaPrompt.build(
                    proyecto, coordinador, tema, lugar, fecha, participantes, transcript)

                val body = JSONObject().put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)))))

                val modelo = URLEncoder.encode(model, "UTF-8")
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent")
                    .openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.connectTimeout = 30_000
                conn.readTimeout = 180_000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    val text = JSONObject(response)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    main.post { onResult(text.trim(), null) }
                } else {
                    val msg = try {
                        JSONObject(response).getJSONObject("error").getString("message")
                    } catch (e: Exception) { response.take(300) }
                    main.post { onResult(null, "Error $code: $msg") }
                }
            } catch (e: Exception) {
                main.post { onResult(null, "Error de conexión: ${e.message}") }
            }
        }
    }
}
