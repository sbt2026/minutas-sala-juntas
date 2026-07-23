package com.contexto.minutas

/**
 * Genera la minuta SIN inteligencia artificial:
 * clasifica frases por palabras clave (acuerdos, pendientes, próximos pasos)
 * y produce el texto estructurado del formato Contexto Arquitectos.
 */
object LocalMinutes {

    private val reAcuerdo = Regex(
        "\\b(acordamos|se acuerda|acuerdos?|quedamos en|se aprueba|se autoriza|se decide|decidimos|aprobamos|acordado)\\b",
        RegexOption.IGNORE_CASE)

    private val rePendiente = Regex(
        "\\b(pendientes?|tareas?|se compromete|queda en|hay que|debe entregar|va a enviar|enviar[áa]|entregar[áa]|revisar[áa]|preparar[áa]|encargarse|responsable de|entregado)\\b",
        RegexOption.IGNORE_CASE)

    private val reProximo = Regex(
        "\\b(pr[óo]xima (reuni[óo]n|junta|sesi[óo]n)|siguiente (reuni[óo]n|junta)|nos vemos el|se agenda|agendar|dar seguimiento)\\b",
        RegexOption.IGNORE_CASE)

    private val reEntregado = Regex("\\b(entregado|se entreg[óo]|ya se envi[óo])\\b", RegexOption.IGNORE_CASE)

    // Iniciales tipo "SBT", "RP" o nombres propios al inicio de frase
    private val reIniciales = Regex("\\b[A-ZÁÉÍÓÚÑ]{2,4}\\b")

    fun generate(
        proyecto: String, coordinador: String, tema: String, lugar: String,
        fecha: String, participantes: String, segments: List<String>
    ): String {
        val frases = segments
            .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.trim() }
            .filter { it.length > 3 }

        val items = mutableListOf<Triple<String, String, String>>()
        frases.forEach { f ->
            val estatus = when {
                reEntregado.containsMatchIn(f) -> "ENTREGADO"
                reAcuerdo.containsMatchIn(f) -> "ACORDADO"
                rePendiente.containsMatchIn(f) -> "EN PROCESO"
                reProximo.containsMatchIn(f) -> "EN PROCESO"
                else -> null
            }
            if (estatus != null) {
                val resp = reIniciales.findAll(f).map { it.value }
                    .filter { it !in setOf("PDF", "DWG", "PDFS") }
                    .distinct().joinToString(", ").ifBlank { "—" }
                items.add(Triple(f, resp, estatus))
            }
        }

        val sb = StringBuilder()
        sb.appendLine("# MINUTA DE REUNIÓN")
        sb.appendLine("## DATOS")
        sb.appendLine("PROYECTO: ${proyecto.ifBlank { "(sin proyecto)" }}")
        sb.appendLine("COORDINADOR: ${coordinador.ifBlank { "—" }}")
        sb.appendLine("TEMA DE REUNIÓN: ${tema.ifBlank { "—" }}")
        sb.appendLine("LUGAR DE REUNIÓN: ${lugar.ifBlank { "—" }}")
        sb.appendLine("FECHA: $fecha")
        sb.appendLine("## PARTICIPANTES")
        MinutaParser.parseParticipantes(participantes).forEach { (n, e) ->
            sb.appendLine("$n | $e")
        }
        sb.appendLine("## RESULTADOS Y CONCLUSIONES")
        if (items.isEmpty())
            sb.appendLine("No se detectaron acuerdos por palabras clave; revisar transcripción. | — | ")
        else items.forEach { (i, r, s2) -> sb.appendLine("$i | $r | $s2") }
        return sb.toString().trim()
    }
}
