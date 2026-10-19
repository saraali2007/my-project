package com.zfod.fireinspect

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.util.IOUtils
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SyncManager {

    private val storage = FirebaseStorage.getInstance("gs://fire-inspection-b1c5a.firebasestorage.app")
    private val db = FirebaseFirestore.getInstance()

    /**
     * 1. مزامنة بيانات الصندوق بالكامل في Firestore
     */
    suspend fun saveCabinetToFirestore(cabinetMap: Map<String, Any>, cabinetId: String): Boolean {
        return try {
            db.collection("inspections").document(cabinetId).set(cabinetMap).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 2. جلب جميع بيانات الصناديق من Firestore
     */
    suspend fun fetchAllCabinets(): List<Map<String, Any>> {
        return try {
            val snapshot = db.collection("inspections").get().await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 3. توليد ملف الإكسل بالصور وتخزينه ورفعه للسحابة
     */
    suspend fun generateAndUploadExcel(context: Context, inspections: List<Map<String, Any>>): String? {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Inspection Report")
            val drawing = sheet.createDrawingPatriarch()

            // ترويسة الجدول
            val headerRow = sheet.createRow(0)
            val headers = listOf("رقم البوكس", "الموقع", "النوبة", "التاريخ والوقت", "الملاحظات", "الصورة 1", "الصورة 2", "الصورة 3")
            headers.forEachIndexed { i, title ->
                val cell = headerRow.createCell(i)
                cell.setCellValue(title)
                sheet.setColumnWidth(i, if (i >= 5) 28 * 256 else 18 * 256)
            }

            // كتابة الصفوف وإدراج الصور
            inspections.forEachIndexed { index, item ->
                val rowIndex = index + 1
                val row = sheet.createRow(rowIndex)
                row.heightInPoints = 85f // ارتفاع ملائم لمعاينة الصور

                row.createCell(0).setCellValue(item["number"]?.toString() ?: item["id"]?.toString() ?: "")
                row.createCell(1).setCellValue(item["location"]?.toString() ?: "")
                row.createCell(2).setCellValue(item["shift"]?.toString() ?: "")
                row.createCell(3).setCellValue(item["date"]?.toString() ?: item["updatedAt"]?.toString() ?: "")
                row.createCell(4).setCellValue(item["notes"]?.toString() ?: "")

                // استخراج أسماء الصور المحلية لإدراجها بالخلية
                val photoKeys = listOf("photo1", "photo2", "photo3")
                photoKeys.forEachIndexed { pIdx, key ->
                    val colIdx = 5 + pIdx
                    val photoName = item[key]?.toString()
                    if (!photoName.isNullOrEmpty()) {
                        val file = PhotoStore.thumbFile(context, photoName).takeIf { it.exists() }
                            ?: PhotoStore.fullFile(context, photoName).takeIf { it.exists() }

                        if (file != null && file.exists()) {
                            try {
                                val bytes = FileInputStream(file).use { IOUtils.toByteArray(it) }
                                val picIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG)
                                val anchor = XSSFClientAnchor().apply {
                                    setCol1(colIdx)
                                    this.row1 = rowIndex
                                    setCol2(colIdx + 1)
                                    this.row2 = rowIndex + 1
                                    dx1 = 10; dy1 = 10; dx2 = -10; dy2 = -10
                                }
                                drawing.createPicture(anchor, picIdx)
                            } catch (_: Exception) {
                                row.createCell(colIdx).setCellValue("خطأ بالصورة")
                            }
                        } else {
                            row.createCell(colIdx).setCellValue("لا يوجد ملف")
                        }
                    } else {
                        row.createCell(colIdx).setCellValue("—")
                    }
                }
            }

            // حفظ الملف مؤقتاً في كاش التطبيق
            val excelFile = File(context.cacheDir, "Fire_Inspection_Report.xlsx")
            FileOutputStream(excelFile).use { out ->
                workbook.write(out)
            }
            workbook.close()

            // رفع ملف الإكسل إلى Firebase Storage
            val reportRef = storage.reference.child("reports/Fire_Inspection_Report.xlsx")
            reportRef.putFile(Uri.fromFile(excelFile)).await()
            val downloadUrl = reportRef.downloadUrl.await().toString()

            // تسجيل رابط التقرير الأخير في Firestore
            val reportMeta = hashMapOf(
                "lastUpdated" to System.currentTimeMillis(),
                "downloadUrl" to downloadUrl
            )
            db.collection("reports").document("latest").set(reportMeta).await()

            downloadUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}