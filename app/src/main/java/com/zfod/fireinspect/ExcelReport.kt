package com.zfod.fireinspect

import java.util.Locale

/** Builds the .xlsx export (same content and look as the HTML report, one sheet per shift + dashboard). */
class ExcelReport(
    cabinets: List<Cabinet>,
    standard: List<Int>,
    private val opt: ReportOptions,
    private val zfod: ImgRef?,
    private val eni: ImgRef?,
    private val photo: (String) -> ImgRef?,
) {
    private val an = Analysis(cabinets, standard)
    private val rtl = opt.lang == Lang.AR
    private val th = opt.theme

    private fun hex(s: String) = s.removePrefix("#").uppercase(Locale.US)
    private val page = hex(th.page)
    private val headC = hex(th.head)
    private val line = hex(th.line)
    private val card = hex(th.card)
    private val muted = hex(th.muted)
    private val accent = hex(th.accent)
    private val txt = hex(opt.textColor)
    private val badFill = if (th.dark) "4A1F2A" else "FDE2E2"
    private val book = XlsxBuilder(rtl, page, txt)

    private fun t(k: String) = Tr.get(opt.lang, k)
    private fun tf(k: String, vararg a: Any) = Tr.f(opt.lang, k, *a)

    private fun s(
        bold: Boolean = false, size: Int = 10, color: String = txt, fill: String? = page, h: String = "center",
        wrap: Boolean = false, border: String? = null, numFmt: String? = null, italic: Boolean = false,
    ) = book.style(Sty(bold = bold, italic = italic, size = size, color = color, fill = fill, h = h, wrap = wrap, border = border, numFmt = numFmt))

    private val sTitle get() = s(bold = true, size = 16)
    private val sSub get() = s(size = 9, color = muted)
    private val sHead get() = s(bold = true, size = 8, fill = headC, wrap = true, border = line)
    private val sCell get() = s(border = line, size = 9)
    private val sCellL get() = s(border = line, size = 9, h = if (rtl) "right" else "left", wrap = true)
    private val sTot get() = s(bold = true, border = line, fill = headC, size = 9)
    private val sTotL get() = s(bold = true, border = line, fill = headC, size = 9, h = if (rtl) "right" else "left")
    private val sSec get() = s(bold = true, size = 11, color = accent, h = if (rtl) "right" else "left")
    private val sNote get() = s(size = 8, color = muted, italic = true, h = if (rtl) "right" else "left", wrap = true)

    fun build(): ByteArray {
        if (opt.overview) dashboard()
        for (sh in opt.shifts) shiftSheet(sh)
        if (book.sheets.isEmpty()) book.sheet(t("no_data"), accent).set(0, 0, t("no_data"), s())
        return book.toBytes()
    }

    // ---- shared pieces ---------------------------------------------------------------------------
    private fun banner(sh: XlsxBuilder.Sheet, lastCol: Int, subtitle: String) {
        sh.height(0, 58.0)
        sh.height(1, 18.0)
        sh.merge(0, 1, 0, lastCol - 1, t("report_name"), sTitle)
        sh.set(0, 0, null, s())
        sh.set(0, lastCol, null, s())
        sh.merge(1, 0, 1, lastCol, subtitle, sSub)
        val w0 = (sh.colWidths[0] ?: 12.0)
        val wl = (sh.colWidths[lastCol] ?: 12.0)
        // logos stay fixed on screen: ZFOD on the left, ENI on the right (RTL sheets are mirrored)
        val zCol = if (rtl) lastCol else 0
        val eCol = if (rtl) 0 else lastCol
        val zW = if (rtl) wl else w0
        val eW = if (rtl) w0 else wl
        zfod?.let { sh.image(it, zCol, 0, 70, 70, colWidthPx = (zW * 7 + 5).toInt(), rowHeightPx = 77) }
        eni?.let { sh.image(it, eCol, 0, 70, 70, colWidthPx = (eW * 7 + 5).toInt(), rowHeightPx = 77) }
    }

    private fun kpis(sh: XlsxBuilder.Sheet, row: Int, lastCol: Int, st: ShiftStats) {
        val items = listOf(
            Triple(t("k_total"), st.total, "3B82F6"), Triple(t("k_ok"), st.ok, "22C55E"), Triple(t("k_faulty"), st.faulty, "EF4444"),
            Triple(t("k_na"), st.na, "94A3B8"), Triple(t("k_pending"), st.pending, "A855F7"), Triple(t("k_rate"), st.rate, "06B6D4"),
        )
        val n = lastCol + 1
        sh.height(row, 18.0)
        sh.height(row + 1, 30.0)
        var start = 0
        items.forEachIndexed { i, (label, value, color) ->
            val end = if (i == items.size - 1) lastCol else (n * (i + 1) / items.size) - 1
            sh.merge(row, start, row, end, label, s(bold = true, size = 8, color = color, fill = card, border = color))
            val fmt = if (value is Double) "0.0%" else null
            sh.merge(row + 1, start, row + 1, end, value as Number, s(bold = true, size = 18, fill = card, border = color, numFmt = fmt))
            start = end + 1
        }
    }

    private fun statusStyle(st: Status, fill: String?): Int = when (st) {
        Status.OK -> s(bold = true, size = 9, color = "22C55E", fill = fill, border = line)
        Status.NO -> s(bold = true, size = 9, color = "EF4444", fill = fill, border = line)
        Status.NA -> s(bold = true, size = 9, color = "94A3B8", fill = fill, border = line)
    }

    private fun statusText(st: Status) = when (st) {
        Status.OK -> t("st_ok"); Status.NO -> t("st_no"); Status.NA -> t("st_na")
    }

    private fun xlLines(text: String, cpl: Int): Int =
        if (text.isBlank()) 1 else text.trim().split(Regex("\\s+")).fold(1 to 0) { (ln, used), w ->
            val len = w.length
            when {
                used == 0 -> (ln + (len - 1) / cpl) to (((len - 1) % cpl) + 1)
                used + 1 + len <= cpl -> ln to (used + 1 + len)
                else -> (ln + 1 + (len - 1) / cpl) to (((len - 1) % cpl) + 1)
            }
        }.first

    // ---- shift sheet ---------------------------------------------------------------------------------
    private fun shiftSheet(shift: Char) {
        val rows = an.rows(shift)
        val st = an.stats(shift)
        val ph = opt.photos
        val lastCol = if (ph) 19 else 16
        val sh = book.sheet(tf("shift_x", shift), hex(SHIFT_COLORS[shift] ?: "#3B82F6"))
        val widths = listOf(12.0, 8.0, 12.0, 12.0, 26.0) + List(EQ_COUNT) { 9.0 } + listOf(12.0, 8.0, 34.0, 16.0)
        widths.forEachIndexed { i, w -> sh.width(i, w) }
        if (ph) for (c in 17..19) sh.width(c, 17.0)

        val days = rows.mapNotNull { it.epochDay }
        val dateTxt = when {
            days.isEmpty() -> "—"
            days.min() == days.max() -> Fmt.date(days.min())
            else -> Fmt.date(days.min()) + " → " + Fmt.date(days.max())
        }
        banner(sh, lastCol, "${tf("shift_x", shift)}  ·  ${t("insp_date")}: $dateTxt  ·  ${t("insp_time")}: ${Fmt.time(st.lastTimeMin).ifEmpty { "—" }}  ·  ${t("printed")}: ${opt.printedDate} ${opt.printedTime}")
        sh.height(2, 6.0)
        kpis(sh, 3, lastCol, st)
        sh.height(5, 6.0)

        // table header
        val hr = 6
        sh.height(hr, 34.0)
        val heads = ArrayList<String>()
        heads += listOf("h_tag", "h_shift", "h_hydrant", "h_hstatus", "h_cabstatus").map { t(it) }
        for (k in 0 until EQ_COUNT) heads += ReportText.eqShort(opt.lang, k)
        heads += listOf("h_date", "h_time", "h_remarks", "h_location").map { t(it) }
        if (ph) { heads += t("h_photos"); heads += ""; heads += "" }
        heads.forEachIndexed { c, h -> if (!(ph && c >= 18)) sh.set(hr, c, h, sHead) }
        if (ph) sh.merge(hr, 17, hr, 19, t("h_photos"), sHead)
        sh.freezeRows = hr + 1

        var r = hr + 1
        for (c in rows) {
            val bad = c.status == Status.NO
            val fill = if (bad) badFill else page
            val hasPh = ph && c.hasPhotos
            val xl = maxOf(xlLines(c.remarks, 36), xlLines(c.notes.ifBlank { "// //" }, 26), xlLines(c.location, 15)).coerceIn(1, 10)
            val textH = if (xl <= 1) 20.0 else xl * 12.0 + 6.0
            sh.height(r, if (hasPh) maxOf(66.0, textH) else textH)
            val cell = s(border = line, size = 9, fill = fill)
            val cellL = s(border = line, size = 9, fill = fill, h = if (rtl) "right" else "left", wrap = true)
            sh.set(r, 0, c.tagBox, s(bold = true, border = line, size = 9, fill = fill))
            sh.set(r, 1, c.shift.toString(), s(bold = true, border = line, size = 9, fill = fill, color = hex(SHIFT_COLORS[c.shift] ?: "#3B82F6")))
            sh.set(r, 2, c.hydrantTag, cell)
            sh.set(r, 3, statusText(c.status), statusStyle(c.status, fill))
            sh.set(r, 4, c.notes.ifBlank { "// //" } + if (bad) "  ▲" else "", cellL)
            for (k in 0 until EQ_COUNT) {
                val n = c.counts[k]
                val low = n != null && n < an.standard[k]
                sh.set(r, 5 + k, n, if (low) s(bold = true, border = line, size = 9, fill = fill, color = "EF4444") else cell)
            }
            sh.set(r, 13, c.epochDay?.let { it + 25569 }, s(border = line, size = 9, fill = fill, numFmt = "yyyy-mm-dd"))
            sh.set(r, 14, c.timeMin?.let { it / 1440.0 }, s(border = line, size = 9, fill = fill, numFmt = "hh:mm"))
            sh.set(r, 15, c.remarks, cellL)
            sh.set(r, 16, c.location, cellL)
            if (ph) {
                for (p in 0 until PHOTO_SLOTS) {
                    sh.set(r, 17 + p, null, cell)
                    val img = c.photos[p].takeIf { it.isNotEmpty() }?.let { photo(it) }
                    if (img != null) sh.image(img, 17 + p, r, 112, 82, colWidthPx = (17 * 7 + 5), rowHeightPx = 88)
                }
            }
            r++
        }
        // totals
        sh.merge(r, 0, r, 4, t("totals"), sTotL)
        for (k in 0 until EQ_COUNT) sh.set(r, 5 + k, rows.sumOf { it.counts[k] ?: 0 }, sTot)
        sh.merge(r, 13, r, lastCol, null, sTot)
        sh.height(r, 20.0)
        r += 2

        // analysis
        sh.merge(r, 0, r, 12, tf("shift_analysis", shift), sSec)
        r++
        val top = r
        sh.height(r, 28.0)
        sh.merge(r, 0, r, 3, t("fault_type"), sHead)
        sh.set(r, 4, t("cabinets"), sHead)
        sh.merge(r, 5, r, 8, t("equipment"), sHead)
        sh.set(r, 9, t("in_stock"), sHead)
        sh.set(r, 10, t("standard"), sHead)
        sh.set(r, 11, t("short"), sHead)
        sh.merge(r, 13, r, 14, t("rate_title"), sHead)
        sh.set(r, 15, "", sHead)
        r++
        for ((i, f) in FAULT_TYPES.withIndex()) {
            sh.merge(top + 1 + i, 0, top + 1 + i, 3, ReportText.faultName(opt.lang, f.key), sCellL)
            sh.set(top + 1 + i, 4, an.faultCount(shift, f), sCell)
        }
        val oi = top + 1 + FAULT_TYPES.size
        sh.merge(oi, 0, oi, 3, t("f_other"), sCellL); sh.set(oi, 4, an.otherCount(shift), sCell)
        sh.merge(oi + 1, 0, oi + 1, 3, t("remarks_logged"), sTotL); sh.set(oi + 1, 4, st.remarks, sTot)
        for (k in 0 until EQ_COUNT) {
            val rr = top + 1 + k
            sh.merge(rr, 5, rr, 8, ReportText.eqLong(opt.lang, k), sCellL)
            sh.set(rr, 9, an.inStock(shift, k), sCell)
            sh.set(rr, 10, an.standard[k], sCell)
            val sv = an.short(shift, k)
            sh.set(rr, 11, sv, s(bold = true, border = line, size = 9, color = if (sv > 0) "EF4444" else "22C55E"))
        }
        val rateRows = listOf(
            Triple(t("st_ok"), st.ok, "22C55E"), Triple(t("st_no"), st.faulty, "EF4444"), Triple(t("st_na"), st.na, "94A3B8"),
            Triple(t("k_pending"), st.pending, "A855F7"), Triple(t("k_rate"), st.rate, "06B6D4"),
        )
        rateRows.forEachIndexed { i, (label, v, color) ->
            val rr = top + 1 + i
            sh.merge(rr, 13, rr, 14, label, s(bold = true, size = 9, color = color, border = line, h = if (rtl) "right" else "left"))
            sh.set(rr, 15, v as Number, s(bold = true, border = line, size = 9, numFmt = if (v is Double) "0.0%" else null))
        }
        r = oi + 2
        sh.height(r, 28.0)
        sh.merge(r, 0, r, 12, t("short_note"), sNote)
        r += 2
        sh.merge(r, 0, r, lastCol, t("footer"), s(size = 8, color = muted))
    }

    // ---- dashboard sheet ---------------------------------------------------------------------------------
    private fun dashboard() {
        val sh = book.sheet(t("dash_title"), accent)
        val lastCol = 10
        sh.width(0, 30.0)
        for (c in 1..9) sh.width(c, 13.0)
        sh.width(10, 16.0)
        banner(sh, lastCol, "${t("overview_sub")}  ·  ${t("printed")}: ${opt.printedDate} ${opt.printedTime}")
        sh.height(2, 6.0)
        val tot = an.stats(null)
        sh.merge(2, 0, 2, lastCol, t("overview"), sSec)
        kpis(sh, 3, lastCol, tot)
        var r = 6
        sh.height(r, 8.0)
        r++

        // comparison
        sh.merge(r, 0, r, lastCol, t("shift_comparison"), sSec); r++
        sh.height(r, 28.0)
        listOf("c_shift", "cabinets", "st_ok", "st_no", "st_na", "k_pending", "operational_pct", "fault_pct", "last_insp", "h_remarks", "health")
            .forEachIndexed { c, k -> sh.set(r, c, t(k), sHead) }
        r++
        fun cmp(label: String, st: ShiftStats, color: String?, total: Boolean) {
            val base = if (total) sTot else sCell
            sh.set(r, 0, label, if (color != null) s(bold = true, border = line, size = 9, color = color, h = if (rtl) "right" else "left") else sTotL)
            sh.set(r, 1, st.total, base); sh.set(r, 2, st.ok, base); sh.set(r, 3, st.faulty, base); sh.set(r, 4, st.na, base); sh.set(r, 5, st.pending, base)
            sh.set(r, 6, st.rate, s(bold = total, border = line, size = 9, fill = if (total) headC else page, numFmt = "0.0%"))
            sh.set(r, 7, st.faultRate, s(bold = total, border = line, size = 9, fill = if (total) headC else page, numFmt = "0.0%"))
            sh.set(r, 8, st.lastDay?.let { it + 25569 }, s(bold = total, border = line, size = 9, fill = if (total) headC else page, numFmt = "yyyy-mm-dd"))
            sh.set(r, 9, st.remarks, base)
            sh.set(r, 10, if (st.faulty > 0) tf("n_faults", st.faulty) else t("healthy"),
                s(bold = true, border = line, size = 9, fill = if (total) headC else page, color = if (st.faulty > 0) "EF4444" else "22C55E"))
            sh.height(r, 20.0)
            r++
        }
        for (x in SHIFTS) cmp(tf("shift_x", x), an.stats(x), hex(SHIFT_COLORS[x] ?: "#3B82F6"), false)
        cmp(t("total_w"), tot, null, true)
        r++

        // faults matrix
        sh.merge(r, 0, r, lastCol, t("faults_title"), sSec); r++
        sh.height(r, 24.0)
        sh.set(r, 0, t("fault_type"), sHead)
        SHIFTS.forEachIndexed { i, x -> sh.set(r, 1 + i, tf("shift_x", x), sHead) }
        sh.set(r, 5, t("total_w"), sHead)
        sh.merge(r, 6, r, lastCol, t("keywords"), sHead)
        r++
        for (f in FAULT_TYPES) {
            sh.set(r, 0, ReportText.faultName(opt.lang, f.key), sCellL)
            SHIFTS.forEachIndexed { i, x -> sh.set(r, 1 + i, an.faultCount(x, f), sCell) }
            sh.set(r, 5, an.faultCount(null, f), sTot)
            sh.merge(r, 6, r, lastCol, f.keywords.filter { it.all { ch -> ch.code < 0x600 } }.joinToString(", "), s(border = line, size = 8, color = muted, h = if (rtl) "right" else "left"))
            r++
        }
        sh.set(r, 0, t("f_other"), sCellL)
        SHIFTS.forEachIndexed { i, x -> sh.set(r, 1 + i, an.otherCount(x), sCell) }
        sh.set(r, 5, an.otherCount(null), sTot)
        sh.merge(r, 6, r, lastCol, t("no_keyword"), s(border = line, size = 8, color = muted, h = if (rtl) "right" else "left"))
        r++
        sh.set(r, 0, t("remarks_logged"), sTotL)
        SHIFTS.forEachIndexed { i, x -> sh.set(r, 1 + i, an.stats(x).remarks, sTot) }
        sh.set(r, 5, tot.remarks, sTot)
        sh.merge(r, 6, r, lastCol, t("one_remark_note"), sTotL)
        r += 2

        // inventory
        sh.merge(r, 0, r, lastCol, t("inventory_title"), sSec); r++
        sh.height(r, 24.0)
        sh.set(r, 0, t("equipment"), sHead); sh.set(r, 1, t("standard"), sHead)
        SHIFTS.forEachIndexed { i, x -> sh.set(r, 2 + i, tf("shift_x", x), sHead) }
        sh.set(r, 6, t("total_w"), sHead); sh.set(r, 7, t("short"), sHead); sh.set(r, 8, t("pct_hoses"), sHead)
        sh.merge(r, 9, r, lastCol, t("status"), sHead)
        r++
        val hoseTotal = (0..2).sumOf { an.inStock(null, it) }.coerceAtLeast(1)
        for (k in 0 until EQ_COUNT) {
            val sv = an.short(null, k)
            sh.set(r, 0, ReportText.eqLong(opt.lang, k), sCellL)
            sh.set(r, 1, an.standard[k], sCell)
            SHIFTS.forEachIndexed { i, x -> sh.set(r, 2 + i, an.inStock(x, k), sCell) }
            sh.set(r, 6, an.inStock(null, k), sTot)
            sh.set(r, 7, sv, s(bold = true, border = line, size = 9, color = if (sv > 0) "EF4444" else "22C55E"))
            if (k <= 2) sh.set(r, 8, an.inStock(null, k).toDouble() / hoseTotal, s(border = line, size = 9, numFmt = "0.0%")) else sh.set(r, 8, "—", sCell)
            sh.merge(r, 9, r, lastCol, if (sv > 0) t("st_short") else t("st_full"), s(bold = true, border = line, size = 9, color = if (sv > 0) "EF4444" else "22C55E"))
            r++
        }
        sh.height(r, 26.0)
        sh.merge(r, 0, r, lastCol, t("short_note"), sNote)
        r += 2

        // summary
        sh.merge(r, 0, r, lastCol, t("summary_title"), sSec); r++
        for (line1 in ReportText.summary(an, opt.lang)) {
            sh.height(r, 22.0)
            sh.merge(r, 0, r, lastCol, line1, s(size = 10, fill = card, border = line, h = if (rtl) "right" else "left", wrap = true))
            r++
        }
        r++

        // faulty list
        val bad = an.faulty()
        sh.merge(r, 0, r, lastCol, tf("faulty_list", bad.size), sSec); r++
        sh.height(r, 22.0)
        listOf("h_no", "c_shift", "h_tag", "h_hydrant", "h_date", "h_time").forEachIndexed { c, k -> sh.set(r, c, t(k), sHead) }
        sh.merge(r, 6, r, lastCol, t("h_remarks"), sHead)
        r++
        if (bad.isEmpty()) { sh.merge(r, 0, r, lastCol, t("no_faulty"), sCell); r++ }
        bad.forEachIndexed { i, c ->
            val f = s(border = line, size = 9, fill = badFill)
            sh.set(r, 0, i + 1, f)
            sh.set(r, 1, tf("shift_x", c.shift), s(bold = true, border = line, size = 9, fill = badFill, color = hex(SHIFT_COLORS[c.shift] ?: "#3B82F6")))
            sh.set(r, 2, c.tagBox, s(bold = true, border = line, size = 9, fill = badFill))
            sh.set(r, 3, c.hydrantTag, f)
            sh.set(r, 4, c.epochDay?.let { it + 25569 }, s(border = line, size = 9, fill = badFill, numFmt = "yyyy-mm-dd"))
            sh.set(r, 5, c.timeMin?.let { it / 1440.0 }, s(border = line, size = 9, fill = badFill, numFmt = "hh:mm"))
            sh.merge(r, 6, r, lastCol, c.remarks, s(border = line, size = 9, fill = badFill, h = if (rtl) "right" else "left", wrap = true))
            r++
        }
        r++

        // closing
        sh.merge(r, 0, r, lastCol, t("closing"), sSec); r++
        sh.height(r, 30.0)
        sh.merge(r, 0, r, lastCol, t("closing_text"), s(size = 10, fill = card, border = line, h = if (rtl) "right" else "left", wrap = true))
        r++
        sh.height(r, 34.0)
        sh.merge(r, 0, r, 4, t("prepared_by"), s(size = 10, color = muted, h = if (rtl) "right" else "left"))
        sh.merge(r, 5, r, lastCol, t("signature"), s(size = 10, color = muted, h = if (rtl) "right" else "left"))
        r += 2
        sh.merge(r, 0, r, lastCol, t("footer"), s(size = 8, color = muted))
    }
}
