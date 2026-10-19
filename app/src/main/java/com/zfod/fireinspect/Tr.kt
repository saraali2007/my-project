package com.zfod.fireinspect

import java.util.Locale

/** All user-facing text, in English and Arabic. Unknown keys fall back to the key itself. */
object Tr {
    private val m = HashMap<String, Array<String>>()

    private fun p(key: String, en: String, ar: String) {
        m[key] = arrayOf(en, ar)
    }

    fun get(lang: Lang, key: String): String {
        val v = m[key] ?: return key
        return if (lang == Lang.AR) v[1] else v[0]
    }

    fun has(key: String): Boolean = m.containsKey(key)

    fun f(lang: Lang, key: String, vararg a: Any): String = String.format(Locale.US, get(lang, key), *a)

    init {
        // ---- report ---------------------------------------------------------------------------
        p("report_name", "FIRE FIGHTING EQUIPMENT INSPECTION REPORT", "تقرير فحص معدات الإطفاء")
        p("dash_title", "MASTER DASHBOARD", "اللوحة الرئيسية")
        p("overview_sub", "Live data from Shift A · B · C · D   ·   Safety & Emergency Response Department", "بيانات مباشرة من النوبات A · B · C · D   ·   قسم السلامة والاستجابة للطوارئ")
        p("printed", "Printed", "تاريخ الطباعة")
        p("footer", "ZFOD – DGSN Safety & Emergency Response  ·  Confidential – Internal Use Only  ·  2026", "ZFOD – DGSN السلامة والاستجابة للطوارئ  ·  سري – للاستخدام الداخلي فقط  ·  2026")
        p("page", "Page", "صفحة")
        p("no_data", "No data to show", "لا توجد بيانات للعرض")
        p("shift_x", "Shift %s", "النوبة %s")
        p("insp_date", "Inspection date", "تاريخ الفحص")
        p("insp_time", "Inspection time", "وقت الفحص")
        p("cabinets", "CABINETS", "الصناديق")
        p("k_total", "TOTAL CABINETS", "إجمالي الصناديق")
        p("k_ok", "HYDRANT OK", "الحنفيات السليمة")
        p("k_faulty", "FAULTY", "المعطلة")
        p("k_na", "N/A", "غير متاحة")
        p("k_pending", "PENDING DATE", "بدون تاريخ")
        p("k_rate", "OPERATIONAL RATE", "نسبة الجاهزية")
        p("h_no", "#", "#")
        p("h_tag", "Tag Box", "رقم الصندوق")
        p("h_shift", "Shift", "النوبة")
        p("h_hydrant", "Hydrant Tag", "رقم الحنفية")
        p("h_hstatus", "Hydrant Status", "حالة الحنفية")
        p("h_cabstatus", "Cabinet Status / Notes", "حالة الصندوق / ملاحظات")
        p("h_date", "Date Inspected", "تاريخ الفحص")
        p("h_time", "Time", "الوقت")
        p("h_remarks", "Remarks", "الملاحظات")
        p("h_location", "Location", "الموقع")
        p("h_photos", "Photos", "الصور")
        p("totals", "TOTAL (units counted)", "المجموع (الوحدات المعدودة)")
        p("st_ok", "OK", "سليم")
        p("st_no", "NO", "معطل")
        p("st_na", "N/A", "غ/م")
        p("shift_analysis", "Shift %s analysis (this shift only)", "تحليل النوبة %s (هذه النوبة فقط)")
        p("fault_type", "FAULT TYPE (from Remarks)", "نوع العطل (من الملاحظات)")
        p("equip_vs_std", "Equipment count vs standard", "عدد المعدات مقابل المعيار")
        p("equipment", "EQUIPMENT", "المعدات")
        p("in_stock", "IN STOCK", "المتوفر")
        p("standard", "STANDARD", "المعيار")
        p("short", "SHORT", "النقص")
        p("short_note", "SHORT = units missing versus the standard per cabinet. Cabinets with no figure entered are not counted.", "النقص = الوحدات الناقصة قياساً بالمعيار لكل صندوق. الصناديق التي لم تُدخل لها أرقام لا تُحتسب.")
        p("rate_title", "Operational rate", "نسبة الجاهزية")
        p("overview", "Overview — hydrant status", "نظرة عامة — حالة الحنفيات")
        p("shift_comparison", "Shift comparison", "مقارنة النوبات")
        p("c_shift", "SHIFT", "النوبة")
        p("operational_pct", "OPERATIONAL %", "الجاهزية %")
        p("fault_pct", "FAULT %", "الأعطال %")
        p("last_insp", "LAST INSPECTION", "آخر فحص")
        p("health", "HEALTH", "الحالة")
        p("healthy", "✓ Healthy", "✓ سليمة")
        p("n_faults", "⚠ %d fault(s)", "⚠ %d عطل")
        p("total_w", "TOTAL", "الإجمالي")
        p("faults_title", "Faults — classified from the Remarks column", "الأعطال — مصنفة من عمود الملاحظات")
        p("keywords", "KEYWORDS SEARCHED", "الكلمات المفتاحية")
        p("no_keyword", "no keyword matched", "لا توجد كلمة مطابقة")
        p("one_remark_note", "One remark can match several types.", "الملاحظة الواحدة قد تطابق أكثر من نوع.")
        p("f_cap", "Valve cap missing", "غطاء الصمام مفقود")
        p("f_leak", "Leaking valve / hydrant", "تسرب في الصمام / الحنفية")
        p("f_stuck", "Valve stuck", "الصمام عالق")
        p("f_pass", "Passing (valve not sealing)", "تمرير (الصمام لا يغلق)")
        p("f_gasket", "Gasket replacement needed", "يحتاج تبديل جوان")
        p("f_lever", "Broken lever / valve stem", "كسر في الذراع / ساق الصمام")
        p("f_isol", "Isolated / needs isolating", "معزول / يحتاج عزل")
        p("f_conn", "Missing connection / adapter", "وصلة / مهايئ مفقود")
        p("f_clean", "Needs cleaning", "يحتاج تنظيف")
        p("f_other", "Other remarks", "ملاحظات أخرى")
        p("remarks_logged", "Remarks logged", "الملاحظات المسجلة")
        p("inventory_title", "Inventory — equipment count vs standard", "المخزون — عدد المعدات مقابل المعيار")
        p("pct_hoses", "% OF HOSES", "% من الخراطيم")
        p("status", "STATUS", "الحالة")
        p("st_short", "⚠ Short", "⚠ ناقص")
        p("st_full", "✓ OK", "✓ مكتمل")
        p("summary_title", "Report — inspection analysis summary", "التقرير — ملخص تحليل الفحص")
        p("faulty_list", "Faulty hydrants — action required (%d)", "الحنفيات المعطلة — مطلوب إجراء (%d)")
        p("no_faulty", "No faulty hydrants recorded.", "لا توجد حنفيات معطلة مسجلة.")
        p("closing", "Report closing", "ختام التقرير")
        p("closing_text", "Report prepared and submitted by the Fire Fighting Inspection Team – ZFOD DGSN Safety & Emergency Response Department.", "أُعد هذا التقرير وقُدم من فريق فحص معدات الإطفاء – قسم السلامة والاستجابة للطوارئ ZFOD DGSN.")
        p("prepared_by", "Prepared by: ______________________________", "أعده: ______________________________")
        p("signature", "Signature / Date: ______________________________", "التوقيع / التاريخ: ______________________________")
        // summary lines
        p("sum_1", "📊  %d cabinets inspected across four shifts — %d operational (%s), %d faulty (%s), %d not applicable, %d pending.", "📊  تم فحص %d صندوقاً في أربع نوبات — %d سليم (%s)، %d معطل (%s)، %d غير متاح، %d بانتظار التاريخ.")
        p("sum_2a", "🏆  Best shift: %s (%s operational).", "🏆  أفضل نوبة: %s (جاهزية %s).")
        p("sum_2b", "   Most faults: %s (%d faulty hydrants).", "   أكثر أعطال: %s (%d حنفية معطلة).")
        p("sum_3a", "🔧  Most frequent fault: %s (%d cabinets).  Remarks logged in total: %d.", "🔧  أكثر عطل تكراراً: %s (%d صندوق).  إجمالي الملاحظات المسجلة: %d.")
        p("sum_3b", "🔧  No fault keywords were found in the remarks.  Remarks logged in total: %d.", "🔧  لم تُرصد كلمات أعطال في الملاحظات.  إجمالي الملاحظات المسجلة: %d.")
        p("sum_4a", "📦  %d equipment units are below the standard per cabinet, across %d of %d equipment types.", "📦  هناك %d وحدة معدات أقل من المعيار لكل صندوق، ضمن %d من أصل %d أنواع.")
        p("sum_4b", "📦  All equipment meets the standard per cabinet.", "📦  جميع المعدات مطابقة للمعيار لكل صندوق.")
        p("sum_5a", "⏳  All cabinets have an inspection date.", "⏳  جميع الصناديق لها تاريخ فحص.")
        p("sum_5b", "⏳  %d cabinet(s) have no inspection date.", "⏳  هناك %d صندوق بلا تاريخ فحص.")
        p("sum_6a", "📅  Latest inspection date recorded: %s.", "📅  آخر تاريخ فحص مسجل: %s.")
        p("sum_6b", "📅  No inspection date recorded yet.", "📅  لا يوجد تاريخ فحص مسجل بعد.")
        // equipment (short headers / long names)
        p("eq_s0", "2.5\" Hose", "خرطوم 2.5 بوصة")
        p("eq_s1", "1.5\" Hose", "خرطوم 1.5 بوصة")
        p("eq_s2", "4\" Hose", "خرطوم 4 بوصة")
        p("eq_s3", "2.5\" Nozzle", "نوزل 2.5 بوصة")
        p("eq_s4", "2.5\" Conn.", "وصلة 2.5 بوصة")
        p("eq_s5", "1.5\" Conn.", "وصلة 1.5 بوصة")
        p("eq_s6", "Key 6\" Conn.", "وصلة مفتاح 6 بوصة")
        p("eq_s7", "Key 4\" Conn.", "وصلة مفتاح 4 بوصة")
        p("eq_l0", "2.5\" Hoses", "خراطيم 2.5 بوصة")
        p("eq_l1", "1.5\" Hoses", "خراطيم 1.5 بوصة")
        p("eq_l2", "4\" Hoses", "خراطيم 4 بوصة")
        p("eq_l3", "2.5\" Nozzles", "نوزلات 2.5 بوصة")
        p("eq_l4", "2.5\" Connections", "وصلات 2.5 بوصة")
        p("eq_l5", "1.5\" Connections", "وصلات 1.5 بوصة")
        p("eq_l6", "Key 6\" Connections", "وصلات مفتاح 6 بوصة")
        p("eq_l7", "Key 4\" Connections", "وصلات مفتاح 4 بوصة")
        TrUi.load { k, en, ar -> p(k, en, ar) }
    }
}

object ReportText {
    fun faultName(lang: Lang, key: String) = Tr.get(lang, key)
    fun eqShort(lang: Lang, k: Int) = Tr.get(lang, "eq_s$k")
    fun eqLong(lang: Lang, k: Int) = Tr.get(lang, "eq_l$k")

    /** The six summary sentences shown on the master dashboard (same logic as the Excel report). */
    fun summary(an: Analysis, lang: Lang): List<String> {
        fun f(k: String, vararg a: Any) = Tr.f(lang, k, *a)
        val st = an.stats(null)
        val out = ArrayList<String>()
        out += f("sum_1", st.total, st.ok, Fmt.pct(st.rate), st.faulty, Fmt.pct(st.faultRate), st.na, st.pending)
        val best = an.bestShift()
        if (best != null) {
            var line = f("sum_2a", f("shift_x", best), Fmt.pct(an.stats(best).rate))
            val worst = an.mostFaultsShift()
            if (worst != null) line += f("sum_2b", f("shift_x", worst), an.stats(worst).faulty)
            out += line
        }
        val top = an.topFault()
        out += if (top != null) f("sum_3a", Tr.get(lang, top.first.key), top.second, st.remarks) else f("sum_3b", st.remarks)
        val short = an.totalShort(null)
        out += if (short > 0) f("sum_4a", short, an.typesShort(null), EQ_COUNT) else f("sum_4b")
        out += if (st.pending == 0) f("sum_5a") else f("sum_5b", st.pending)
        out += if (st.lastDay != null) f("sum_6a", Fmt.date(st.lastDay)) else f("sum_6b")
        return out
    }
}
