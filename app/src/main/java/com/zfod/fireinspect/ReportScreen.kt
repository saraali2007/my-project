package com.zfod.fireinspect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.print.PdfPrint
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportScreen(state: AppState, initialShift: Char?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val appLang = LocalLang.current
    val scope = rememberCoroutineScope()

    var shifts by remember { mutableStateOf(if (initialShift != null) setOf(initialShift) else SHIFTS.toSet()) }
    var overview by remember { mutableStateOf(initialShift == null) }
    var repLang by remember { mutableStateOf(state.lang) }
    var showOpts by remember { mutableStateOf(false) }
    var html by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var formatFor by remember { mutableStateOf<String?>(null) } // "save" or "share"

    val selShifts = SHIFTS.filter { it in shifts }
    val nothing = selShifts.isEmpty() && !overview
    val allSelected = selShifts.size == SHIFTS.size && overview

    // ---- the preview WebView (also used for printing and for the PDF export) --------------------------------
    val webView = remember {
        WebView(ctx).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            if (Build.VERSION.SDK_INT in 29..32) {
                @Suppress("DEPRECATION")
                settings.forceDark = WebSettings.FORCE_DARK_OFF
            }
        }
    }
    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
            }
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // ---- (re)build the report whenever something it depends on changes ----------------------------------------
    val cabinets = state.cabinets
    val standard = state.standard
    val theme = state.repTheme
    val textColor = state.repTextColor
    val withPhotos = state.repPhotos
    LaunchedEffect(cabinets, standard, shifts, overview, repLang, theme, textColor, withPhotos) {
        building = true
        if (nothing) {
            html = null
            building = false
            return@LaunchedEffect
        }
        val opt = ReportExport.options(repLang, selShifts, overview, theme, textColor, withPhotos)
        val built = withContext(Dispatchers.Default) { ReportExport.buildHtml(ctx, cabinets, standard, opt) }
        html = built
        building = false
    }
    LaunchedEffect(html) {
        val h = html
        if (h != null) {
            ready = false
            webView.loadDataWithBaseURL(null, h, "text/html", "UTF-8", null)
        }
    }

    // ---- actions ---------------------------------------------------------------------------------------------------
    val jobName = ReportExport.fileName(selShifts, overview, "").removeSuffix(".")
    fun fail(msg: String?) {
        toast(ctx, Tr.f(appLang, "rp_failed", msg ?: "?"))
    }

    fun doPrint() {
        try {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            pm.print(jobName, webView.createPrintDocumentAdapter(jobName), attrs)
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    /** Writes the previewed report to [target] as PDF; falls back to the system print dialog on failure. */
    fun makePdf(target: File, done: () -> Unit) {
        busy = true
        try {
            PdfPrint().write(webView.createPrintDocumentAdapter(jobName), target) { err ->
                busy = false
                if (err == null) {
                    done()
                } else {
                    toast(ctx, Tr.get(appLang, "rp_fallback"))
                    doPrint()
                }
            }
        } catch (t: Throwable) {
            busy = false
            toast(ctx, Tr.get(appLang, "rp_fallback"))
            doPrint()
        }
    }

    fun currentOptions(): ReportOptions = ReportExport.options(repLang, selShifts, overview, theme, textColor, withPhotos)

    fun shareFile(file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, Tr.get(appLang, "rp_share_title")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    fun saveFileTo(src: File, uri: Uri) {
        scope.launch {
            busy = true
            val err = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                    null
                } catch (e: Exception) {
                    e.message ?: "?"
                }
            }
            busy = false
            if (err == null) toast(ctx, Tr.get(appLang, "rp_saved")) else fail(err)
        }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportExport.MIME_PDF)) { uri: Uri? ->
        if (uri != null) {
            val tmp = File(ReportExport.exportDir(ctx), "save.pdf")
            makePdf(tmp) { saveFileTo(tmp, uri) }
        }
    }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportExport.MIME_XLSX)) { uri: Uri? ->
        if (uri != null) {
            val opt = currentOptions()
            scope.launch {
                busy = true
                val err = withContext(Dispatchers.Default) {
                    try {
                        val bytes = ReportExport.buildXlsx(ctx, cabinets, standard, opt)
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        null
                    } catch (e: Exception) {
                        e.message ?: "?"
                    }
                }
                busy = false
                if (err == null) toast(ctx, Tr.get(appLang, "rp_saved")) else fail(err)
            }
        }
    }

    fun chooseFormat(pdf: Boolean) {
        val mode = formatFor
        formatFor = null
        if (mode == "save") {
            try {
                if (pdf) savePdf.launch(ReportExport.fileName(selShifts, overview, "pdf"))
                else saveXlsx.launch(ReportExport.fileName(selShifts, overview, "xlsx"))
            } catch (e: Exception) {
                fail(e.message)
            }
        } else if (mode == "share") {
            if (pdf) {
                val f = File(ReportExport.exportDir(ctx), ReportExport.fileName(selShifts, overview, "pdf"))
                makePdf(f) { shareFile(f, ReportExport.MIME_PDF) }
            } else {
                val opt = currentOptions()
                val name = ReportExport.fileName(selShifts, overview, "xlsx")
                scope.launch {
                    busy = true
                    val res = withContext(Dispatchers.Default) {
                        try {
                            val f = File(ReportExport.exportDir(ctx), name)
                            f.writeBytes(ReportExport.buildXlsx(ctx, cabinets, standard, opt))
                            f to null
                        } catch (e: Exception) {
                            null to (e.message ?: "?")
                        }
                    }
                    busy = false
                    val file = res.first
                    if (file != null) shareFile(file, ReportExport.MIME_XLSX) else fail(res.second)
                }
            }
        }
    }

    // ---- UI ---------------------------------------------------------------------------------------------------------
    val canAct = ready && !building && !busy && !nothing
    Column(Modifier.fillMaxSize().background(c.bg)) {
        AppHeader()
        Toolbar(t("report_print"), onBack)

        // shift buttons: print one shift, several, or everything
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipBtn(t("rp_all"), allSelected, c.accent, { shifts = SHIFTS.toSet(); overview = true })
            SHIFTS.forEach { s ->
                ChipBtn(tf("shift_x", s), s in shifts, shiftColor(s), {
                    shifts = if (shifts.size == SHIFTS.size) setOf(s) else if (s in shifts) shifts - s else shifts + s
                })
            }
            ChipBtn(t("rp_overview"), overview, c.accent, { overview = !overview })
            ChipBtn(t("rp_options"), showOpts, c.muted, { showOpts = !showOpts })
        }

        if (showOpts) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panel {
                    Txt(t("rp_lang"), size = 12, bold = true, color = c.muted)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipBtn(Tr.get(Lang.AR, "lang_ar"), repLang == Lang.AR, c.accent, { repLang = Lang.AR }, Modifier.weight(1f))
                        ChipBtn(Tr.get(Lang.EN, "lang_en"), repLang == Lang.EN, c.accent, { repLang = Lang.EN }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt(t("rp_style"), size = 12, bold = true, color = c.muted)
                    Spacer(Modifier.height(6.dp))
                    ReportThemeChips(state)
                    Spacer(Modifier.height(10.dp))
                    Txt(t("rp_text"), size = 12, bold = true, color = c.muted)
                    Spacer(Modifier.height(6.dp))
                    ReportTextColorSwatches(state)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(t("rp_photos"), size = 14, modifier = Modifier.weight(1f))
                        Switch(checked = state.repPhotos, onCheckedChange = { state.applyRepPhotos(it) })
                    }
                }
            }
        }

        // preview: the full report exactly as it will be printed / saved
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (nothing) {
                Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                    Txt(t("rp_nothing"), size = 14, color = c.muted, align = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
            } else if (!ready || building || busy) {
                Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = if (html == null) 1f else 0.55f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.accent)
                        Spacer(Modifier.height(10.dp))
                        Txt(if (busy) t("rp_working") else t("rp_preparing"), size = 13, color = c.muted)
                    }
                }
            }
        }

        // actions: print / save / share
        Row(Modifier.fillMaxWidth().background(c.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(t("rp_print"), { doPrint() }, Modifier.weight(1f), icon = IconKind.PRINT, enabled = canAct)
            ActionButton(t("rp_save"), { formatFor = "save" }, Modifier.weight(1f), icon = IconKind.SAVE, color = ColOk, enabled = canAct)
            ActionButton(t("rp_share"), { formatFor = "share" }, Modifier.weight(1f), icon = IconKind.SHARE, color = ColPending, enabled = canAct)
        }
    }

    if (formatFor != null) {
        AlertDialog(
            onDismissRequest = { formatFor = null },
            containerColor = c.surface,
            title = { Txt(t("rp_format"), size = 18, bold = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(t("fmt_pdf"), { chooseFormat(true) }, Modifier.fillMaxWidth(), color = ColBad)
                    ActionButton(t("fmt_xlsx"), { chooseFormat(false) }, Modifier.fillMaxWidth(), color = ColOk)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { formatFor = null }) { Txt(t("cancel"), size = 14, bold = true, color = c.muted) } },
        )
    }
}
