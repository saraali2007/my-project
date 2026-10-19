package com.zfod.fireinspect

/** One fault category, detected by searching the Remarks text for keywords (EN + AR). */
class FaultType(val key: String, val keywords: List<String>)

val FAULT_TYPES: List<FaultType> = listOf(
    FaultType("f_cap", listOf("cap", "غطاء", "سدادة")),
    FaultType("f_leak", listOf("leak", "تسرب", "تسريب", "يسرب")),
    FaultType("f_stuck", listOf("stuck", "عالق", "معلق", "لا يفتح", "لايفتح")),
    FaultType("f_pass", listOf("pass", "pahs", "paiss", "تمرير", "لا يغلق", "لايغلق")),
    FaultType("f_gasket", listOf("gasket", "gazket", "جوان", "واشر")),
    FaultType("f_lever", listOf("lever", "stem", "broke", "break", "ذراع", "كسر", "مكسور")),
    FaultType("f_isol", listOf("isol", "izol", "islo", "عزل", "معزول")),
    FaultType("f_conn", listOf("connect", "adapter", "وصلة", "مهايئ", "محول")),
    FaultType("f_clean", listOf("clean", "تنظيف", "نظافة")),
)

data class ShiftStats(
    val total: Int,
    val ok: Int,
    val faulty: Int,
    val na: Int,
    val pending: Int,
    val remarks: Int,
    val lastDay: Long?,
    val lastTimeMin: Int?,
) {
    val rate: Double get() = if (total == 0) 0.0 else ok.toDouble() / total
    val faultRate: Double get() = if (total == 0) 0.0 else faulty.toDouble() / total
}

/** All calculations behind the dashboard, the shift analysis and the report. */
class Analysis(private val all: List<Cabinet>, standard: List<Int>) {
    val standard: List<Int> = List(EQ_COUNT) { standard.getOrNull(it) ?: DEFAULT_STANDARD[it] }

    fun rows(shift: Char?): List<Cabinet> = if (shift == null) all else all.filter { it.shift == shift }

    fun stats(shift: Char?): ShiftStats {
        val r = rows(shift)
        val last = r.filter { it.epochDay != null }
            .maxWithOrNull(compareBy<Cabinet>({ it.epochDay }, { it.timeMin ?: -1 }))
        return ShiftStats(
            total = r.size,
            ok = r.count { it.status == Status.OK },
            faulty = r.count { it.status == Status.NO },
            na = r.count { it.status == Status.NA },
            pending = r.count { it.epochDay == null },
            remarks = r.count { it.remarks.isNotBlank() },
            lastDay = last?.epochDay,
            lastTimeMin = last?.timeMin,
        )
    }

    private fun matches(c: Cabinet, f: FaultType): Boolean {
        val t = c.remarks.lowercase()
        return t.isNotBlank() && f.keywords.any { t.contains(it) }
    }

    fun faultCount(shift: Char?, f: FaultType): Int = rows(shift).count { matches(it, f) }

    /** Remarks that matched none of the keyword groups. */
    fun otherCount(shift: Char?): Int =
        rows(shift).count { it.remarks.isNotBlank() && FAULT_TYPES.none { f -> matches(it, f) } }

    fun inStock(shift: Char?, k: Int): Int = rows(shift).sumOf { it.counts[k] ?: 0 }

    /** Units missing against the standard, counted only for cabinets with a figure entered. */
    fun short(shift: Char?, k: Int): Int =
        rows(shift).sumOf { c -> c.counts[k]?.let { n -> maxOf(0, standard[k] - n) } ?: 0 }

    fun totalShort(shift: Char?): Int = (0 until EQ_COUNT).sumOf { short(shift, it) }

    fun typesShort(shift: Char?): Int = (0 until EQ_COUNT).count { short(shift, it) > 0 }

    fun faulty(): List<Cabinet> = SHIFTS.flatMap { s -> rows(s).filter { it.status == Status.NO } }

    /** Best shift by operational rate (ties -> first). Null when there is no data. */
    fun bestShift(): Char? = SHIFTS.filter { rows(it).isNotEmpty() }.maxByOrNull { stats(it).rate }

    fun mostFaultsShift(): Char? =
        SHIFTS.filter { rows(it).isNotEmpty() }.maxByOrNull { stats(it).faulty }?.takeIf { stats(it).faulty > 0 }

    fun topFault(): Pair<FaultType, Int>? =
        FAULT_TYPES.map { it to faultCount(null, it) }.filter { it.second > 0 }.maxByOrNull { it.second }
}
