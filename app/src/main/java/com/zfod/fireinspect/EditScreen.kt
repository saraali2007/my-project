package com.zfod.fireinspect

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditScreen(state: AppState, original: Cabinet, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()

    val canEdit = state.canEdit(original.shift)

    var tag by remember { mutableStateOf(original.tagBox) }
    var hyd by remember { mutableStateOf(original.hydrantTag) }
    var shift by remember { mutableStateOf(original.shift) }
    var status by remember { mutableStateOf(original.status) }
    var notes by remember { mutableStateOf(original.notes) }
    var counts by remember { mutableStateOf(original.counts.map { it?.toString() ?: "" }) }
    var day by remember { mutableStateOf(original.epochDay) }
    var minutes by remember { mutableStateOf(original.timeMin) }
    var remarks by remember { mutableStateOf(original.remarks) }
    var location by remember { mutableStateOf(original.location) }
    var photos by remember { mutableStateOf(original.photos) }
    val created = remember { mutableListOf<String>() }

    var pickSlot by remember { mutableStateOf(0) }
    var showSource by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val failMsg = t("ed_photo_fail")
    val needTag = t("ed_tag_req")

    fun importPhoto(slot: Int, after: () -> Unit, open: () -> InputStream?) {
        if (!canEdit) return
        scope.launch {
            busy = true
            val name = withContext(Dispatchers.IO) { PhotoStore.import(ctx, open) }
            busy = false
            after()
            if (name == null) {
                toast(ctx, failMsg)
            } else {
                created.add(name)
                val l = photos.toMutableList()
                l[slot] = name
                photos = l
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) importPhoto(pickSlot, {}) { ctx.contentResolver.openInputStream(uri) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val f = cameraFile
        cameraFile = null
        if (f != null) {
            if (ok) importPhoto(pickSlot, { f.delete() }) { f.inputStream() } else f.delete()
        }
    }

    fun cancel() {
        created.forEach { PhotoStore.delete(ctx, it) }
        onClose()
    }

    fun save() {
        if (!canEdit) return
        if (tag.isBlank()) {
            toast(ctx, needTag)
            return
        }
        val cab = original.copy(
            tagBox = tag.trim(),
            hydrantTag = hyd.trim(),
            shift = shift,
            status = status,
            notes = notes.trim(),
            counts = counts.map { it.trim().toIntOrNull()?.coerceIn(0, 9999) },
            epochDay = day,
            timeMin = minutes,
            remarks = remarks.trim(),
            location = location.trim(),
            photos = photos,
        )
        val keep = photos.filter { it.isNotEmpty() }.toSet()
        (original.photos + created).filter { it.isNotEmpty() && it !in keep }.toSet().forEach { PhotoStore.delete(ctx, it) }
        state.upsert(cab)
        onClose()
    }

    BackHandler { cancel() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        AppHeader()
        Toolbar(if (isNew) t("new_cabinet") else t("edit_cabinet"), { cancel() }) {
            if (!isNew && canEdit) IconBtn(IconKind.DELETE, { confirmDelete = true }, ColBad)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!canEdit) {
                Spacer(Modifier.height(4.dp))
                Panel {
                    Txt(Tr.f(lang, "perm_denied", original.shift), size = 13, bold = true, color = ColBad)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(tag, { if (canEdit) tag = it }, t("h_tag"), Modifier.weight(1f))
                Field(hyd, { if (canEdit) hyd = it }, t("h_hydrant"), Modifier.weight(1f))
            }

            Txt(t("h_shift"), size = 12, bold = true, color = c.muted)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHIFTS.forEach { s ->
                    ChipBtn(tf("shift_x", s), s == shift, shiftColor(s), { if (canEdit) shift = s })
                }
            }
            if (!isNew && shift != original.shift) Txt(t("ed_move_note"), size = 11, color = c.muted)

            Txt(t("ed_status"), size = 12, bold = true, color = c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipBtn(t("st_ok"), status == Status.OK, ColOk, { if (canEdit) status = Status.OK }, Modifier.weight(1f))
                ChipBtn(t("st_no"), status == Status.NO, ColBad, { if (canEdit) status = Status.NO }, Modifier.weight(1f))
                ChipBtn(t("st_na"), status == Status.NA, ColNa, { if (canEdit) status = Status.NA }, Modifier.weight(1f))
            }

            Field(notes, { if (canEdit) notes = it }, t("ed_notes"), Modifier.fillMaxWidth(), singleLine = false, minLines = 2)

            Txt(t("ed_counts"), size = 12, bold = true, color = c.muted)
            for (row in 0 until EQ_COUNT / 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in row * 2 until row * 2 + 2) {
                        Field(counts[k], { v ->
                            if (canEdit) {
                                val l = counts.toMutableList()
                                l[k] = v.filter { ch -> ch.isDigit() }.take(4)
                                counts = l
                            }
                        }, ReportText.eqShort(lang, k), Modifier.weight(1f), number = true)
                    }
                }
            }
            Txt(t("ed_counts_note"), size = 11, color = c.muted)

            Txt(t("insp_date") + " / " + t("insp_time"), size = 12, bold = true, color = c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    if (day == null) t("no_date") else Fmt.date(day),
                    {
                        if (canEdit) {
                            val d = LocalDate.ofEpochDay(day ?: AppState.today())
                            DatePickerDialog(ctx, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        }
                    },
                    Modifier.weight(1f), filled = false, color = c.accent,
                )
                ActionButton(
                    if (minutes == null) "--:--" else Fmt.time(minutes),
                    {
                        if (canEdit) {
                            val m = minutes ?: (LocalTime.now().hour * 60 + LocalTime.now().minute)
                            TimePickerDialog(ctx, { _, hh, mm -> minutes = hh * 60 + mm }, m / 60, m % 60, true).show()
                        }
                    },
                    Modifier.weight(1f), filled = false, color = c.accent,
                )
            }
            if (canEdit) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipBtn(t("ed_now"), false, c.accent, {
                        day = AppState.today()
                        minutes = LocalTime.now().hour * 60 + LocalTime.now().minute
                    })
                    ChipBtn(t("ed_today"), false, c.accent, { day = AppState.today() })
                    if (day != null || minutes != null) ChipBtn("✕", false, ColBad, { day = null; minutes = null })
                }
            }

            Field(remarks, { if (canEdit) remarks = it }, t("h_remarks"), Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
            Field(location, { if (canEdit) location = it }, t("h_location"), Modifier.fillMaxWidth())

            Txt(t("ed_photos"), size = 12, bold = true, color = c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until PHOTO_SLOTS) {
                    val p = photos[i]
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        val shape = RoundedCornerShape(12.dp)
                        if (p.isNotEmpty()) {
                            Box(Modifier.fillMaxSize().clip(shape).clickable { if (canEdit) { pickSlot = i; showSource = true } }) {
                                PhotoThumb(p, Modifier.fillMaxSize(), null)
                            }
                            if (canEdit) {
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).clip(RoundedCornerShape(15.dp)).background(ColBad).clickable {
                                    val l = photos.toMutableList()
                                    l[i] = ""
                                    photos = l
                                }, contentAlignment = Alignment.Center) { AppIcon(IconKind.CLOSE, androidx.compose.ui.graphics.Color.White, 16) }
                            }
                        } else {
                            Box(
                                Modifier.fillMaxSize().clip(shape).border(1.5.dp, c.line, shape).clickable { if (canEdit) { pickSlot = i; showSource = true } },
                                contentAlignment = Alignment.Center,
                            ) { AppIcon(IconKind.CAMERA, c.muted, 30) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().background(c.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(t("cancel"), { cancel() }, Modifier.weight(1f), filled = false, color = c.muted)
            if (canEdit) {
                ActionButton(t("save"), { save() }, Modifier.weight(1f), icon = IconKind.CHECK, enabled = !busy)
            }
        }
    }

    if (busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
    }

    if (showSource && canEdit) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            containerColor = c.surface,
            title = { Txt(t("ed_add_photo"), size = 18, bold = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(t("ed_gallery"), {
                        showSource = false
                        try { gallery.launch("image/*") } catch (_: Exception) { toast(ctx, failMsg) }
                    }, Modifier.fillMaxWidth(), icon = IconKind.GALLERY)
                    ActionButton(t("ed_camera"), {
                        showSource = false
                        try {
                            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                            val f = File(dir, "cam_" + System.currentTimeMillis() + ".jpg")
                            cameraFile = f
                            camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f))
                        } catch (_: Exception) {
                            cameraFile = null
                            toast(ctx, failMsg)
                        }
                    }, Modifier.fillMaxWidth(), icon = IconKind.CAMERA)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSource = false }) { Txt(t("cancel"), size = 14, bold = true, color = c.muted) } },
        )
    }

    if (confirmDelete && canEdit) {
        ConfirmDialog(
            title = t("delete"),
            message = tf("ed_delete_q", original.tagBox.ifBlank { "—" }),
            confirmLabel = t("delete"),
            onConfirm = {
                confirmDelete = false
                created.forEach { PhotoStore.delete(ctx, it) }
                state.delete(original.id)
                onClose()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}
