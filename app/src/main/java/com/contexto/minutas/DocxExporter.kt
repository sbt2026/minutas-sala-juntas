package com.contexto.minutas

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera el .docx con el formato oficial de minutas de Contexto Arquitectos. */
object DocxExporter {

    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val FILL = "8496B0"
    private const val FILL2 = "D9D9D9"
    private const val FONT = "Century Gothic"

    fun export(context: Context, baseName: String, m: Minuta): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.docx")
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/Minutas")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"), values) ?: return null
        val logo = context.assets.open("logo_gris.png").readBytes()
        context.contentResolver.openOutputStream(uri)?.use { writeDocx(it, m, logo) }
            ?: return null
        return uri
    }

    private fun writeDocx(os: OutputStream, m: Minuta, logo: ByteArray) {
        ZipOutputStream(os).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES.toByteArray())
            zip.entry("_rels/.rels", RELS.toByteArray())
            zip.entry("word/_rels/document.xml.rels", DOC_RELS.toByteArray())
            zip.entry("word/media/logo.png", logo)
            zip.entry("word/header1.xml", hf("hdr").toByteArray())
            zip.entry("word/footer1.xml", hf("ftr").toByteArray())
            zip.entry("word/document.xml", documentXml(m).toByteArray())
        }
    }

    private fun ZipOutputStream.entry(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name)); write(data); closeEntry()
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun run(t: String, bold: Boolean = false, color: String? = null, sz: Int = 18): String {
        val c = if (color != null) """<w:color w:val="$color"/>""" else ""
        val b = if (bold) "<w:b/>" else ""
        return """<w:r><w:rPr><w:rFonts w:ascii="$FONT" w:hAnsi="$FONT"/>$b$c""" +
            """<w:sz w:val="$sz"/></w:rPr><w:t xml:space="preserve">${esc(t)}</w:t></w:r>"""
    }

    private fun p(t: String = "", bold: Boolean = false, color: String? = null,
                  sz: Int = 18, align: String? = null): String {
        val j = if (align != null) """<w:jc w:val="$align"/>""" else ""
        val r = if (t.isNotEmpty()) run(t, bold, color, sz) else ""
        return "<w:p><w:pPr>$j</w:pPr>$r</w:p>"
    }

    private fun tc(content: String, w: Int, fill: String? = null,
                   span: Int = 1, vmerge: String? = null): String {
        var props = """<w:tcW w:w="$w" w:type="dxa"/>"""
        if (span > 1) props += """<w:gridSpan w:val="$span"/>"""
        if (fill != null) props += """<w:shd w:val="clear" w:color="auto" w:fill="$fill"/>"""
        if (vmerge != null) props +=
            if (vmerge == "restart") """<w:vMerge w:val="restart"/>""" else "<w:vMerge/>"
        props += """<w:vAlign w:val="center"/>"""
        return "<w:tc><w:tcPr>$props</w:tcPr>$content</w:tc>"
    }

    private val BORDERS = "<w:tblBorders>" +
        listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") {
            """<w:$it w:val="single" w:sz="4" w:space="0" w:color="808080"/>"""
        } + "</w:tblBorders>"

    private fun tbl(grid: List<Int>, rows: List<String>): String {
        val g = grid.joinToString("") { """<w:gridCol w:w="$it"/>""" }
        return """<w:tbl><w:tblPr><w:tblW w:w="${grid.sum()}" w:type="dxa"/>$BORDERS""" +
            """<w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>$g</w:tblGrid>""" +
            rows.joinToString("") { "<w:tr>$it</w:tr>" } + "</w:tbl>"
    }

    private fun logoDrawing(): String {
        val cx = 1143000; val cy = 503906  // 1.25 in, proporción 338x149
        return """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>""" +
            """<wp:inline distT="0" distB="0" distL="0" distR="0" """ +
            """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">""" +
            """<wp:extent cx="$cx" cy="$cy"/><wp:docPr id="1" name="logo"/>""" +
            """<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
            """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            """<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            """<pic:nvPicPr><pic:cNvPr id="1" name="logo"/><pic:cNvPicPr/></pic:nvPicPr>""" +
            """<pic:blipFill><a:blip r:embed="rIdLogo" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>""" +
            """<a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
            """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>""" +
            """<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""" +
            """</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
    }

    private fun documentXml(m: Minuta): String {
        // Tabla de encabezado
        val w1 = listOf(2000, 5100, 1740)
        val hdrRows = listOf(
            tc(logoDrawing(), w1[0]) +
                tc(p(m.proyecto.uppercase(), bold = true, align = "center", sz = 22), w1[1]) +
                tc(p(), w1[2]),
            tc(p("COORDINADOR:", bold = true), w1[0]) +
                tc(p(m.coordinador), w1[1]) +
                tc(p("FECHA:", bold = true) + p(m.fecha), w1[2], vmerge = "restart"),
            tc(p("TEMA DE REUNIÓN:", bold = true), w1[0]) +
                tc(p(m.tema.uppercase()), w1[1]) + tc(p(), w1[2], vmerge = "cont"),
            tc(p("LUGAR DE REUNIÓN:", bold = true), w1[0]) +
                tc(p(m.lugar), w1[1]) + tc(p(), w1[2], vmerge = "cont")
        )
        val t1 = tbl(w1, hdrRows)

        // Participantes
        val w2 = listOf(3540, 3160, 2140)
        val pRows = mutableListOf(
            tc(p("PARTICIPANTES INVOLUCRADOS", bold = true, color = "FFFFFF",
                align = "center"), w2.sum(), fill = FILL, span = 3),
            tc(p("NOMBRE", bold = true, align = "center"), w2[0], fill = FILL2) +
                tc(p("EMPRESA", bold = true, align = "center"), w2[1], fill = FILL2) +
                tc(p("FIRMA", bold = true, align = "center"), w2[2], fill = FILL2)
        )
        m.participantes.forEach { (n, e) ->
            pRows.add(tc(p(n), w2[0]) + tc(p(e), w2[1]) + tc(p(), w2[2]))
        }
        val t2 = tbl(w2, pRows)

        // Resultados y conclusiones
        val w3 = listOf(540, 4820, 1700, 1780)
        val iRows = mutableListOf(
            tc(p("RESULTADOS Y CONCLUSIONES", bold = true, color = "FFFFFF",
                align = "center"), w3.sum(), fill = FILL, span = 4),
            tc(p("No.", bold = true, align = "center"), w3[0], fill = FILL2) +
                tc(p("ITEM", bold = true, align = "center"), w3[1], fill = FILL2) +
                tc(p("RESPONSABLE", bold = true, align = "center"), w3[2], fill = FILL2) +
                tc(p("ESTATUS", bold = true, align = "center"), w3[3], fill = FILL2)
        )
        val letras = "abcdefghijklmnopqrstuvwxyz"
        m.items.forEachIndexed { i, (item, resp, est) ->
            iRows.add(tc(p("${letras[i % 26]}.", align = "center"), w3[0]) +
                tc(p(item), w3[1]) +
                tc(p(resp, align = "center"), w3[2]) +
                tc(p(est, align = "center"), w3[3]))
        }
        val t3 = tbl(w3, iRows)

        var trans = ""
        if (m.transcripcion.isNotEmpty()) {
            trans = p() + p("TRANSCRIPCIÓN", bold = true, sz = 20) +
                m.transcripcion.joinToString("") { p("• $it", sz = 16) }
        }

        val sect = """<w:sectPr>""" +
            """<w:headerReference w:type="default" r:id="rIdHdr" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>""" +
            """<w:footerReference w:type="default" r:id="rIdFtr" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>""" +
            """<w:pgSz w:w="12240" w:h="15840"/>""" +
            """<w:pgMar w:top="1080" w:right="1440" w:bottom="1080" w:left="1440" """ +
            """w:header="540" w:footer="540"/></w:sectPr>"""

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            "<w:body>" + t1 + p() + t2 + p() + t3 + trans + sect + "</w:body></w:document>"
    }

    private fun hf(tag: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<w:$tag xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
        """<w:p><w:pPr><w:jc w:val="right"/></w:pPr>""" +
        run("MINUTA DE REUNIÓN | CONTEXTO ARQUITECTOS", color = "808080", sz = 14) +
        """</w:p></w:$tag>"""

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Default Extension="png" ContentType="image/png"/>""" +
        """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
        """<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>""" +
        """<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""" +
        """</Types>"""

    private const val RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
        """</Relationships>"""

    private const val DOC_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rIdLogo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/logo.png"/>""" +
        """<Relationship Id="rIdHdr" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>""" +
        """<Relationship Id="rIdFtr" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""" +
        """</Relationships>"""
}
