package com.zfod.fireinspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Report style chips (white / light blue / pink / soft dark / deep dark). */
@Composable
fun ReportThemeChips(state: AppState) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportTheme.values().forEach { th ->
            val sel = th == state.repTheme
            val swatch = parseHex(th.page)
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            Row(
                Modifier.clip(shape)
                    .background(if (sel) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(if (sel) 2.dp else 1.dp, if (sel) c.accent else c.line, shape)
                    .clickable { state.applyRepTheme(th) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(swatch).border(1.dp, c.muted, CircleShape))
                Spacer(Modifier.width(8.dp))
                Txt(t("th_" + th.key), size = 13, bold = true, maxLines = 1)
            }
        }
    }
}

/** Eight report text colours; the first one is automatic (follows the report style). */
@Composable
fun ReportTextColorSwatches(state: AppState) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        REPORT_TEXT_COLORS.forEachIndexed { i, hex ->
            val sel = i == state.repTextColor
            if (hex == null) {
                ChipBtn(t("rp_auto"), sel, c.accent, { state.applyRepTextColor(i) })
            } else {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(parseHex(hex))
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else c.muted, CircleShape)
                        .clickable { state.applyRepTextColor(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) AppIcon(IconKind.CHECK, if (hex == "#FFFFFF" || hex == "#F9A825") Color.Black else Color.White, 20)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }
    val restoredMsg = t("st_restored")

    Column(Modifier.fillMaxSize().background(c.bg)) {
        AppHeader()
        Toolbar(t("settings"), onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- language --------------------------------------------------------------------
            SectionTitle(t("st_language"))
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipBtn(Tr.get(Lang.AR, "lang_ar"), state.lang == Lang.AR, c.accent, { state.applyLang(Lang.AR) }, Modifier.weight(1f))
                    ChipBtn(Tr.get(Lang.EN, "lang_en"), state.lang == Lang.EN, c.accent, { state.applyLang(Lang.EN) }, Modifier.weight(1f))
                }
            }

            // ---- appearance --------------------------------------------------------------------
            SectionTitle(t("st_appearance"))
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipBtn(t("mode_dark"), state.appMode == 0, c.accent, { state.applyAppMode(0) }, Modifier.weight(1f))
                    ChipBtn(t("mode_white"), state.appMode == 1, c.accent, { state.applyAppMode(1) }, Modifier.weight(1f))
                    ChipBtn(t("mode_eye"), state.appMode == 2, c.accent, { state.applyAppMode(2) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Txt(t("st_accent"), size = 12, bold = true, color = c.muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    APP_ACCENTS.forEachIndexed { i, argb ->
                        val sel = i == state.accent
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Color(argb))
                                .border(if (sel) 3.dp else 1.dp, if (sel) c.text else c.line, CircleShape)
                                .clickable { state.applyAccent(i) },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) AppIcon(IconKind.CHECK, Color.White, 22) }
                    }
                }
            }

            // ---- size -----------------------------------------------------------------------------
            SectionTitle(t("st_scale"))
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ChipBtn("−", false, c.accent, { state.applyScale(state.scale - 10) })
                    Txt(state.scale.toString() + "%", size = 24, bold = true, color = c.accent, align = TextAlign.Center, modifier = Modifier.weight(1f))
                    ChipBtn("+", false, c.accent, { state.applyScale(state.scale + 10) })
                }
                Slider(
                    value = state.scale.toFloat(),
                    onValueChange = { state.applyScale(it.roundToInt()) },
                    valueRange = 10f..300f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("10%", size = 11, color = c.muted)
                    Txt("300%", size = 11, color = c.muted)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                    Txt("Aa  أب  123", size = (18f * state.scale / 100f).roundToInt().coerceIn(1, 60), bold = true, color = c.accent)
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(t("st_scale_reset"), { state.applyScale(100) }, Modifier.fillMaxWidth(), filled = false)
                Spacer(Modifier.height(8.dp))
                Txt(t("st_scale_note"), size = 11, color = c.muted)
            }

            // ---- report defaults ----------------------------------------------------------------------
            SectionTitle(t("st_report_defaults"))
            Panel {
                Txt(t("rp_style"), size = 12, bold = true, color = c.muted)
                Spacer(Modifier.height(8.dp))
                ReportThemeChips(state)
                Spacer(Modifier.height(14.dp))
                Txt(t("rp_text"), size = 12, bold = true, color = c.muted)
                Spacer(Modifier.height(8.dp))
                ReportTextColorSwatches(state)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(t("rp_photos"), size = 14, modifier = Modifier.weight(1f))
                    Switch(checked = state.repPhotos, onCheckedChange = { state.applyRepPhotos(it) })
                }
            }

            // ---- standard quantity -------------------------------------------------------------------------
            SectionTitle(t("st_standard"))
            Panel {
                for (k in 0 until EQ_COUNT) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(ReportText.eqLong(state.lang, k), size = 14, modifier = Modifier.weight(1f))
                        ChipBtn("−", false, c.accent, { state.setStandard(k, state.standard[k] - 1) })
                        Txt(state.standard[k].toString(), size = 17, bold = true, align = TextAlign.Center, modifier = Modifier.width(44.dp))
                        ChipBtn("+", false, c.accent, { state.setStandard(k, state.standard[k] + 1) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Txt(t("st_standard_note"), size = 11, color = c.muted)
            }

            // ---- data ----------------------------------------------------------------------------------------------
            SectionTitle(t("st_data"))
            Panel {
                Txt(tf("st_cabinets_now", state.cabinets.size), size = 13, color = c.muted)
                Spacer(Modifier.height(8.dp))
                ActionButton(t("st_restore"), { confirmRestore = true }, Modifier.fillMaxWidth(), color = ColBad, filled = false)
            }
            Txt(t("footer"), size = 10, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = t("st_restore"),
            message = t("st_restore_q"),
            confirmLabel = t("continue_"),
            onConfirm = {
                confirmRestore = false
                state.restoreOriginal()
                toast(ctx, restoredMsg)
            },
            onDismiss = { confirmRestore = false },
            danger = true,
        )
    }
}
