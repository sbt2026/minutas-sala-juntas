package com.contexto.minutas

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Exporta la minuta como PDF carta con el formato Contexto Arquitectos. */
object PdfExporter {

    private const val PAGE_W = 612   // carta en puntos
    private const val PAGE_H = 792
    private const val MARGIN = 54f
    private const val PAD = 5f
    private val GRAY_HEADER = Color.rgb(0x84, 0x96, 0xB0)
    private val GRAY_LIGHT = Color.rgb(0xD9, 0xD9, 0xD9)
    private val GRAY_LINE = Color.rgb(0x80, 0x80, 0x80)

    fun export(context: Context, baseName: String, m: Minuta): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/Minutas")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"), values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { os ->
            Builder(context, m).build().writeTo(os)
        } ?: return null
        return uri
    }

    private class Builder(val context: Context, val m: Minuta) {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        var y = MARGIN
        var pageNum = 0

        val normal = TextPaint().apply { color = Color.BLACK; textSize = 9f; isAntiAlias = true }
        val bold = TextPaint().apply {
            color = Color.BLACK; textSize = 9f; isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val boldWhite = TextPaint().apply {
            color = Color.WHITE; textSize = 9f; isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val small = TextPaint().apply { color = Color.rgb(0x80,0x80,0x80); textSize = 7f; isAntiAlias = true }
        val fill = Paint().apply { style = Paint.Style.FILL }
        val line = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 0.7f; color = GRAY_LINE
        }

        fun build(): PdfDocument {
            newPage()
            drawHeaderTable()
            y += 12f
            drawParticipantes()
            y += 12f
            drawItems()
            if (m.transcripcion.isNotEmpty()) {
                y += 14f
                drawParagraph("TRANSCRIPCIÓN", bold, 10f)
                m.transcripcion.forEach { drawParagraph("• $it", normal, 8f) }
            }
            page?.let { doc.finishPage(it) }
            return doc
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            y = MARGIN
            // encabezado de página
            val c = page!!.canvas
            val t = "MINUTA DE REUNIÓN | CONTEXTO ARQUITECTOS"
            c.drawText(t, PAGE_W - MARGIN - small.measureText(t), MARGIN - 20f, small)
            c.drawText(t, PAGE_W - MARGIN - small.measureText(t), PAGE_H - MARGIN + 26f, small)
        }

        fun ensure(h: Float) { if (y + h > PAGE_H - MARGIN) newPage() }

        fun layoutFor(text: String, paint: TextPaint, width: Float,
                      align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(10))
                .setAlignment(align).setLineSpacing(1.5f, 1f).build()

        /** Dibuja una fila de tabla; cells = (texto, paint, alineación), fondo opcional. */
        fun row(x: Float, widths: List<Float>, cells: List<Triple<String, TextPaint, Layout.Alignment>>,
                bg: Int? = null, minH: Float = 0f): Float {
            val layouts = cells.mapIndexed { i, (t, p, a) -> layoutFor(t, p, widths[i] - 2 * PAD, a) }
            val h = maxOf(layouts.maxOf { it.height.toFloat() } + 2 * PAD, minH)
            ensure(h)
            val c = page!!.canvas
            var cx = x
            layouts.forEachIndexed { i, lay ->
                val r = RectF(cx, y, cx + widths[i], y + h)
                if (bg != null) { fill.color = bg; c.drawRect(r, fill) }
                c.drawRect(r, line)
                c.save()
                c.translate(cx + PAD, y + (h - lay.height) / 2f)
                lay.draw(c)
                c.restore()
                cx += widths[i]
            }
            y += h
            return h
        }

        fun drawParagraph(text: String, paint: TextPaint, spacing: Float) {
            val lay = layoutFor(text, paint, PAGE_W - 2 * MARGIN)
            ensure(lay.height + spacing)
            val c = page!!.canvas
            c.save(); c.translate(MARGIN, y); lay.draw(c); c.restore()
            y += lay.height + spacing
        }

        val L = Layout.Alignment.ALIGN_NORMAL
        val C = Layout.Alignment.ALIGN_CENTER

        fun drawHeaderTable() {
            val total = PAGE_W - 2 * MARGIN
            val w = listOf(total * 0.23f, total * 0.57f, total * 0.20f)
            val c = page!!.canvas

            // fila 1: logo + proyecto
            val logoBmp = BitmapFactory.decodeStream(context.assets.open("logo_gris.png"))
            val rowH = 46f
            ensure(rowH * 4)
            val topY = y
            // celdas fila 1
            fill.color = Color.WHITE
            var cx = MARGIN
            listOf(w[0], w[1], w[2]).forEach { ww ->
                c.drawRect(RectF(cx, y, cx + ww, y + rowH), line); cx += ww
            }
            // logo centrado en celda 1
            val lw = w[0] - 14f; val lh = lw * 149f / 338f
            c.drawBitmap(logoBmp, null,
                RectF(MARGIN + 7f, y + (rowH - lh) / 2f, MARGIN + 7f + lw, y + (rowH + lh) / 2f), null)
            // proyecto centrado
            val proj = layoutFor(m.proyecto.uppercase(), bold, w[1] - 2 * PAD, C)
            c.save(); c.translate(MARGIN + w[0] + PAD, y + (rowH - proj.height) / 2f)
            proj.draw(c); c.restore()
            y += rowH

            // filas 2-4 con FECHA fusionada a la derecha
            val datos = listOf(
                "COORDINADOR:" to m.coordinador,
                "TEMA DE REUNIÓN:" to m.tema.uppercase(),
                "LUGAR DE REUNIÓN:" to m.lugar)
            val fechaTop = y
            datos.forEach { (k, v) ->
                val lay1 = layoutFor(k, bold, w[0] - 2 * PAD)
                val lay2 = layoutFor(v, normal, w[1] - 2 * PAD)
                val h = maxOf(lay1.height, lay2.height) + 2 * PAD
                c.drawRect(RectF(MARGIN, y, MARGIN + w[0], y + h), line)
                c.drawRect(RectF(MARGIN + w[0], y, MARGIN + w[0] + w[1], y + h), line)
                c.save(); c.translate(MARGIN + PAD, y + PAD); lay1.draw(c); c.restore()
                c.save(); c.translate(MARGIN + w[0] + PAD, y + PAD); lay2.draw(c); c.restore()
                y += h
            }
            // celda FECHA fusionada
            val fx = MARGIN + w[0] + w[1]
            c.drawRect(RectF(fx, fechaTop, fx + w[2], y), line)
            val fl = layoutFor("FECHA:\n${m.fecha}", bold, w[2] - 2 * PAD)
            c.save(); c.translate(fx + PAD, fechaTop + PAD); fl.draw(c); c.restore()
        }

        fun drawParticipantes() {
            val total = PAGE_W - 2 * MARGIN
            val w = listOf(total * 0.40f, total * 0.36f, total * 0.24f)
            row(MARGIN, listOf(total),
                listOf(Triple("PARTICIPANTES INVOLUCRADOS", boldWhite, C)), bg = GRAY_HEADER)
            row(MARGIN, w, listOf(
                Triple("NOMBRE", bold, C), Triple("EMPRESA", bold, C),
                Triple("FIRMA", bold, C)), bg = GRAY_LIGHT)
            m.participantes.forEach { (n, e) ->
                row(MARGIN, w, listOf(
                    Triple(n, normal, L), Triple(e, normal, L), Triple("", normal, L)),
                    minH = 22f)
            }
        }

        fun drawItems() {
            val total = PAGE_W - 2 * MARGIN
            val w = listOf(total * 0.06f, total * 0.55f, total * 0.19f, total * 0.20f)
            row(MARGIN, listOf(total),
                listOf(Triple("RESULTADOS Y CONCLUSIONES", boldWhite, C)), bg = GRAY_HEADER)
            row(MARGIN, w, listOf(
                Triple("No.", bold, C), Triple("ITEM", bold, C),
                Triple("RESPONSABLE", bold, C), Triple("ESTATUS", bold, C)), bg = GRAY_LIGHT)
            val letras = "abcdefghijklmnopqrstuvwxyz"
            m.items.forEachIndexed { i, (item, resp, est) ->
                row(MARGIN, w, listOf(
                    Triple("${letras[i % 26]}.", normal, C), Triple(item, normal, L),
                    Triple(resp, normal, C), Triple(est, normal, C)))
            }
        }
    }
}
