package com.contexto.minutas

/** Prompt compartido por los clientes de IA (Gemini y Claude). */
object MinutaPrompt {
    fun build(
        proyecto: String, coordinador: String, tema: String, lugar: String,
        fecha: String, participantes: String, transcript: String
    ): String = """
Eres un asistente que redacta minutas de reunión para el despacho Contexto Arquitectos.
A partir de la transcripción, genera la minuta EXACTAMENTE en este formato de texto (sin comentarios adicionales, sin markdown extra):

# MINUTA DE REUNIÓN
## DATOS
PROYECTO: $proyecto
COORDINADOR: $coordinador
TEMA DE REUNIÓN: $tema
LUGAR DE REUNIÓN: $lugar
FECHA: $fecha
## PARTICIPANTES
(una línea por participante con el formato: Nombre | Empresa)
## RESULTADOS Y CONCLUSIONES
(una línea por cada acuerdo, entrega o pendiente relevante con el formato:
Descripción del ítem | Responsable | Estatus
donde Estatus es uno de: ENTREGADO, ACORDADO, EN PROCESO.
El responsable son las iniciales o nombre de quien queda a cargo; usa — si no aplica.)

Participantes indicados: ${participantes.ifBlank { "(no especificados)" }}
Sé fiel a la transcripción; no inventes acuerdos ni nombres.

Transcripción:
$transcript
""".trimIndent()
}
