package com.zfod.fireinspect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Fault classification table (counts from the Remarks column). [shift] = null means all shifts. */
@Composable
fun FaultList(an: Analysis, shift: Char?) {
    val c = LocalColors.current
    val lang = LocalLang.current
    val counts = FAULT_TYPES.map { an.faultCount(shift, it) }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FAULT_TYPES.forEachIndexed { i, f ->
            val n = counts[i]
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(ReportText.faultName(lang, f.key), size = 13, modifier = Modifier.weight(1f))
                    Txt(n.toString(), size = 14, bold = true, color = if (n > 0) ColBad else c.muted)
                }
                Spacer(Modifier.height(3.dp))
                ProgressBar(n.toFloat() / max, if (n > 0) ColBad else c.line)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(t("f_other"), size = 13, modifier = Modifier.weight(1f))
            Txt(an.otherCount(shift).toString(), size = 14, bold = true)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(t("remarks_logged"), size = 13, bold = true, modifier = Modifier.weight(1f))
            Txt(an.rows(shift).count { it.remarks.isNotBlank() }.toString(), size = 14, bold = true, color = c.accent)
        }
    }
}

/** Equipment in stock vs the standard per cabinet. [shift] = null means all shifts. */
@Composable
fun EquipmentTable(an: Analysis, shift: Char?) {
    val c = LocalColors.current
    val lang = LocalLang.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Txt(t("equipment"), size = 10, bold = true, color = c.muted, modifier = Modifier.weight(2.2f))
            Txt(t("in_stock"), size = 10, bold = true, color = c.muted, modifier = Modifier.weight(1f), align = TextAlign.Center)
            Txt(t("standard"), size = 10, bold = true, color = c.muted, modifier = Modifier.weight(1f), align = TextAlign.Center)
            Txt(t("short"), size = 10, bold = true, color = c.muted, modifier = Modifier.weight(1f), align = TextAlign.Center)
        }
        for (k in 0 until EQ_COUNT) {
            val sh = an.short(shift, k)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(ReportText.eqLong(lang, k), size = 13, modifier = Modifier.weight(2.2f))
                Txt(an.inStock(shift, k).toString(), size = 13, modifier = Modifier.weight(1f), align = TextAlign.Center)
                Txt(an.standard[k].toString(), size = 13, color = c.muted, modifier = Modifier.weight(1f), align = TextAlign.Center)
                Txt(sh.toString(), size = 14, bold = true, color = if (sh > 0) ColBad else ColOk, modifier = Modifier.weight(1f), align = TextAlign.Center)
            }
        }
        Txt(t("short_note"), size = 10, color = c.muted)
    }
}

/** Short operational summary of one shift. */
@Composable
fun ShiftSummary(st: ShiftStats) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(t("rate_title"), size = 13, bold = true, modifier = Modifier.weight(1f))
            Txt(Fmt.pct(st.rate), size = 18, bold = true, color = ColRate)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(st.rate.toFloat(), ColOk)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(t("st_ok") + ": " + st.ok, size = 12, bold = true, color = ColOk)
            Txt(t("st_no") + ": " + st.faulty, size = 12, bold = true, color = ColBad)
            Txt(t("st_na") + ": " + st.na, size = 12, bold = true, color = ColNa)
            Txt(t("k_pending") + ": " + st.pending, size = 12, bold = true, color = ColPending)
        }
        if (st.lastDay != null) {
            Spacer(Modifier.height(4.dp))
            Txt(t("last_check") + ": " + Fmt.date(st.lastDay) + (if (st.lastTimeMin != null) "  " + Fmt.time(st.lastTimeMin) else ""), size = 11, color = c.muted)
        }
    }
}
