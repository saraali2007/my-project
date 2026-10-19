package com.zfod.fireinspect

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object PoiExcelExporter {
    private const val STORAGE_BUCKET = "gs://fire-inspection-b1c5a.firebasestorage.app"

    fun buildXlsxBytes(ctx: Context, cabinets: List<Cabinet>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Fire Inspection Report")
        val drawing = sheet.createDrawingPatriarch()

        val headerRow = sheet.createRow(0)
        val headers = listOf("Box Number", "Location", "Shift", "Date/Time", "Notes", "Photo 1", "Photo 2", "Photo 3")
        headers.forEachIndexed { idx, title ->
            headerRow.createCell(idx).setCellValue(title)
        }

        var rowIndex = 1
        for (c in cabinets) {
            val row = sheet.createRow(rowIndex)
            row.heightInPoints = 90f // Row height enlarged to 90pt

            row.createCell(0).setCellValue(c.tagBox)
            row.createCell(1).setCellValue(c.location)
            row.createCell(2).setCellValue(c.shift.toString())
            row.createCell(3).setCellValue("${Fmt.date(c.epochDay)} ${Fmt.time(c.timeMin)}")
            row.createCell(4).setCellValue(c.notes.ifBlank { c.remarks })

            for (i in 0 until 3) {
                val photoName = c.photos.getOrNull(i) ?: ""
                row.createCell(5 + i)
                if (photoName.isNotEmpty()) {
                    val imgFile = PhotoStore.fullFile(ctx, photoName)
                    if (imgFile.exists()) {
                        try {
                            val bytes = imgFile.readBytes()
                            val pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG)
                            val c1 = 5 + i
                            val r1 = rowIndex
                            val c2 = 5 + i + 1
                            val r2 = rowIndex + 1
                            val xAnchor = XSSFClientAnchor(0, 0, 0, 0, c1, r1, c2, r2)
                            xAnchor.setAnchorType(org.apache.poi.ss.usermodel.ClientAnchor.AnchorType.MOVE_AND_RESIZE)
                            drawing.createPicture(xAnchor, pictureIdx)
                        } catch (_: Exception) {}
                    }
                }
            }
            rowIndex++
        }

        val bos = ByteArrayOutputStream()
        workbook.write(bos)
        workbook.close()
        val bytes = bos.toByteArray()

        try {
            val file = File(ctx.cacheDir, "Fire_Inspection_Report.xlsx")
            file.writeBytes(bytes)
            val storageRef = FirebaseStorage.getInstance(STORAGE_BUCKET).reference.child("reports/Fire_Inspection_Report.xlsx")
            storageRef.putFile(Uri.fromFile(file)).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    FirebaseFirestore.getInstance()
                        .collection("reports")
                        .document("latest")
                        .set(mapOf("url" to uri.toString(), "timestamp" to System.currentTimeMillis()))
                }
            }
        } catch (_: Exception) {}

        return bytes
    }
}
