package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Writes the content of a print adapter (a WebView) straight to a PDF file.
 * It lives in the android.print package because the layout/write callbacks have package-private
 * constructors; this is the standard way to export a WebView to PDF without showing the print UI.
 */
class PdfPrint {
    private val attrs: PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    /** [done] receives null on success or an error message. Must be called on the main thread. */
    fun write(adapter: PrintDocumentAdapter, target: File, done: (String?) -> Unit) {
        try {
            adapter.onLayout(null, attrs, CancellationSignal(), object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    val pfd: ParcelFileDescriptor
                    try {
                        pfd = ParcelFileDescriptor.open(
                            target,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                        )
                    } catch (t: Throwable) {
                        done(t.message ?: "cannot open file")
                        return
                    }
                    adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, CancellationSignal(), object : PrintDocumentAdapter.WriteResultCallback() {
                        override fun onWriteFinished(pages: Array<out PageRange>?) {
                            try { pfd.close() } catch (_: Exception) {}
                            try { adapter.onFinish() } catch (_: Exception) {}
                            done(null)
                        }

                        override fun onWriteFailed(error: CharSequence?) {
                            try { pfd.close() } catch (_: Exception) {}
                            done(error?.toString() ?: "write failed")
                        }
                    })
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    done(error?.toString() ?: "layout failed")
                }
            }, null)
        } catch (t: Throwable) {
            // also catches IllegalAccessError on devices that block the android.print package trick
            done(t.message ?: "print failed")
        }
    }
}
