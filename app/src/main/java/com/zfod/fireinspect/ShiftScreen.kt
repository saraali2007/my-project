package com.zfod.fireinspect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Short, language-independent equipment labels for the compact count row. */
private val EQ_ABBR = listOf("2.5″H", "1.5″H", "4″H", "2.5″N", "2.5″C", "1.5″C", "K6″", "K4″")

@Composable
fun PhotoThumb(name: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { PhotoStore.loadThumb(ctx, name)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(8.dp)
    val base = modifier.clip(shape).background(c.line).border(1.dp, c.line, shape)
    val mod = if (onClick != null) base.clickable(onClick = onClick) else base
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
    } else {
        Box(mod)
    }
}

@Composable
fun FullPhotoDialog(name: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { PhotoStore.loadFull(ctx, name)?.asImageBitmap() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) { IconBtn(IconKind.CLOSE, onClose, Color.White) }
        }
    }
}

@Composable
private fun CabinetCard(c: Cabinet, standard: List<Int>, onEdit: () -> Unit, onPhoto: (String) -> Unit) {
    val col = LocalColors.current
    val bad = c.status == Status.NO
    Panel(onClick = onEdit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(c.tagBox.ifBlank { "—" }, size = 17, bold = true)
            Txt("  ·  ", size = 14, color = col.muted)
            Txt(c.hydrantTag.ifBlank { "—" }, size = 15, color = col.muted, modifier = Modifier.weight(1f), maxLines = 1)
            if (bad) {
                AppIcon(IconKind.WARN, ColBad, 18)
                Spacer(Modifier.width(6.dp))
            }
            StatusPill(c.status)
        }
        if (c.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Txt(c.notes, size = 12, color = col.muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (k in 0 until EQ_COUNT) {
                val n = c.counts[k]
                val low = n != null && n < standard[k]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(n?.toString() ?: "–", size = 14, bold = true, color = if (low) ColBad else col.text, maxLines = 1)
                    Txt(EQ_ABBR[k], size = 8, color = col.muted, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        val dt = (Fmt.date(c.epochDay) + "  " + Fmt.time(c.timeMin)).trim()
        Spacer(Modifier.height(6.dp))
        Txt(if (dt.isEmpty()) t("no_date") else dt, size = 11, color = col.muted)
        if (c.remarks.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Txt(c.remarks, size = 12, color = if (bad) ColBad else col.text)
        }
        if (c.location.isNotBlank()) {
            Txt(t("h_location") + ": " + c.location, size = 11, color = col.muted)
        }
        if (c.hasPhotos) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.photos.filter { it.isNotEmpty() }.forEach { p -> PhotoThumb(p, Modifier.size(76.dp)) { onPhoto(p) } }
            }
        }
    }
}

@Composable
fun ShiftScreen(
    state: AppState,
    shift: Char,
    onShift: (Char) -> Unit,
    onEdit: (Cabinet) -> Unit,
    onAdd: () -> Unit,
) {
    val c = LocalColors.current
    val cabinets = state.cabinets
    val standard = state.standard
    val an = remember(cabinets, standard) { Analysis(cabinets, standard) }
    val st = an.stats(shift)
    val shiftCol = shiftColor(shift)
    var query by remember { mutableStateOf("") }
    var onlyFaulty by remember { mutableStateOf(false) }
    var photoOpen by remember { mutableStateOf<String?>(null) }

    val rows = an.rows(shift).filter { r ->
        (!onlyFaulty || r.status == Status.NO) &&
            (query.isBlank() || r.tagBox.contains(query.trim(), true) || r.hydrantTag.contains(query.trim(), true) || r.remarks.contains(query.trim(), true))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHIFTS.forEach { s -> ChipBtn(tf("shift_x", s), s == shift, shiftColor(s), { onShift(s) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard(t("k_total"), st.total.toString(), shiftCol, Modifier.weight(1f), IconKind.CABINET)
                    KpiCard(t("k_ok"), st.ok.toString(), ColOk, Modifier.weight(1f), IconKind.HYDRANT)
                    KpiCard(t("k_faulty"), st.faulty.toString(), ColBad, Modifier.weight(1f), IconKind.WARN)
                    KpiCard(t("k_rate"), Fmt.pct0(st.rate), ColRate, Modifier.weight(1f))
                }
            }
            item {
                Field(query, { query = it }, t("search_hint"), Modifier.fillMaxWidth())
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChipBtn(t("all"), !onlyFaulty, c.accent, { onlyFaulty = false })
                    ChipBtn(t("faulty_only"), onlyFaulty, ColBad, { onlyFaulty = true })
                    Txt(t("tap_edit"), size = 11, color = c.muted, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                }
            }
            if (rows.isEmpty()) {
                item {
                    Txt(t("no_cabinets"), size = 14, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
                }
            }
            items(rows, key = { it.id }) { r ->
                CabinetCard(r, an.standard, { onEdit(r) }, { p -> photoOpen = p })
            }
            item { SectionTitle(tf("shift_analysis", shift), shiftCol) }
            item { Panel { ShiftSummary(st) } }
            item { SectionTitle(t("fault_type"), shiftCol) }
            item { Panel { FaultList(an, shift) } }
            item { SectionTitle(t("equip_vs_std"), shiftCol) }
            item { Panel { EquipmentTable(an, shift) } }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ActionButton(t("add_cabinet"), onAdd, icon = IconKind.ADD, color = shiftCol)
        }
    }
    val po = photoOpen
    if (po != null) FullPhotoDialog(po) { photoOpen = null }
}
