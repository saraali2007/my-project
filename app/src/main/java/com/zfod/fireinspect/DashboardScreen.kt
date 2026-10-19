package com.zfod.fireinspect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(state: AppState, onOpenShift: (Char) -> Unit, onReport: () -> Unit) {
    val c = LocalColors.current
    val lang = LocalLang.current
    val cabinets = state.cabinets
    val standard = state.standard
    val an = remember(cabinets, standard) { Analysis(cabinets, standard) }
    val tot = an.stats(null)
    val summary = remember(an, lang) { ReportText.summary(an, lang) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Txt(t("overview"), size = 15, bold = true, color = c.accent, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(t("k_total"), tot.total.toString(), c.accent, Modifier.weight(1f), IconKind.CABINET)
                KpiCard(t("k_ok"), tot.ok.toString(), ColOk, Modifier.weight(1f), IconKind.HYDRANT)
                KpiCard(t("k_faulty"), tot.faulty.toString(), ColBad, Modifier.weight(1f), IconKind.WARN)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(t("k_na"), tot.na.toString(), ColNa, Modifier.weight(1f))
                KpiCard(t("k_pending"), tot.pending.toString(), ColPending, Modifier.weight(1f))
                KpiCard(t("k_rate"), Fmt.pct(tot.rate), ColRate, Modifier.weight(1f), bar = tot.rate.toFloat())
            }
        }
        item {
            ActionButton(t("report_print"), onReport, Modifier.fillMaxWidth(), icon = IconKind.PRINT)
        }

        item { SectionTitle(t("shift_comparison")) }
        items(SHIFTS, key = { it }) { s ->
            val st = an.stats(s)
            val col = shiftColor(s)
            Panel(onClick = { onOpenShift(s) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ChipBtn(tf("shift_x", s), true, col, { onOpenShift(s) })
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Txt(st.total.toString() + " " + t("cab_short"), size = 13, color = c.muted, modifier = Modifier.weight(1f))
                    if (st.faulty > 0) {
                        AppIcon(IconKind.WARN, ColBad, 18)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Txt(tf("n_faults", st.faulty).removePrefix("⚠ "), size = 12, bold = true, color = ColBad)
                    } else {
                        AppIcon(IconKind.CHECK, ColOk, 18)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Txt(t("healthy").removePrefix("✓ "), size = 12, bold = true, color = ColOk)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ShiftSummary(st)
            }
        }

        item { SectionTitle(t("faults_title")) }
        item { Panel { FaultList(an, null) } }

        item { SectionTitle(t("inventory_title")) }
        item { Panel { EquipmentTable(an, null) } }

        item { SectionTitle(t("summary_title")) }
        item {
            Panel {
                summary.forEachIndexed { i, line ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Txt(line.trim(), size = 13)
                }
            }
        }

        item {
            Txt(t("footer"), size = 10, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        }
    }
}
