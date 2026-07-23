package com.contexto.minutas

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Cliente mínimo de la API de Claude para generar la minuta. */
object ClaudeClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun generateMinutes(
        apiKey: String,
        model: String,
        proyecto: String,
        coordinador: String,
        tema: String,
        lugar: String,
        fecha: String,
        participantes: String,
        transcript: String,
        onResult: (minuta: String?, error: String?) -> Unit
    ) {
        executor.execute {
            try {
                val prompt = MinutaPrompt.build(
                    proyecto, coordinador, tema, lugar, fecha, participantes, transcript)

                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4000)
                    .put("messages", JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    ))

                val conn = URL("https://api.anthropic.com/v1/messages")
                    .openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.connectTimeout = 30_000
                conn.readTimeout = 180_000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    val text = JSONObject(response)
                        .getJSONArray("content").getJSONObject(0).getString("text")
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
