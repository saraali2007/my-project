package com.zfod.fireinspect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.InputStream

/** يخزن صور الصناديق محلياً بنسخة كاملة ومصغرة، ويرفعها إلى Firebase Storage */
object PhotoStore {
    private const val FULL = 1280
    private const val THUMB = 480

    // سحابة Firebase Storage الخاصة بمشروعك
    private val storage = FirebaseStorage.getInstance("gs://fire-inspection-b1c5a.firebasestorage.app")

    fun fullFile(ctx: Context, name: String) = File(File(ctx.filesDir, "photos").apply { mkdirs() }, name)
    fun thumbFile(ctx: Context, name: String) = File(File(ctx.filesDir, "thumbs").apply { mkdirs() }, name)

    /** استيراد الصورة وحفظها محلياً بأبعاد مناسبة لمنع نفاد الذاكرة */
    fun import(ctx: Context, open: () -> InputStream?): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= FULL) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val orientation = try {
                open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            val full = fit(bmp, FULL)
            val name = "p_" + System.currentTimeMillis() + "_" + (Math.random() * 10000).toInt() + ".jpg"
            save(full, fullFile(ctx, name), 85)
            val thumb = fit(full, THUMB)
            save(thumb, thumbFile(ctx, name), 75)
            if (thumb !== full) thumb.recycle()
            if (full !== bmp) bmp.recycle()
            full.recycle()
            name
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun fit(b: Bitmap, max: Int): Bitmap {
        val s = max.toFloat() / maxOf(b.width, b.height)
        if (s >= 1f) return b
        val targetW = (b.width * s).toInt().coerceAtLeast(1)
        val targetH = (b.height * s).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(b, targetW, targetH, true)
    }

    private fun save(b: Bitmap, f: File, q: Int) {
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, q, it) }
    }

    fun delete(ctx: Context, name: String) {
        try { fullFile(ctx, name).delete() } catch (_: Exception) {}
        try { thumbFile(ctx, name).delete() } catch (_: Exception) {}
    }

    fun loadThumb(ctx: Context, name: String): Bitmap? = try {
        BitmapFactory.decodeFile(thumbFile(ctx, name).absolutePath)
    } catch (_: Exception) {
        null
    }

    fun loadFull(ctx: Context, name: String): Bitmap? = try {
        BitmapFactory.decodeFile(fullFile(ctx, name).absolutePath)
    } catch (_: Exception) {
        null
    }

    /** دالة لرفع الصورة المحلية إلى Firebase Storage وإرجاع الرابط العام */
    suspend fun uploadToStorage(ctx: Context, photoName: String): String? {
        return try {
            val file = fullFile(ctx, photoName)
            if (!file.exists()) return null
            val ref = storage.reference.child("photos/$photoName")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
