package com.zfod.fireinspect

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** An image to embed in a sheet. [w]/[h] are the pixel size, used to keep the aspect ratio. */
class ImgRef(val data: ByteArray, val w: Int, val h: Int, val png: Boolean)

/** Style description; identical descriptions share one cell format. */
data class Sty(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val size: Int = 10,
    val color: String = "000000",
    val fill: String? = null,
    val h: String = "center",
    val v: String = "center",
    val wrap: Boolean = false,
    val border: String? = null,
    val numFmt: String? = null,
)

/**
 * Minimal, dependency-free .xlsx writer: inline strings, styles, merged cells, column widths,
 * row heights, frozen header, right-to-left sheets and embedded pictures.
 */
class XlsxBuilder(private val rtl: Boolean, private val baseFill: String, private val baseColor: String) {
    private val fonts = arrayListOf("<font><sz val=\"10\"/><name val=\"Arial\"/></font>")
    private val fills = arrayListOf("<fill><patternFill patternType=\"none\"/></fill>", "<fill><patternFill patternType=\"gray125\"/></fill>")
    private val borders = arrayListOf("<border><left/><right/><top/><bottom/><diagonal/></border>")
    private val numFmts = ArrayList<String>()
    private val xfs = arrayListOf("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
    private val xfKeys = HashMap<Sty, Int>()
    val sheets = ArrayList<Sheet>()
    private val media = ArrayList<ImgRef>()

    val base: Sty get() = Sty(color = baseColor, fill = baseFill)

    private fun idx(list: ArrayList<String>, xml: String): Int {
        val i = list.indexOf(xml)
        if (i >= 0) return i
        list.add(xml)
        return list.size - 1
    }

    /** Returns the cell-format index for [s]. */
    fun style(s: Sty): Int = xfKeys.getOrPut(s) {
        val font = idx(fonts, "<font>${if (s.bold) "<b/>" else ""}${if (s.italic) "<i/>" else ""}<sz val=\"${s.size}\"/><color rgb=\"FF${s.color}\"/><name val=\"Arial\"/></font>")
        val fill = if (s.fill == null) 0 else idx(fills, "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF${s.fill}\"/><bgColor indexed=\"64\"/></patternFill></fill>")
        val border = if (s.border == null) 0 else {
            val c = "<color rgb=\"FF${s.border}\"/>"
            idx(borders, "<border><left style=\"thin\">$c</left><right style=\"thin\">$c</right><top style=\"thin\">$c</top><bottom style=\"thin\">$c</bottom><diagonal/></border>")
        }
        var nf = 0
        if (s.numFmt != null) {
            val i = numFmts.indexOf(s.numFmt).let { if (it >= 0) it else { numFmts.add(s.numFmt); numFmts.size - 1 } }
            nf = 164 + i
        }
        val align = "<alignment horizontal=\"${s.h}\" vertical=\"${s.v}\"${if (s.wrap) " wrapText=\"1\"" else ""}/>"
        xfs.add("<xf numFmtId=\"$nf\" fontId=\"$font\" fillId=\"$fill\" borderId=\"$border\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"${if (nf != 0) " applyNumberFormat=\"1\"" else ""}>$align</xf>")
        xfs.size - 1
    }

    fun sheet(name: String, tab: String): Sheet {
        val s = Sheet(this, name, tab)
        sheets.add(s)
        return s
    }

    private fun mediaIndex(img: ImgRef): Int {
        val i = media.indexOfFirst { it === img }
        if (i >= 0) return i
        media.add(img)
        return media.size - 1
    }

    class Cell(val value: Any?, val style: Int)
    class Anchor(val img: ImgRef, val col: Int, val row: Int, val colOff: Long, val rowOff: Long, val cx: Long, val cy: Long)

    class Sheet(private val book: XlsxBuilder, val name: String, val tab: String) {
        val rows = java.util.TreeMap<Int, java.util.TreeMap<Int, Cell>>()
        val merges = ArrayList<String>()
        val colWidths = HashMap<Int, Double>()
        val rowHeights = HashMap<Int, Double>()
        val anchors = ArrayList<Anchor>()
        var freezeRows = 0
        var lastCol = 0

        fun set(r: Int, c: Int, v: Any?, style: Int) {
            rows.getOrPut(r) { java.util.TreeMap() }[c] = Cell(v, style)
            if (c > lastCol) lastCol = c
        }

        /** Merges the range and fills every covered cell with [style] so borders/fills look right. */
        fun merge(r1: Int, c1: Int, r2: Int, c2: Int, v: Any?, style: Int) {
            for (r in r1..r2) for (c in c1..c2) set(r, c, null, style)
            set(r1, c1, v, style)
            if (r1 != r2 || c1 != c2) merges.add("${ref(c1, r1)}:${ref(c2, r2)}")
        }

        fun width(c: Int, w: Double) { colWidths[c] = w; if (c > lastCol) lastCol = c }
        fun height(r: Int, h: Double) { rowHeights[r] = h }

        /** Places [img] fitted inside a box of [boxW]×[boxH] pixels at the cell (col,row), centred. */
        fun image(img: ImgRef, col: Int, row: Int, boxW: Int, boxH: Int, colWidthPx: Int? = null, rowHeightPx: Int? = null) {
            val scale = minOf(boxW.toDouble() / img.w, boxH.toDouble() / img.h)
            val wPx = (img.w * scale).toInt().coerceAtLeast(1)
            val hPx = (img.h * scale).toInt().coerceAtLeast(1)
            val offX = if (colWidthPx != null) ((colWidthPx - wPx) / 2).coerceAtLeast(0) else 0
            val offY = if (rowHeightPx != null) ((rowHeightPx - hPx) / 2).coerceAtLeast(0) else 0
            anchors.add(Anchor(img, col, row, offX * 9525L, offY * 9525L, wPx * 9525L, hPx * 9525L))
        }

        fun toXml(hasDrawing: Boolean): String {
            val sb = StringBuilder(32 * 1024)
            val maxRow = rows.lastKey()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            sb.append("<sheetPr><tabColor rgb=\"FF$tab\"/><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
            sb.append("<dimension ref=\"A1:${ref(lastCol, maxRow)}\"/>")
            sb.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"${if (book.rtl) " rightToLeft=\"1\"" else ""} zoomScale=\"90\" zoomScaleNormal=\"90\">")
            if (freezeRows > 0) {
                sb.append("<pane ySplit=\"$freezeRows\" topLeftCell=\"A${freezeRows + 1}\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\"/>")
            }
            sb.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>")
            val baseStyle = book.style(book.base)
            sb.append("<cols>")
            for (c in 0..lastCol) {
                val w = colWidths[c] ?: 12.0
                sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$w\" style=\"$baseStyle\" customWidth=\"1\"/>")
            }
            sb.append("<col min=\"${lastCol + 2}\" max=\"16384\" width=\"9\" style=\"$baseStyle\"/>")
            sb.append("</cols><sheetData>")
            for ((r, cells) in rows) {
                val ht = rowHeights[r]
                sb.append("<row r=\"${r + 1}\"")
                if (ht != null) sb.append(" ht=\"$ht\" customHeight=\"1\"")
                sb.append(">")
                for ((c, cell) in cells) {
                    val rf = ref(c, r)
                    when (val v = cell.value) {
                        null -> sb.append("<c r=\"$rf\" s=\"${cell.style}\"/>")
                        is Number -> sb.append("<c r=\"$rf\" s=\"${cell.style}\"><v>${numStr(v)}</v></c>")
                        else -> {
                            val t = xml(v.toString())
                            sb.append("<c r=\"$rf\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">$t</t></is></c>")
                        }
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData>")
            if (merges.isNotEmpty()) {
                sb.append("<mergeCells count=\"${merges.size}\">")
                for (m in merges) sb.append("<mergeCell ref=\"$m\"/>")
                sb.append("</mergeCells>")
            }
            sb.append("<pageMargins left=\"0.3\" right=\"0.3\" top=\"0.4\" bottom=\"0.4\" header=\"0.2\" footer=\"0.2\"/>")
            sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
            if (hasDrawing) sb.append("<drawing r:id=\"rId1\"/>")
            sb.append("</worksheet>")
            return sb.toString()
        }
    }

    fun toBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, text: String) = put(z, name, text.toByteArray(Charsets.UTF_8))

            // register media in a stable order
            val drawingSheets = sheets.withIndex().filter { it.value.anchors.isNotEmpty() }
            for ((_, s) in drawingSheets) for (a in s.anchors) mediaIndex(a.img)

            val ct = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            ct.append("<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>")
            ct.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            ct.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            sheets.forEachIndexed { i, _ ->
                ct.append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            }
            drawingSheets.forEach { (i, _) ->
                ct.append("<Override PartName=\"/xl/drawings/drawing${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())

            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")

            val wb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><bookViews><workbookView/></bookViews><sheets>")
            sheets.forEachIndexed { i, s -> wb.append("<sheet name=\"${xmlAttr(sheetName(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
            wb.append("</sheets></workbook>")
            put("xl/workbook.xml", wb.toString())

            val wr = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            sheets.forEachIndexed { i, _ ->
                wr.append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>")
            }
            wr.append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            put("xl/_rels/workbook.xml.rels", wr.toString())

            // sheets must be generated before styles are written (styles are registered while writing cells)
            sheets.forEachIndexed { i, s ->
                put("xl/worksheets/sheet${i + 1}.xml", s.toXml(s.anchors.isNotEmpty()))
                if (s.anchors.isNotEmpty()) {
                    put("xl/worksheets/_rels/sheet${i + 1}.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing${i + 1}.xml\"/></Relationships>")
                    val d = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                    val rel = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                    s.anchors.forEachIndexed { k, a ->
                        val mi = mediaIndex(a.img) + 1
                        val ext = if (a.img.png) "png" else "jpeg"
                        rel.append("<Relationship Id=\"rId${k + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image$mi.$ext\"/>")
                        d.append("<xdr:oneCellAnchor><xdr:from><xdr:col>${a.col}</xdr:col><xdr:colOff>${a.colOff}</xdr:colOff><xdr:row>${a.row}</xdr:row><xdr:rowOff>${a.rowOff}</xdr:rowOff></xdr:from>")
                        d.append("<xdr:ext cx=\"${a.cx}\" cy=\"${a.cy}\"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"${k + 2}\" name=\"Picture ${k + 1}\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                        d.append("<xdr:blipFill><a:blip r:embed=\"rId${k + 1}\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                        d.append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"${a.cx}\" cy=\"${a.cy}\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
                    }
                    d.append("</xdr:wsDr>")
                    rel.append("</Relationships>")
                    put("xl/drawings/drawing${i + 1}.xml", d.toString())
                    put("xl/drawings/_rels/drawing${i + 1}.xml.rels", rel.toString())
                }
            }
            media.forEachIndexed { i, m -> put(z, "xl/media/image${i + 1}.${if (m.png) "png" else "jpeg"}", m.data) }

            val st = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            if (numFmts.isNotEmpty()) {
                st.append("<numFmts count=\"${numFmts.size}\">")
                numFmts.forEachIndexed { i, f -> st.append("<numFmt numFmtId=\"${164 + i}\" formatCode=\"${xmlAttr(f)}\"/>") }
                st.append("</numFmts>")
            }
            st.append("<fonts count=\"${fonts.size}\">${fonts.joinToString("")}</fonts>")
            st.append("<fills count=\"${fills.size}\">${fills.joinToString("")}</fills>")
            st.append("<borders count=\"${borders.size}\">${borders.joinToString("")}</borders>")
            st.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
            st.append("<cellXfs count=\"${xfs.size}\">${xfs.joinToString("")}</cellXfs>")
            st.append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
            put("xl/styles.xml", st.toString())
        }
        return bos.toByteArray()
    }

    private fun put(z: ZipOutputStream, name: String, data: ByteArray) {
        z.putNextEntry(ZipEntry(name))
        z.write(data)
        z.closeEntry()
    }

    companion object {
        fun colName(c: Int): String {
            var n = c
            val sb = StringBuilder()
            do {
                sb.insert(0, ('A' + n % 26))
                n = n / 26 - 1
            } while (n >= 0)
            return sb.toString()
        }

        fun ref(c: Int, r: Int) = "${colName(c)}${r + 1}"

        fun numStr(n: Number): String = when (n) {
            is Int, is Long -> n.toString()
            else -> {
                val d = n.toDouble()
                if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
            }
        }

        fun xml(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (ch in s) {
                when {
                    ch == '&' -> sb.append("&amp;")
                    ch == '<' -> sb.append("&lt;")
                    ch == '>' -> sb.append("&gt;")
                    ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> {}
                    ch.code == 0xFFFE || ch.code == 0xFFFF -> {}
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun xmlAttr(s: String): String = xml(s).replace("\"", "&quot;")

        fun sheetName(s: String): String {
            val clean = s.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim()
            return (if (clean.isEmpty()) "Sheet" else clean).take(31)
        }
    }
}
