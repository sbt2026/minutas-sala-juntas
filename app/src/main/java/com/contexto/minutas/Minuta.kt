package com.contexto.minutas

/** Datos estructurados de la minuta en el formato Contexto Arquitectos. */
data class Minuta(
    val proyecto: String,
    val coordinador: String,
    val tema: String,
    val lugar: String,
    val fecha: String,
    val participantes: List<Pair<String, String>>,   // nombre, empresa
    val items: List<Triple<String, String, String>>, // ítem, responsable, estatus
    val transcripcion: List<String>
)

object MinutaParser {

    /** "Nombre (Empresa), Nombre (Empresa)" -> pares nombre/empresa */
    fun parseParticipantes(texto: String): List<Pair<String, String>> =
        texto.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map {
                val m = Regex("^(.*?)\\s*\\((.*?)\\)\\s*$").find(it)
                if (m != null) m.groupValues[1].trim() to m.groupValues[2].trim()
                else it to ""
            }

    /** Convierte el texto estructurado de la minuta en un objeto Minuta. */
    fun parse(texto: String, transcripcion: List<String>): Minuta {
        var seccion = ""
        val datos = mutableMapOf<String, String>()
        val participantes = mutableListOf<Pair<String, String>>()
        val items = mutableListOf<Triple<String, String, String>>()

        texto.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("## ") -> seccion = line.removePrefix("## ").uppercase()
                line.startsWith("# ") || line.isBlank() -> {}
                seccion.startsWith("DATOS") -> {
                    val idx = line.indexOf(':')
                    if (idx > 0) datos[line.substring(0, idx).trim().uppercase()] =
                        line.substring(idx + 1).trim()
                }
                seccion.startsWith("PARTICIPANTES") -> {
                    val partes = line.removePrefix("- ").split('|').map { it.trim() }
                    if (partes[0].isNotEmpty() && !partes[0].equals("Nombre", true))
                        participantes.add(partes[0] to partes.getOrElse(1) { "" })
                }
                seccion.startsWith("RESULTADOS") -> {
                    val partes = line.removePrefix("- ").split('|').map { it.trim() }
                    if (partes[0].isNotEmpty() && !partes[0].equals("Item", true) &&
                        !partes[0].equals("Ítem", true))
                        items.add(Triple(partes[0],
                            partes.getOrElse(1) { "—" }.ifBlank { "—" },
                            partes.getOrElse(2) { "" }))
                }
            }
        }
        return Minuta(
            proyecto = datos["PROYECTO"] ?: "",
            coordinador = datos["COORDINADOR"] ?: "",
            tema = datos["TEMA DE REUNIÓN"] ?: datos["TEMA"] ?: "",
            lugar = datos["LUGAR DE REUNIÓN"] ?: datos["LUGAR"] ?: "",
            fecha = datos["FECHA"] ?: "",
            participantes = participantes,
            items = items,
            transcripcion = transcripcion
        )
    }
}
