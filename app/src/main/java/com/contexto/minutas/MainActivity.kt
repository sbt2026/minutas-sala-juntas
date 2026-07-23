package com.contexto.minutas

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnRec: Button
    private lateinit var btnMinuta: Button
    private lateinit var btnWord: Button
    private lateinit var btnPdf: Button
    private lateinit var btnNueva: Button
    private lateinit var btnAjustes: Button
    private lateinit var txtTimer: TextView
    private lateinit var txtEstado: TextView
    private lateinit var txtTranscript: TextView
    private lateinit var txtMinuta: TextView
    private lateinit var txtFecha: TextView
    private lateinit var lblMinuta: TextView
    private lateinit var scrollTrans: ScrollView
    private lateinit var scrollMinuta: ScrollView
    private lateinit var editProyecto: EditText
    private lateinit var editCoordinador: EditText
    private lateinit var editTema: EditText
    private lateinit var editLugar: EditText
    private lateinit var editParticipantes: EditText
    private lateinit var splash: View

    private var recording = false
    private val segments get() = RecordingService.segments
    private val partial get() = RecordingService.partial
    private var minutaTexto: String? = null
    private var startTime = 0L
    private var elapsedSec = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("minutas", Context.MODE_PRIVATE) }

    private val timerTick = object : Runnable {
        override fun run() {
            elapsedSec = (System.currentTimeMillis() - startTime) / 1000
            txtTimer.text = formatTime(elapsedSec)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRec = findViewById(R.id.btnRec)
        btnMinuta = findViewById(R.id.btnMinuta)
        btnWord = findViewById(R.id.btnWord)
        btnPdf = findViewById(R.id.btnPdf)
        btnNueva = findViewById(R.id.btnNueva)
        btnAjustes = findViewById(R.id.btnAjustes)
        txtTimer = findViewById(R.id.txtTimer)
        txtEstado = findViewById(R.id.txtEstado)
        txtTranscript = findViewById(R.id.txtTranscript)
        txtMinuta = findViewById(R.id.txtMinuta)
        txtFecha = findViewById(R.id.txtFecha)
        lblMinuta = findViewById(R.id.lblMinuta)
        scrollTrans = findViewById(R.id.scrollTrans)
        scrollMinuta = findViewById(R.id.scrollMinuta)
        editProyecto = findViewById(R.id.editProyecto)
        editCoordinador = findViewById(R.id.editCoordinador)
        editTema = findViewById(R.id.editTema)
        editLugar = findViewById(R.id.editLugar)
        editParticipantes = findViewById(R.id.editParticipantes)
        splash = findViewById(R.id.splash)

        txtFecha.text = SimpleDateFormat("EEEE d 'de' MMMM, yyyy", Locale("es", "MX"))
            .format(Date())

        // Portada: se oculta al tocar o tras 2.5 s
        splash.setOnClickListener { hideSplash() }
        handler.postDelayed({ hideSplash() }, 2500)

        // Recordar valores frecuentes
        editProyecto.setText(prefs.getString("proyecto", ""))
        editCoordinador.setText(prefs.getString("coordinador", ""))
        editLugar.setText(prefs.getString("lugar", ""))

        if (RecordingService.running) {
            recording = true
            startTime = RecordingService.startTime
            handler.post(timerTick)
            btnRec.text = "DETENER"
            btnRec.setBackgroundResource(R.drawable.btn_rec_active)
            RecordingService.uiListener = { renderTranscript() }
            RecordingService.statusListener = { st -> txtEstado.text = st }
            renderTranscript()
        }

        btnRec.setOnClickListener { toggleRecording() }
        btnMinuta.setOnClickListener { generarMinuta() }
        btnWord.setOnClickListener { exportar(word = true) }
        btnPdf.setOnClickListener { exportar(word = false) }
        btnNueva.setOnClickListener { confirmarNueva() }
        btnAjustes.setOnClickListener { mostrarAjustes() }
    }

    private fun hideSplash() {
        if (splash.visibility != View.VISIBLE) return
        splash.animate().alpha(0f).setDuration(400)
            .withEndAction { splash.visibility = View.GONE }.start()
    }

    // ---------- Grabación ----------

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
        RecordingService.uiListener = { renderTranscript() }
        RecordingService.statusListener = { st -> txtEstado.text = st }
        ContextCompat.startForegroundService(
            this, Intent(this, RecordingService::class.java))
        recording = true
        startTime = System.currentTimeMillis()
        handler.post(timerTick)
        btnRec.text = "DETENER"
        btnRec.setBackgroundResource(R.drawable.btn_rec_active)
        btnMinuta.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (segments.isEmpty()) txtTranscript.text = ""
    }

    private fun stopRecording() {
        stopService(Intent(this, RecordingService::class.java))
        recording = false
        handler.removeCallbacks(timerTick)
        btnRec.text = "GRABAR"
        btnRec.setBackgroundResource(R.drawable.btn_rec)
        txtEstado.text = "Grabación detenida — ${segments.size} intervenciones"
        btnMinuta.isEnabled = segments.isNotEmpty()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderTranscript()
    }

    private fun renderTranscript() {
        val body = segments.joinToString("\n\n")
        txtTranscript.text = when {
            body.isBlank() && partial.isBlank() -> "La transcripción aparecerá aquí…"
            partial.isBlank() -> body
            body.isBlank() -> "» $partial"
            else -> "$body\n\n» $partial"
        }
        scrollTrans.post { scrollTrans.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(this, "Se necesita el micrófono para transcribir",
                Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Minuta ----------

    private fun fechaDoc(): String =
        SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

    private fun generarMinuta() {
        if (segments.isEmpty()) return
        guardarCamposFrecuentes()
        val geminiKey = prefs.getString("gemini_key", "") ?: ""
        val claudeKey = prefs.getString("api_key", "") ?: ""

        if (geminiKey.isBlank() && claudeKey.isBlank()) {
            mostrarMinuta(LocalMinutes.generate(
                proyecto = editProyecto.text.toString().trim(),
                coordinador = editCoordinador.text.toString().trim(),
                tema = editTema.text.toString().trim(),
                lugar = editLugar.text.toString().trim(),
                fecha = fechaDoc(),
                participantes = editParticipantes.text.toString().trim(),
                segments = segments))
            txtEstado.text = "Minuta generada (modo plantilla) — exporta a Word o PDF"
            return
        }

        val usaGemini = geminiKey.isNotBlank()
        btnMinuta.isEnabled = false
        btnMinuta.text = "Generando…"
        txtEstado.text =
            if (usaGemini) "Generando minuta con Gemini…" else "Generando minuta con Claude…"

        val alTerminar: (String?, String?) -> Unit = { result, error ->
            btnMinuta.isEnabled = true
            btnMinuta.text = "Generar minuta"
            if (result != null) {
                mostrarMinuta(result)
            } else {
                txtEstado.text = "Error al generar minuta"
                AlertDialog.Builder(this)
                    .setTitle("No se pudo generar la minuta")
                    .setMessage(error)
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }

        if (usaGemini) {
            GeminiClient.generateMinutes(
                apiKey = geminiKey,
                model = prefs.getString("modelo_gemini", "gemini-flash-latest")
                    ?: "gemini-flash-latest",
                proyecto = editProyecto.text.toString().trim(),
                coordinador = editCoordinador.text.toString().trim(),
                tema = editTema.text.toString().trim(),
                lugar = editLugar.text.toString().trim(),
                fecha = fechaDoc(),
                participantes = editParticipantes.text.toString().trim(),
                transcript = segments.joinToString("\n"),
                onResult = alTerminar)
        } else {
            ClaudeClient.generateMinutes(
                apiKey = claudeKey,
                model = prefs.getString("modelo", "claude-sonnet-5") ?: "claude-sonnet-5",
                proyecto = editProyecto.text.toString().trim(),
                coordinador = editCoordinador.text.toString().trim(),
                tema = editTema.text.toString().trim(),
                lugar = editLugar.text.toString().trim(),
                fecha = fechaDoc(),
                participantes = editParticipantes.text.toString().trim(),
                transcript = segments.joinToString("\n"),
                onResult = alTerminar)
        }
    }

    private fun mostrarMinuta(texto: String) {
        minutaTexto = texto
        lblMinuta.visibility = View.VISIBLE
        scrollMinuta.visibility = View.VISIBLE
        txtMinuta.text = texto
        btnWord.isEnabled = true
        btnPdf.isEnabled = true
        txtEstado.text = "Minuta lista — exporta a Word o PDF"
    }

    private fun guardarCamposFrecuentes() {
        prefs.edit()
            .putString("proyecto", editProyecto.text.toString().trim())
            .putString("coordinador", editCoordinador.text.toString().trim())
            .putString("lugar", editLugar.text.toString().trim())
            .apply()
    }

    // ---------- Exportación ----------

    private fun exportar(word: Boolean) {
        val texto = minutaTexto ?: return
        val minuta = MinutaParser.parse(texto, emptyList()).let {
            // respaldo por si el texto no trae datos completos
            it.copy(
                proyecto = it.proyecto.ifBlank { editProyecto.text.toString().trim() },
                coordinador = it.coordinador.ifBlank { editCoordinador.text.toString().trim() },
                tema = it.tema.ifBlank { editTema.text.toString().trim() },
                lugar = it.lugar.ifBlank { editLugar.text.toString().trim() },
                fecha = it.fecha.ifBlank { fechaDoc() },
                participantes = it.participantes.ifEmpty {
                    MinutaParser.parseParticipantes(editParticipantes.text.toString())
                })
        }

        val fechaCorta = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val proyecto = minuta.proyecto.ifBlank { "PROYECTO" }
            .replace(Regex("[^A-Za-z0-9À-ÿ ]"), "").trim().take(30)
        val base = "MINUTA - $proyecto - $fechaCorta"

        val uri: Uri? = try {
            if (word) DocxExporter.export(this, base, minuta)
            else PdfExporter.export(this, base, minuta)
        } catch (e: Exception) { null }

        if (uri != null) {
            val mime = if (word) DocxExporter.MIME else "application/pdf"
            AlertDialog.Builder(this)
                .setTitle("Minuta exportada")
                .setMessage("Se guardó \"$base\" en la carpeta Documentos/Minutas del panel.\n\n¿Quieres compartirla o abrirla?")
                .setPositiveButton("Compartir") { _, _ ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "Compartir minuta"))
                }
                .setNeutralButton("Abrir") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (e: Exception) {
                        Toast.makeText(this, "No hay app para abrir este archivo",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
        } else {
            Toast.makeText(this, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Nueva reunión / Ajustes ----------

    private fun confirmarNueva() {
        if (segments.isEmpty() && minutaTexto == null) return
        AlertDialog.Builder(this)
            .setTitle("Nueva reunión")
            .setMessage("Se borrará la transcripción y la minuta actuales. ¿Continuar?")
            .setPositiveButton("Sí, empezar de nuevo") { _, _ ->
                if (recording) stopRecording()
                RecordingService.reset(); minutaTexto = null; elapsedSec = 0
                txtTimer.text = "00:00:00"
                txtTranscript.text = "La transcripción aparecerá aquí…"
                txtMinuta.text = ""
                lblMinuta.visibility = View.GONE
                scrollMinuta.visibility = View.GONE
                btnMinuta.isEnabled = false
                btnWord.isEnabled = false
                btnPdf.isEnabled = false
                editTema.setText("")
                editParticipantes.setText("")
                txtEstado.text = "Listo para grabar"
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarAjustes() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val lblGemini = TextView(this).apply { text = "IA con Gemini (gratis — aistudio.google.com):" }
        val inputGeminiKey = EditText(this).apply {
            hint = "Clave API de Gemini (AIza…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("gemini_key", ""))
        }
        val inputGeminiModel = EditText(this).apply {
            hint = "Modelo Gemini (ej. gemini-flash-latest)"
            setText(prefs.getString("modelo_gemini", "gemini-flash-latest"))
        }
        val lblClaude = TextView(this).apply { text = "IA con Claude (opcional, de pago):" }
        val inputKey = EditText(this).apply {
            hint = "Clave API de Claude (sk-ant-…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("api_key", ""))
        }
        val inputModel = EditText(this).apply {
            hint = "Modelo Claude (ej. claude-sonnet-5)"
            setText(prefs.getString("modelo", "claude-sonnet-5"))
        }
        val inputLang = EditText(this).apply {
            hint = "Idioma de transcripción (ej. es-MX)"
            setText(prefs.getString("idioma", "es-MX"))
        }
        layout.addView(lblGemini); layout.addView(inputGeminiKey); layout.addView(inputGeminiModel)
        layout.addView(lblClaude); layout.addView(inputKey); layout.addView(inputModel)
        layout.addView(inputLang)
        val scroll = ScrollView(this).apply { addView(layout) }

        AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setView(scroll)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit()
                    .putString("gemini_key", inputGeminiKey.text.toString().trim())
                    .putString("modelo_gemini", inputGeminiModel.text.toString().trim()
                        .ifBlank { "gemini-flash-latest" })
                    .putString("api_key", inputKey.text.toString().trim())
                    .putString("modelo", inputModel.text.toString().trim()
                        .ifBlank { "claude-sonnet-5" })
                    .putString("idioma", inputLang.text.toString().trim()
                        .ifBlank { "es-MX" })
                    .apply()
                Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatTime(sec: Long): String =
        String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)

    override fun onDestroy() {
        RecordingService.uiListener = null
        RecordingService.statusListener = null
        super.onDestroy()
    }
}
