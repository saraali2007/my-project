package com.zfod.fireinspect

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun toast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun Txt(
    text: String,
    size: Int = 14,
    bold: Boolean = false,
    color: Color = LocalColors.current.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun rememberAssetImage(name: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) {
        try {
            ctx.assets.open(name).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

// ---- icons (drawn, no icon library needed) ---------------------------------------------------------

enum class IconKind { BACK, ADD, CLOSE, GEAR, PRINT, SHARE, SAVE, DELETE, EDIT, CAMERA, GALLERY, CHECK, WARN, HYDRANT, CABINET, GLOBE, SYNC, USER, SHIELD }

@Composable
fun AppIcon(kind: IconKind, color: Color, size: Int = 22) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.09f
        val st = Stroke(width = sw, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        when (kind) {
            IconKind.BACK -> {
                val f = if (rtl) -1f else 1f
                fun fx(v: Float) = if (f > 0) v else 1f - v
                ln(fx(0.82f), 0.5f, fx(0.2f), 0.5f)
                ln(fx(0.46f), 0.24f, fx(0.2f), 0.5f)
                ln(fx(0.46f), 0.76f, fx(0.2f), 0.5f)
            }
            IconKind.ADD -> { ln(0.5f, 0.18f, 0.5f, 0.82f); ln(0.18f, 0.5f, 0.82f, 0.5f) }
            IconKind.CLOSE -> { ln(0.22f, 0.22f, 0.78f, 0.78f); ln(0.78f, 0.22f, 0.22f, 0.78f) }
            IconKind.GEAR -> {
                drawCircle(color, radius = w * 0.17f, center = p(0.5f, 0.5f), style = st)
                for (i in 0 until 8) {
                    val a = Math.PI * 2 * i / 8
                    val c = Math.cos(a).toFloat()
                    val s = Math.sin(a).toFloat()
                    ln(0.5f + c * 0.28f, 0.5f + s * 0.28f, 0.5f + c * 0.41f, 0.5f + s * 0.41f)
                }
                drawCircle(color, radius = w * 0.28f, center = p(0.5f, 0.5f), style = st)
            }
            IconKind.PRINT -> {
                drawRoundRect(color, p(0.14f, 0.36f), Size(w * 0.72f, h * 0.36f), CornerRadius(w * 0.08f), style = st)
                drawRect(color, p(0.28f, 0.14f), Size(w * 0.44f, h * 0.22f), style = st)
                drawRect(color, p(0.28f, 0.58f), Size(w * 0.44f, h * 0.28f), style = st)
            }
            IconKind.SHARE -> {
                ln(0.28f, 0.5f, 0.72f, 0.26f)
                ln(0.28f, 0.5f, 0.72f, 0.74f)
                drawCircle(color, w * 0.12f, p(0.26f, 0.5f))
                drawCircle(color, w * 0.12f, p(0.74f, 0.24f))
                drawCircle(color, w * 0.12f, p(0.74f, 0.76f))
            }
            IconKind.SAVE -> {
                ln(0.5f, 0.12f, 0.5f, 0.62f)
                ln(0.3f, 0.44f, 0.5f, 0.64f)
                ln(0.7f, 0.44f, 0.5f, 0.64f)
                ln(0.16f, 0.88f, 0.84f, 0.88f)
                ln(0.16f, 0.72f, 0.16f, 0.88f)
                ln(0.84f, 0.72f, 0.84f, 0.88f)
            }
            IconKind.DELETE -> {
                ln(0.16f, 0.26f, 0.84f, 0.26f)
                ln(0.38f, 0.14f, 0.62f, 0.14f)
                drawRect(color, p(0.26f, 0.32f), Size(w * 0.48f, h * 0.54f), style = st)
                ln(0.42f, 0.44f, 0.42f, 0.74f)
                ln(0.58f, 0.44f, 0.58f, 0.74f)
            }
            IconKind.EDIT -> {
                ln(0.2f, 0.8f, 0.76f, 0.24f)
                ln(0.2f, 0.8f, 0.2f, 0.62f)
                ln(0.2f, 0.8f, 0.38f, 0.8f)
                ln(0.62f, 0.14f, 0.86f, 0.38f)
            }
            IconKind.CAMERA -> {
                drawRoundRect(color, p(0.1f, 0.28f), Size(w * 0.8f, h * 0.56f), CornerRadius(w * 0.1f), style = st)
                drawCircle(color, radius = w * 0.17f, center = p(0.5f, 0.56f), style = st)
                ln(0.34f, 0.2f, 0.66f, 0.2f)
            }
            IconKind.GALLERY -> {
                drawRoundRect(color, p(0.1f, 0.16f), Size(w * 0.8f, h * 0.68f), CornerRadius(w * 0.08f), style = st)
                drawCircle(color, radius = w * 0.07f, center = p(0.32f, 0.38f))
                ln(0.14f, 0.78f, 0.42f, 0.5f)
                ln(0.42f, 0.5f, 0.6f, 0.68f)
                ln(0.6f, 0.68f, 0.7f, 0.58f)
                ln(0.7f, 0.58f, 0.86f, 0.74f)
            }
            IconKind.CHECK -> { ln(0.2f, 0.54f, 0.42f, 0.74f); ln(0.42f, 0.74f, 0.82f, 0.28f) }
            IconKind.WARN -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.92f, h * 0.86f)
                    lineTo(w * 0.08f, h * 0.86f)
                    close()
                }
                drawPath(path, color, style = st)
                ln(0.5f, 0.4f, 0.5f, 0.62f)
                drawCircle(color, radius = w * 0.05f, center = p(0.5f, 0.74f))
            }
            IconKind.HYDRANT -> {
                drawArc(color, 180f, 180f, false, p(0.3f, 0.14f), Size(w * 0.4f, h * 0.36f), style = st)
                drawRect(color, p(0.3f, 0.32f), Size(w * 0.4f, h * 0.48f), style = st)
                drawRect(color, p(0.2f, 0.8f), Size(w * 0.6f, h * 0.1f), style = st)
                ln(0.5f, 0.06f, 0.5f, 0.16f)
                ln(0.1f, 0.5f, 0.3f, 0.5f)
                ln(0.7f, 0.5f, 0.9f, 0.5f)
                drawCircle(color, radius = w * 0.06f, center = p(0.5f, 0.52f))
            }
            IconKind.CABINET -> {
                drawRoundRect(color, p(0.18f, 0.1f), Size(w * 0.64f, h * 0.8f), CornerRadius(w * 0.06f), style = st)
                ln(0.5f, 0.1f, 0.5f, 0.9f)
                drawRect(color, p(0.26f, 0.26f), Size(w * 0.16f, h * 0.26f), style = st)
                drawCircle(color, radius = w * 0.04f, center = p(0.62f, 0.5f))
            }
            IconKind.GLOBE -> {
                drawCircle(color, radius = w * 0.38f, center = p(0.5f, 0.5f), style = st)
                drawOval(color, p(0.32f, 0.12f), Size(w * 0.36f, h * 0.76f), style = st)
                ln(0.12f, 0.5f, 0.88f, 0.5f)
            }
            IconKind.SYNC -> {
                drawArc(color, 30f, 270f, false, p(0.2f, 0.2f), Size(w * 0.6f, h * 0.6f), style = st)
                ln(0.72f, 0.35f, 0.85f, 0.35f)
                ln(0.85f, 0.22f, 0.85f, 0.35f)
                drawArc(color, 210f, 270f, false, p(0.2f, 0.2f), Size(w * 0.6f, h * 0.6f), style = st)
                ln(0.28f, 0.65f, 0.15f, 0.65f)
                ln(0.15f, 0.78f, 0.15f, 0.65f)
            }
            IconKind.USER -> {
                drawCircle(color, radius = w * 0.22f, center = p(0.5f, 0.34f), style = st)
                drawArc(color, 0f, 180f, false, p(0.2f, 0.52f), Size(w * 0.6f, h * 0.38f), style = st)
            }
            IconKind.SHIELD -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.85f, h * 0.28f)
                    lineTo(w * 0.85f, h * 0.6f)
                    quadraticTo(w * 0.85f, h * 0.85f, w * 0.5f, h * 0.92f)
                    quadraticTo(w * 0.15f, h * 0.85f, w * 0.15f, h * 0.6f)
                    lineTo(w * 0.15f, h * 0.28f)
                    close()
                }
                drawPath(path, color, style = st)
            }
        }
    }
}

@Composable
fun IconBtn(kind: IconKind, onClick: () -> Unit, color: Color = LocalColors.current.text, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AppIcon(kind, color, 22) }
}

// ---- layout pieces ---------------------------------------------------------------------------------------

@Composable
private fun Logo(img: ImageBitmap?) {
    if (img != null) {
        Image(bitmap = img, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Spacer(Modifier.size(44.dp))
    }
}

/** Fixed header: ZFOD logo on the left, ENI logo on the right, report name in the middle. */
@Composable
fun AppHeader() {
    val c = LocalColors.current
    val zfod = rememberAssetImage("logo_zfod.jpg")
    val eni = rememberAssetImage("logo_eni.png")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().background(c.surface)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(zfod)
                Row(Modifier.weight(1f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    AppIcon(IconKind.HYDRANT, c.accent, 22)
                    Spacer(Modifier.width(6.dp))
                    Txt(Tr.get(Lang.EN, "app_title"), size = 12, bold = true, align = TextAlign.Center, maxLines = 3, modifier = Modifier.weight(1f, fill = false))
                }
                Logo(eni)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent))
        }
    }
}

@Composable
fun Toolbar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconBtn(IconKind.BACK, onBack)
        Txt(title, size = 17, bold = true, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1)
        actions()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    val base = modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.line, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(12.dp), content = content)
}

@Composable
fun SectionTitle(text: String, color: Color = LocalColors.current.accent) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Txt(text, size = 15, bold = true, color = color)
    }
}

@Composable
fun StatusPill(s: Status) {
    val txt = when (s) { Status.OK -> t("st_ok"); Status.NO -> t("st_no"); Status.NA -> t("st_na") }
    val col = when (s) { Status.OK -> ColOk; Status.NO -> ColBad; Status.NA -> ColNa }
    val shape = RoundedCornerShape(50)
    Box(Modifier.clip(shape).background(col.copy(alpha = 0.14f)).border(1.dp, col, shape).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Txt(txt, size = 12, bold = true, color = col)
    }
}

@Composable
fun KpiCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, icon: IconKind? = null, bar: Float? = null) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(c.card).border(1.5.dp, color, shape).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Column {
            Txt(label, size = 10, bold = true, color = color, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Txt(value, size = 22, bold = true, maxLines = 1)
            if (bar != null) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(bar, color)
            }
        }
        if (icon != null) {
            Box(Modifier.align(Alignment.BottomEnd)) { AppIcon(icon, color, 20) }
        }
    }
}

@Composable
fun ProgressBar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val f = if (frac.isNaN()) 0f else frac.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.line)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
    }
}

@Composable
fun ChipBtn(label: String, selected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, if (selected) color else c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = 13, bold = true, color = if (selected) Color.White else c.text, maxLines = 1)
    }
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconKind? = null,
    color: Color = LocalColors.current.accent,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (filled) color else Color.Transparent
    val fg = if (filled) Color.White else color
    Row(
        modifier.clip(shape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.5.dp, color.copy(alpha = if (enabled) 1f else 0.4f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) {
            AppIcon(icon, fg, 20)
            Spacer(Modifier.width(8.dp))
        }
        Txt(label, size = 14, bold = true, color = fg, maxLines = 1)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Txt(title, size = 18, bold = true) },
        text = { Txt(message, size = 14, color = c.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Txt(confirmLabel, size = 14, bold = true, color = if (danger) ColBad else c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Txt(t("cancel"), size = 14, bold = true, color = c.muted) }
        },
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier,
    )
}
