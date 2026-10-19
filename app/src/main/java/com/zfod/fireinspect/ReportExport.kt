package com.zfod.fireinspect

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.time.LocalTime

/** Builds the HTML / Excel report from the current data and manages export files. */
object ReportExport {
    private val uriCache = HashMap<String, String>()
    private val refCache = HashMap<String, ImgRef?>()

    private fun dataUri(bytes: ByteArray, mime: String): String =
        "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun assetBytes(ctx: Context, name: String): ByteArray? = try {
        ctx.assets.open(name).use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun refOf(bytes: ByteArray?, png: Boolean): ImgRef? {
        if (bytes == null) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        return ImgRef(bytes, o.outWidth, o.outHeight, png)
    }

    /** data: URI of the stored thumbnail, or null when the file is missing. */
    private fun photoUri(ctx: Context, name: String): String? {
        synchronized(uriCache) { uriCache[name]?.let { return it } }
        val f = PhotoStore.thumbFile(ctx, name)
        if (!f.exists()) return null
        val u = try { dataUri(f.readBytes(), "image/jpeg") } catch (e: Exception) { return null }
        synchronized(uriCache) { uriCache[name] = u }
        return u
    }

    private fun photoRef(ctx: Context, name: String): ImgRef? {
        synchronized(refCache) { if (refCache.containsKey(name)) return refCache[name] }
        val f = PhotoStore.thumbFile(ctx, name)
        val r = if (f.exists()) try { refOf(f.readBytes(), false) } catch (e: Exception) { null } else null
        synchronized(refCache) { refCache[name] = r }
        return r
    }

    fun options(
        lang: Lang, shifts: List<Char>, overview: Boolean, theme: ReportTheme, textColorIndex: Int, photos: Boolean,
    ): ReportOptions {
        val now = LocalTime.now()
        return ReportOptions(
            lang = lang, shifts = shifts, overview = overview, theme = theme, textColorIndex = textColorIndex, photos = photos,
            printedDate = Fmt.date(AppState.today()), printedTime = String.format(java.util.Locale.US, "%02d:%02d", now.hour, now.minute),
        )
    }

    fun buildHtml(ctx: Context, cabinets: List<Cabinet>, standard: List<Int>, opt: ReportOptions): String {
        val zf = assetBytes(ctx, "logo_zfod.jpg")?.let { dataUri(it, "image/jpeg") } ?: ""
        val eni = assetBytes(ctx, "logo_eni.png")?.let { dataUri(it, "image/png") } ?: ""
        return ReportBuilder(cabinets, standard, opt, { n -> photoUri(ctx, n) }, zf, eni).build()
    }

    fun buildXlsx(ctx: Context, cabinets: List<Cabinet>, standard: List<Int>, opt: ReportOptions): ByteArray {
        return PoiExcelExporter.buildXlsxBytes(ctx, cabinets)
    }

    /** Photos that were deleted are removed from the caches. */
    fun clearCaches() {
        synchronized(uriCache) { uriCache.clear() }
        synchronized(refCache) { refCache.clear() }
    }

    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun fileName(shifts: List<Char>, overview: Boolean, ext: String): String {
        val part = when {
            shifts.size == SHIFTS.size && overview -> "All"
            shifts.isEmpty() -> "Dashboard"
            else -> "Shift" + shifts.joinToString("") + (if (overview) "_Dashboard" else "")
        }
        return "FireInspection_" + part + "_" + Fmt.date(AppState.today()) + "." + ext
    }

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
