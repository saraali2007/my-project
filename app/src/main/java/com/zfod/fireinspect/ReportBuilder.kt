package com.zfod.fireinspect

import java.util.Locale

/**
 * Builds the complete report as one self-contained HTML document made of fixed A4-landscape pages.
 * The same HTML is used for the in-app preview, for system printing and for PDF export, so all
 * three always look identical. Pagination is computed here (not by the browser), which keeps
 * rows from being cut and makes dark backgrounds fill the whole page.
 */
class ReportBuilder(
    cabinets: List<Cabinet>,
    standard: List<Int>,
    private val opt: ReportOptions,
    /** Maps a stored photo file name to a URL the WebView can load (or null when missing). */
    private val photoUrl: (String) -> String?,
    private val zfodLogo: String = "logo_zfod.jpg",
    private val eniLogo: String = "logo_eni.png",
) {
    private val an = Analysis(cabinets, standard)
    private val rtl = opt.lang == Lang.AR

    private fun t(k: String) = Tr.get(opt.lang, k)
    private fun tf(k: String, vararg a: Any) = String.format(Locale.US, t(k), *a)

    // ---- page geometry (mm) --------------------------------------------------------------
    private companion object {
        const val PAGE_W = 297.0
        const val PAGE_H = 209.4
        const val PAD = 7.0
        const val USABLE = 184.0      // content height available below the header, above the footer
        const val H_FULL = 22.0       // full header block incl. margin
        const val H_COMPACT = 12.0    // compact header on continuation pages
        const val ROW = 6.6
        const val ROW_PHOTO = 19.0
        const val ROW_MINI = 5.4
        const val HEAD_H = 9.0
        const val LINE_H = 2.95       // one text line at 7pt
        const val MAX_LINES = 8
        const val CPL_REMARKS = 21    // conservative characters per line in each wrapped column
        const val CPL_NOTES = 15
        const val CPL_LOCATION = 10
    }

    private fun linesFor(text: String, cpl: Int): Int =
        if (text.isBlank()) 1 else text.trim().split(Regex("\\s+")).fold(1 to 0) { (ln, used), w ->
            val len = w.length
            when {
                used == 0 -> (ln + (len - 1) / cpl) to (((len - 1) % cpl) + 1)
                used + 1 + len <= cpl -> ln to (used + 1 + len)
                else -> (ln + 1 + (len - 1) / cpl) to (((len - 1) % cpl) + 1)
            }
        }.first

    // ---- block model -----------------------------------------------------------------------
    private sealed class Blk
    private class Fixed(val h: Double, val html: String, val keepNext: Boolean = false) : Blk()
    private class RowH(val h: Double, val html: String)
    private class Tbl(val cls: String, val cols: String, val head: String, val headH: Double, val rows: List<RowH>) : Blk()

    private class PageData(val full: Boolean, val label: String, val body: String)

    private val pages = ArrayList<PageData>()

    // ---- public ------------------------------------------------------------------------------
    fun build(): String {
        if (opt.overview) layout(overviewBlocks(), t("dash_title"))
        for (s in opt.shifts) layout(shiftBlocks(s), tf("shift_x", s))
        if (pages.isEmpty()) layout(listOf(Fixed(10.0, "<div class='empty'>${esc(t("no_data"))}</div>")), "")
        val total = pages.size
        val sb = StringBuilder(64 * 1024)
        sb.append(head())
        pages.forEachIndexed { i, p ->
            sb.append("<section class='page'>")
            sb.append(header(p.full, p.label))
            sb.append("<div class='body'>").append(p.body).append("</div>")
            sb.append(footer(i + 1, total))
            sb.append("</section>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    // ---- layout engine -----------------------------------------------------------------------
    private fun layout(blocks: List<Blk>, label: String) {
        var used = 0.0
        var cur = StringBuilder()
        var full = true
        fun cap() = USABLE - (if (full) H_FULL else H_COMPACT)
        fun flush() {
            pages.add(PageData(full, label, cur.toString()))
            cur = StringBuilder()
            used = 0.0
            full = false
        }
        fun minNext(b: Blk?): Double = when (b) {
            null -> 0.0
            is Fixed -> b.h
            is Tbl -> b.headH + (b.rows.firstOrNull()?.h ?: 0.0)
        }
        for ((i, b) in blocks.withIndex()) {
            when (b) {
                is Fixed -> {
                    val need = b.h + if (b.keepNext) minNext(blocks.getOrNull(i + 1)) else 0.0
                    if (used > 0 && used + need > cap()) flush()
                    cur.append(b.html)
                    used += b.h
                }
                is Tbl -> {
                    var idx = 0
                    while (idx < b.rows.size) {
                        if (used > 0 && used + b.headH + b.rows[idx].h > cap()) flush()
                        val start = idx
                        var h = b.headH
                        while (idx < b.rows.size && used + h + b.rows[idx].h <= cap()) {
                            h += b.rows[idx].h
                            idx++
                        }
                        if (idx == start) { h += b.rows[idx].h; idx++ } // a row taller than a page: force it
                        cur.append("<table class='${b.cls}'>${b.cols}<thead>${b.head}</thead><tbody>")
                        for (r in start until idx) cur.append(b.rows[r].html)
                        cur.append("</tbody></table><div class='gap'></div>")
                        used += h + 2.0
                    }
                }
            }
        }
        if (cur.isNotEmpty() || blocks.isEmpty()) flush()
    }

    // ---- chrome ------------------------------------------------------------------------------
    private fun head(): String {
        val th = opt.theme
        val txt = opt.textColor
        return """<!DOCTYPE html>
<html lang="${if (rtl) "ar" else "en"}" dir="${if (rtl) "rtl" else "ltr"}"><head><meta charset="utf-8">
<meta name="viewport" content="width=1123, user-scalable=yes">
<title>${esc(t("report_name"))}</title>
<style>
:root{--bg:${th.bg};--page:${th.page};--card:${th.card};--head:${th.head};--line:${th.line};--text:$txt;--muted:${th.muted};--accent:${th.accent};--ok:#22c55e;--bad:#ef4444;--warn:#f59e0b;--na:#94a3b8}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:var(--bg);-webkit-print-color-adjust:exact;print-color-adjust:exact}
body{font-family:Roboto,'Noto Sans Arabic','Noto Naskh Arabic','Droid Arabic Naskh',Arial,sans-serif;color:var(--text);padding:8px 0}
.page{width:${PAGE_W}mm;height:${PAGE_H}mm;margin:0 auto 10px;padding:${PAD}mm;background:var(--page);position:relative;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.35);page-break-after:always;break-after:page}
@page{size:A4 landscape;margin:0}
@media print{html,body{background:var(--page)}body{padding:0}.page{margin:0;box-shadow:none}.page:last-of-type{page-break-after:auto;break-after:auto}}
.hdr{display:flex;direction:ltr;align-items:center;justify-content:space-between;border-bottom:0.6mm solid var(--accent);padding-bottom:1.6mm;margin-bottom:2.4mm}
.hdr.full{height:${H_FULL - 2.4}mm}.hdr.compact{height:${H_COMPACT - 2.4}mm}
.hdr img{border-radius:2.4mm;object-fit:cover}.hdr.full img{height:17mm;width:17mm}.hdr.compact img{height:8.6mm;width:8.6mm;border-radius:1.6mm}
.ttl{flex:1;text-align:center;padding:0 4mm;${if (rtl) "direction:rtl;" else ""}}
.ttl h1{font-size:15.5pt;font-weight:800;letter-spacing:${if (rtl) "0" else "0.4mm"};line-height:1.15}
.hdr.compact .ttl h1{font-size:10.5pt}
.ttl .sub{font-size:8.2pt;color:var(--muted);margin-top:0.8mm}
.hdr.compact .sub{display:none}
.body{position:relative}
.ftr{position:absolute;left:${PAD}mm;right:${PAD}mm;bottom:3.4mm;height:5mm;display:flex;justify-content:space-between;gap:4mm;font-size:6.3pt;color:var(--muted);border-top:0.25mm solid var(--line);padding-top:0.9mm}
.gap{height:2mm}
.strip{display:flex;align-items:center;gap:3mm;height:7mm;font-size:8pt;margin-bottom:0}
.chip{display:inline-block;padding:0.6mm 3.4mm;border-radius:1.6mm;font-weight:800;font-size:9pt;color:#fff}
.strip .meta{color:var(--muted)}.strip .meta b{color:var(--text);font-weight:700}
.kpis{display:flex;gap:2.4mm;height:17mm;margin-bottom:2mm}
.kpi{flex:1;position:relative;border:0.45mm solid var(--c);border-radius:2mm;background:var(--card);padding:1.6mm 2.4mm;box-shadow:0 0 2.2mm color-mix(in srgb,var(--c) 35%,transparent);overflow:hidden}
.kpi .l{font-size:6.6pt;font-weight:700;color:var(--c);letter-spacing:${if (rtl) "0" else "0.2mm"}}
.kpi .v{font-size:17pt;font-weight:800;margin-top:0.6mm;line-height:1}
.kpi svg{position:absolute;${if (rtl) "left" else "right"}:2mm;bottom:1.4mm;width:7mm;height:7mm;color:var(--c);opacity:.85}
.kpi .bar{position:absolute;left:2.4mm;right:2.4mm;bottom:1.2mm;height:1.2mm;background:var(--line);border-radius:1mm}.kpi .bar i{display:block;height:100%;background:var(--c);border-radius:1mm}
.sec{font-size:9.4pt;font-weight:800;color:var(--accent);height:6mm;line-height:6mm;border-${if (rtl) "right" else "left"}:1.2mm solid var(--accent);padding-${if (rtl) "right" else "left"}:2mm}
table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:7pt}
th{background:var(--head);font-weight:800;border:0.3mm solid var(--line);padding:0 0.8mm;height:${HEAD_H}mm;text-align:center;line-height:1.15;font-size:6.8pt}
td{border:0.3mm solid var(--line);padding:0 0.8mm;text-align:center;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;vertical-align:middle}
table.flt th,table.inv th,table.bad th{height:7mm}
tr.bad td{background:rgba(239,68,68,.17)}
tr.tot td{background:var(--head);font-weight:800}
td.lft{text-align:start}
td.wrap>div{display:-webkit-box;-webkit-line-clamp:var(--n,2);-webkit-box-orient:vertical;overflow:hidden;white-space:normal;line-height:1.16;text-align:start;word-break:break-word}
.pill{display:inline-block;min-width:10.5mm;padding:0.25mm 1.8mm;border-radius:4mm;border:0.35mm solid var(--p);color:var(--p);background:color-mix(in srgb,var(--p) 14%,transparent);font-weight:800;font-size:6.6pt;line-height:1.25}
.warn{color:var(--bad);font-weight:800}
.photos{display:flex;gap:1mm;justify-content:center;align-items:center;height:100%}
.photos img,.photos i{height:${ROW_PHOTO - 2.2}mm;width:15mm;object-fit:cover;border-radius:1mm;border:0.25mm solid var(--line)}
.photos i{display:block;border-style:dashed;opacity:.45}
.ana{display:flex;gap:3mm;height:66mm;direction:${if (rtl) "rtl" else "ltr"}}
.box{border:0.35mm solid var(--line);border-radius:2mm;background:var(--card);padding:1.6mm 2mm;overflow:hidden}
.box .bt{font-size:8.2pt;font-weight:800;color:var(--accent);margin-bottom:1mm}
.ana table{font-size:6.6pt}.ana th{height:5mm;font-size:6.3pt}.ana td{height:4.1mm;line-height:1.05}
.ana .note{font-size:5.6pt;color:var(--muted);margin-top:1mm;line-height:1.25}
.donut{display:flex;flex-direction:column;align-items:center;justify-content:center;height:46mm;gap:1.4mm}
.donut svg{width:27mm;height:27mm}.donut .pc{font-size:13pt;font-weight:800}
.lg2{font-size:6.6pt;line-height:1.55;width:100%}.lg2 b{display:inline-block;width:2.2mm;height:2.2mm;border-radius:50%;margin-${if (rtl) "left" else "right"}:1.2mm;vertical-align:middle}
.sum{height:var(--h);border:0.35mm solid var(--line);border-radius:2mm;background:var(--card);padding:2mm 3mm;font-size:8.2pt;line-height:1.75}
.close{border:0.35mm solid var(--line);border-radius:2mm;background:var(--card);padding:2.4mm 3mm;font-size:7.8pt;height:25mm}
.close .sig{display:flex;justify-content:space-between;margin-top:6.5mm;gap:8mm}.close .sig div{flex:1;border-top:0.3mm solid var(--muted);padding-top:1mm;color:var(--muted);font-size:7pt}
.empty{padding:40mm 0;text-align:center;font-size:14pt;color:var(--muted)}
.ok{color:var(--ok);font-weight:800}.bd{color:var(--bad);font-weight:800}.mu{color:var(--muted)}
</style></head><body>
"""
    }

    private fun header(full: Boolean, label: String): String {
        val sub = if (full) "<div class='sub'>${esc(subtitleFor(label))}</div>" else ""
        val title = esc(t("report_name")) + if (!full && label.isNotEmpty()) " · " + esc(label) else ""
        return "<div class='hdr ${if (full) "full" else "compact"}'><img src='${zfodLogo}' alt='ZFOD'>" +
            "<div class='ttl'><h1>$title</h1>$sub</div><img src='${eniLogo}' alt='ENI'></div>"
    }

    private fun subtitleFor(label: String): String =
        (if (label.isNotEmpty()) "$label  ·  " else "") + t("printed") + ": " + opt.printedDate + "  " + opt.printedTime

    private fun footer(n: Int, total: Int): String =
        "<div class='ftr'><span>${esc(t("footer"))}</span><span>${esc(t("printed"))}: ${opt.printedDate} ${opt.printedTime}</span>" +
            "<span>${esc(t("page"))} $n / $total</span></div>"

    // ---- shift section ---------------------------------------------------------------------------
    private fun shiftBlocks(s: Char): List<Blk> {
        val rows = an.rows(s)
        val st = an.stats(s)
        val blocks = ArrayList<Blk>()
        blocks += Fixed(7.0, shiftStrip(s, rows, st))
        blocks += Fixed(19.0, kpiRow(st))
        blocks += shiftTable(rows)
        blocks += Fixed(66.0, analysisBlock(s, st))
        return blocks
    }

    private fun shiftStrip(s: Char, rows: List<Cabinet>, st: ShiftStats): String {
        val days = rows.mapNotNull { it.epochDay }
        val dateTxt = when {
            days.isEmpty() -> "—"
            days.min() == days.max() -> Fmt.date(days.min())
            else -> Fmt.date(days.min()) + " → " + Fmt.date(days.max())
        }
        val timeTxt = Fmt.time(st.lastTimeMin).ifEmpty { "—" }
        val color = SHIFT_COLORS[s] ?: "#3B82F6"
        return "<div class='strip'><span class='chip' style='background:$color'>${esc(tf("shift_x", s))}</span>" +
            "<span class='meta'>${esc(t("insp_date"))}: <b>$dateTxt</b></span>" +
            "<span class='meta'>${esc(t("insp_time"))}: <b>$timeTxt</b></span>" +
            "<span class='meta'>${esc(t("cabinets"))}: <b>${st.total}</b></span></div>"
    }

    private fun kpiRow(st: ShiftStats): String {
        val sb = StringBuilder("<div class='kpis'>")
        fun card(label: String, value: String, color: String, icon: String = "", bar: Double? = null) {
            sb.append("<div class='kpi' style='--c:$color'><div class='l'>${esc(label)}</div><div class='v'>${esc(value)}</div>")
            if (icon.isNotEmpty()) sb.append(icon)
            if (bar != null) sb.append("<div class='bar'><i style='width:${(bar * 100).coerceIn(0.0, 100.0).toInt()}%'></i></div>")
            sb.append("</div>")
        }
        card(t("k_total"), st.total.toString(), "#3b82f6", ICON_CABINET)
        card(t("k_ok"), st.ok.toString(), "#22c55e", ICON_HYDRANT)
        card(t("k_faulty"), st.faulty.toString(), "#ef4444", ICON_WARN)
        card(t("k_na"), st.na.toString(), "#94a3b8")
        card(t("k_pending"), st.pending.toString(), "#a855f7")
        card(t("k_rate"), Fmt.pct(st.rate), "#06b6d4", "", st.rate)
        sb.append("</div>")
        return sb.toString()
    }

    // weights per column (relative); normalised to 100 %
    private fun shiftTable(rows: List<Cabinet>): Tbl {
        val ph = opt.photos
        val w = ArrayList<Double>()
        w += listOf(11.0, 6.0, 11.0, 10.0, 18.0)
        repeat(EQ_COUNT) { w += 6.4 }
        w += listOf(13.5, 7.0, 24.0, 12.0)
        if (ph) w += 48.0
        val sum = w.sum()
        val cols = "<colgroup>" + w.joinToString("") { "<col style='width:${String.format(Locale.US, "%.3f", it / sum * 100)}%'>" } + "</colgroup>"
        val heads = ArrayList<String>()
        heads += listOf("h_tag", "h_shift", "h_hydrant", "h_hstatus", "h_cabstatus").map { t(it) }
        for (k in 0 until EQ_COUNT) heads += ReportText.eqShort(opt.lang, k)
        heads += listOf("h_date", "h_time", "h_remarks", "h_location").map { t(it) }
        if (ph) heads += t("h_photos")
        val head = "<tr>" + heads.joinToString("") { "<th>${esc(it)}</th>" } + "</tr>"

        val out = ArrayList<RowH>()
        for (c in rows) {
            val hasPh = ph && c.hasPhotos
            val notesTxt = c.notes.ifBlank { "// //" }
            val lines = maxOf(linesFor(c.remarks, CPL_REMARKS), linesFor(notesTxt, CPL_NOTES), linesFor(c.location, CPL_LOCATION)).coerceIn(1, MAX_LINES)
            val textH = if (lines <= 2) ROW else lines * LINE_H + 1.8
            val h = if (hasPh) maxOf(ROW_PHOTO, textH) else textH
            val bad = c.status == Status.NO
            val sb = StringBuilder()
            sb.append("<tr class='${if (bad) "bad" else ""}' style='height:${h}mm'>")
            sb.append("<td><b>${esc(c.tagBox)}</b></td>")
            sb.append("<td style='color:${SHIFT_COLORS[c.shift]};font-weight:800'>${c.shift}</td>")
            sb.append("<td>${esc(c.hydrantTag)}</td>")
            sb.append("<td>${pill(c.status)}</td>")
            sb.append("<td class='wrap'><div style='--n:$lines'>${esc(notesTxt)}${if (bad) " <span class='warn'>▲</span>" else ""}</div></td>")
            for (k in 0 until EQ_COUNT) {
                val n = c.counts[k]
                val low = n != null && n < an.standard[k]
                sb.append("<td${if (low) " class='warn'" else ""}>${n ?: ""}</td>")
            }
            sb.append("<td>${Fmt.date(c.epochDay)}</td><td>${Fmt.time(c.timeMin)}</td>")
            sb.append("<td class='wrap'><div style='--n:$lines'>${esc(c.remarks)}</div></td>")
            sb.append("<td class='wrap'><div style='--n:$lines'>${esc(c.location)}</div></td>")
            if (ph) sb.append("<td style='padding:0.4mm'>${if (hasPh) photoCell(c) else ""}</td>")
            sb.append("</tr>")
            out += RowH(h + 0.1, sb.toString())
        }
        // totals row ("TOTAL (units counted)")
        val tot = StringBuilder("<tr class='tot' style='height:${ROW}mm'><td colspan='5'>${esc(t("totals"))}</td>")
        for (k in 0 until EQ_COUNT) tot.append("<td>${rows.sumOf { it.counts[k] ?: 0 }}</td>")
        tot.append("<td colspan='${if (ph) 5 else 4}'></td></tr>")
        out += RowH(ROW + 0.1, tot.toString())
        return Tbl("main", cols, head, HEAD_H, out)
    }

    private fun photoCell(c: Cabinet): String {
        val sb = StringBuilder("<div class='photos'>")
        for (p in c.photos) {
            val url = if (p.isEmpty()) null else photoUrl(p)
            if (url != null) sb.append("<img src='${esc(url)}'>") else sb.append("<i></i>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun pill(s: Status): String {
        val (txt, col) = when (s) {
            Status.OK -> t("st_ok") to "var(--ok)"
            Status.NO -> t("st_no") to "var(--bad)"
            Status.NA -> t("st_na") to "var(--na)"
        }
        return "<span class='pill' style='--p:$col'>${esc(txt)}</span>"
    }

    private fun analysisBlock(s: Char, st: ShiftStats): String {
        val sb = StringBuilder("<div class='ana'>")
        // faults
        sb.append("<div class='box' style='flex:9'><div class='bt'>${esc(tf("shift_analysis", s))}</div>")
        sb.append("<table><colgroup><col style='width:78%'><col style='width:22%'></colgroup><thead><tr><th>${esc(t("fault_type"))}</th><th>${esc(t("cabinets"))}</th></tr></thead><tbody>")
        for (f in FAULT_TYPES) sb.append("<tr><td class='lft'>${esc(ReportText.faultName(opt.lang, f.key))}</td><td>${an.faultCount(s, f)}</td></tr>")
        sb.append("<tr><td class='lft'>${esc(t("f_other"))}</td><td>${an.otherCount(s)}</td></tr>")
        sb.append("<tr class='tot'><td class='lft'>${esc(t("remarks_logged"))}</td><td>${st.remarks}</td></tr>")
        sb.append("</tbody></table></div>")
        // equipment
        sb.append("<div class='box' style='flex:9'><div class='bt'>${esc(t("equip_vs_std"))}</div>")
        sb.append("<table><colgroup><col style='width:40%'><col style='width:20%'><col style='width:20%'><col style='width:20%'></colgroup><thead><tr><th>${esc(t("equipment"))}</th><th>${esc(t("in_stock"))}</th><th>${esc(t("standard"))}</th><th>${esc(t("short"))}</th></tr></thead><tbody>")
        for (k in 0 until EQ_COUNT) {
            val sh = an.short(s, k)
            sb.append("<tr><td class='lft'>${esc(ReportText.eqLong(opt.lang, k))}</td><td>${an.inStock(s, k)}</td><td>${an.standard[k]}</td><td class='${if (sh > 0) "bd" else "ok"}'>$sh</td></tr>")
        }
        sb.append("</tbody></table><div class='note'>${esc(t("short_note"))}</div></div>")
        // donut
        sb.append("<div class='box' style='flex:5'><div class='bt'>${esc(t("rate_title"))}</div>")
        sb.append(donut(st))
        sb.append("</div></div>")
        return sb.toString()
    }

    private fun donut(st: ShiftStats): String {
        val total = st.total.coerceAtLeast(1).toDouble()
        val parts = listOf(
            Triple(st.ok, "#22c55e", t("st_ok")),
            Triple(st.faulty, "#ef4444", t("st_no")),
            Triple(st.na, "#94a3b8", t("st_na")),
        )
        val sb = StringBuilder("<div class='donut'><svg viewBox='0 0 42 42'>")
        sb.append("<circle cx='21' cy='21' r='15.9155' fill='none' stroke='${opt.theme.line}' stroke-width='5'/>")
        var acc = 0.0
        for ((n, col, _) in parts) {
            val p = n / total * 100.0
            if (p > 0) {
                sb.append(String.format(Locale.US, "<circle cx='21' cy='21' r='15.9155' fill='none' stroke='%s' stroke-width='5' stroke-dasharray='%.3f %.3f' stroke-dashoffset='%.3f'/>", col, p, 100.0 - p, 25.0 - acc))
            }
            acc += p
        }
        sb.append("</svg><div class='pc'>${Fmt.pct(st.rate)}</div><div class='lg2'>")
        for ((n, col, name) in parts) sb.append("<div><b style='background:$col'></b>${esc(name)}: <strong>$n</strong></div>")
        sb.append("</div></div>")
        return sb.toString()
    }

    // ---- master dashboard -----------------------------------------------------------------------------
    private fun overviewBlocks(): List<Blk> {
        val blocks = ArrayList<Blk>()
        val tot = an.stats(null)
        blocks += Fixed(7.0, "<div class='strip'><span class='chip' style='background:${opt.theme.accent}'>${esc(t("dash_title"))}</span>" +
            "<span class='meta'>${esc(t("overview_sub"))}</span></div>")
        blocks += Fixed(6.0, "<div class='sec'>${esc(t("overview"))}</div>", keepNext = true)
        blocks += Fixed(19.0, kpiRow(tot))

        // shift comparison
        blocks += Fixed(6.0, "<div class='sec'>${esc(t("shift_comparison"))}</div>", keepNext = true)
        val cmpHead = "<tr>" + listOf("c_shift", "cabinets", "st_ok", "st_no", "st_na", "k_pending", "operational_pct", "fault_pct", "last_insp", "h_remarks", "health")
            .joinToString("") { "<th>${esc(t(it))}</th>" } + "</tr>"
        val cmpRows = ArrayList<RowH>()
        for (s in SHIFTS) {
            val st = an.stats(s)
            cmpRows += RowH(ROW + 0.1, cmpRow(tf("shift_x", s), st, SHIFT_COLORS[s], false))
        }
        cmpRows += RowH(ROW + 0.1, cmpRow(t("total_w"), tot, null, true))
        blocks += Tbl("cmp", "", cmpHead, HEAD_H, cmpRows)

        // faults matrix
        blocks += Fixed(6.0, "<div class='sec'>${esc(t("faults_title"))}</div>", keepNext = true)
        val fHead = "<tr><th>${esc(t("fault_type"))}</th>" + SHIFTS.joinToString("") { "<th>${esc(tf("shift_x", it))}</th>" } +
            "<th>${esc(t("total_w"))}</th><th>${esc(t("keywords"))}</th></tr>"
        val fRows = ArrayList<RowH>()
        for (f in FAULT_TYPES) {
            val sb = StringBuilder("<tr style='height:${ROW_MINI}mm'><td class='lft'>${esc(ReportText.faultName(opt.lang, f.key))}</td>")
            for (s in SHIFTS) sb.append("<td>${an.faultCount(s, f)}</td>")
            sb.append("<td><b>${an.faultCount(null, f)}</b></td><td class='lft mu'>${esc(f.keywords.filter { it.all { ch -> ch.code < 0x600 } }.joinToString(", "))}</td></tr>")
            fRows += RowH(ROW_MINI + 0.1, sb.toString())
        }
        run {
            val sb = StringBuilder("<tr style='height:${ROW_MINI}mm'><td class='lft'>${esc(t("f_other"))}</td>")
            for (s in SHIFTS) sb.append("<td>${an.otherCount(s)}</td>")
            sb.append("<td><b>${an.otherCount(null)}</b></td><td class='lft mu'>${esc(t("no_keyword"))}</td></tr>")
            fRows += RowH(ROW_MINI + 0.1, sb.toString())
            val sb2 = StringBuilder("<tr class='tot' style='height:${ROW_MINI}mm'><td class='lft'>${esc(t("remarks_logged"))}</td>")
            for (s in SHIFTS) sb2.append("<td>${an.stats(s).remarks}</td>")
            sb2.append("<td>${tot.remarks}</td><td class='lft'>${esc(t("one_remark_note"))}</td></tr>")
            fRows += RowH(ROW_MINI + 0.1, sb2.toString())
        }
        blocks += Tbl("flt", "<colgroup><col style='width:26%'><col style='width:8%'><col style='width:8%'><col style='width:8%'><col style='width:8%'><col style='width:9%'><col style='width:33%'></colgroup>", fHead, 7.0, fRows)

        // inventory
        blocks += Fixed(6.0, "<div class='sec'>${esc(t("inventory_title"))}</div>", keepNext = true)
        val iHead = "<tr><th>${esc(t("equipment"))}</th><th>${esc(t("standard"))}</th>" +
            SHIFTS.joinToString("") { "<th>${esc(tf("shift_x", it))}</th>" } +
            "<th>${esc(t("total_w"))}</th><th>${esc(t("short"))}</th><th>${esc(t("pct_hoses"))}</th><th>${esc(t("status"))}</th></tr>"
        val hoseTotal = (0..2).sumOf { an.inStock(null, it) }.coerceAtLeast(1)
        val iRows = ArrayList<RowH>()
        for (k in 0 until EQ_COUNT) {
            val sh = an.short(null, k)
            val sb = StringBuilder("<tr style='height:${ROW_MINI}mm'><td class='lft'>${esc(ReportText.eqLong(opt.lang, k))}</td><td>${an.standard[k]}</td>")
            for (s in SHIFTS) sb.append("<td>${an.inStock(s, k)}</td>")
            sb.append("<td><b>${an.inStock(null, k)}</b></td><td class='${if (sh > 0) "bd" else "ok"}'>$sh</td>")
            sb.append("<td>${if (k <= 2) Fmt.pct(an.inStock(null, k).toDouble() / hoseTotal) else "—"}</td>")
            sb.append("<td class='${if (sh > 0) "bd" else "ok"}'>${esc(if (sh > 0) t("st_short") else t("st_full"))}</td></tr>")
            iRows += RowH(ROW_MINI + 0.1, sb.toString())
        }
        blocks += Tbl("inv", "", iHead, 7.0, iRows)
        blocks += Fixed(8.0, "<div class='note mu' style='font-size:6.4pt;line-height:1.3;height:8mm'>${esc(t("short_note"))}</div>")

        // summary
        val lines = ReportText.summary(an, opt.lang)
        val h = lines.size * 6.4 + 4.5
        blocks += Fixed(6.0, "<div class='sec'>${esc(t("summary_title"))}</div>", keepNext = true)
        blocks += Fixed(h + 2, "<div class='sum' style='--h:${h}mm'>" + lines.joinToString("") { "<div>${esc(it)}</div>" } + "</div>")

        // faulty list
        val bad = an.faulty()
        blocks += Fixed(6.0, "<div class='sec'>${esc(tf("faulty_list", bad.size))}</div>", keepNext = true)
        val bHead = "<tr>" + listOf("h_no", "c_shift", "h_tag", "h_hydrant", "h_date", "h_time", "h_remarks").joinToString("") { "<th>${esc(t(it))}</th>" } + "</tr>"
        val bRows = ArrayList<RowH>()
        if (bad.isEmpty()) {
            bRows += RowH(ROW_MINI + 0.1, "<tr style='height:${ROW_MINI}mm'><td colspan='7'>${esc(t("no_faulty"))}</td></tr>")
        }
        bad.forEachIndexed { i, c ->
            bRows += RowH(ROW_MINI + 0.1, "<tr class='bad' style='height:${ROW_MINI}mm'><td>${i + 1}</td>" +
                "<td style='color:${SHIFT_COLORS[c.shift]};font-weight:800'>${esc(tf("shift_x", c.shift))}</td>" +
                "<td><b>${esc(c.tagBox)}</b></td><td>${esc(c.hydrantTag)}</td><td>${Fmt.date(c.epochDay)}</td><td>${Fmt.time(c.timeMin)}</td>" +
                "<td class='wrap'><div>${esc(c.remarks)}</div></td></tr>")
        }
        blocks += Tbl("bad", "<colgroup><col style='width:4%'><col style='width:10%'><col style='width:11%'><col style='width:11%'><col style='width:10%'><col style='width:7%'><col style='width:47%'></colgroup>", bHead, 7.0, bRows)

        // closing
        blocks += Fixed(26.0, "<div class='close'><b>${esc(t("closing"))}</b><div style='margin-top:1.4mm'>${esc(t("closing_text"))}</div>" +
            "<div class='sig'><div>${esc(t("prepared_by"))}</div><div>${esc(t("signature"))}</div></div></div>")
        return blocks
    }

    private fun cmpRow(label: String, st: ShiftStats, color: String?, total: Boolean): String {
        val lab = if (color != null) "<td style='color:$color;font-weight:800'>${esc(label)}</td>" else "<td>${esc(label)}</td>"
        val health = if (st.faulty > 0) "<td class='bd'>${esc(tf("n_faults", st.faulty))}</td>" else "<td class='ok'>${esc(t("healthy"))}</td>"
        val last = Fmt.date(st.lastDay).ifEmpty { "—" }
        return "<tr class='${if (total) "tot" else ""}' style='height:${ROW}mm'>$lab<td>${st.total}</td><td>${st.ok}</td><td>${st.faulty}</td><td>${st.na}</td>" +
            "<td>${st.pending}</td><td>${Fmt.pct(st.rate)}</td><td>${Fmt.pct(st.faultRate)}</td><td>$last</td><td>${st.remarks}</td>$health</tr>"
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> sb.append(ch)
        }
        return sb.toString()
    }
}

private const val ICON_CABINET =
    "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.6'><rect x='4' y='2.5' width='16' height='19' rx='2'/><line x1='12' y1='2.5' x2='12' y2='21.5'/><rect x='6.5' y='6' width='3.6' height='6' rx='.8'/><circle cx='15' cy='11' r='1.4'/></svg>"
private const val ICON_HYDRANT =
    "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.6'><path d='M8 21V10a4 4 0 0 1 8 0v11'/><rect x='6' y='19' width='12' height='2.4' rx='.6'/><path d='M10 6.2V4h4v2.2'/><path d='M4 12h4M16 12h4'/><circle cx='12' cy='13.5' r='1.3'/></svg>"
private const val ICON_WARN =
    "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'><path d='M12 3 22 20H2z' stroke-linejoin='round'/><line x1='12' y1='9.5' x2='12' y2='14'/><circle cx='12' cy='17' r='.6'/></svg>"
