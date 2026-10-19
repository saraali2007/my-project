package com.zfod.fireinspect

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class AppUser(
    val username: String = "",
    val password: String = "",
    val role: String = "Shift A", // خيارات الأدوار: Admin, Shift A, Shift B, Shift C, Shift D
    val isBlocked: Boolean = false
)

object UserManager {
    private val db = FirebaseFirestore.getInstance()
    var currentUser: AppUser? = null

    /**
     * 1. التحقق من تسجيل الدخول
     */
    suspend fun login(username: String, password: String): Pair<Boolean, String> {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        // حساب الأدمن الرئيسي الافتراضي
        if (trimmedUser == "admin" && trimmedPass == "admin123") {
            currentUser = AppUser(username = "admin", password = "admin123", role = "Admin")
            return Pair(true, "تم الدخول كمسؤول")
        }

        return try {
            val doc = db.collection("users").document(trimmedUser).get().await()
            if (doc.exists()) {
                val user = doc.toObject(AppUser::class.java)
                if (user != null) {
                    if (user.isBlocked) {
                        Pair(false, "هذا الحساب محظور من قبل الأدمن")
                    } else if (user.password == trimmedPass) {
                        currentUser = user
                        Pair(true, "تم تسجيل الدخول بنجاح")
                    } else {
                        Pair(false, "كلمة المرور غير صحيحة")
                    }
                } else {
                    Pair(false, "بيانات الحساب غير صالحة")
                }
            } else {
                Pair(false, "اسم المستخدم غير موجود")
            }
        } catch (e: Exception) {
            Pair(false, "خطأ في الاتصال بقاعدة البيانات: ${e.message}")
        }
    }

    /**
     * 2. إنشاء أو تعديل حساب مستخدم وحفظه في Firestore
     */
    suspend fun saveUser(user: AppUser): Boolean {
        return try {
            db.collection("users").document(user.username.trim()).set(user).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 3. جلب جميع الحسابات المسجلة في السحابة لعرضها للأدمن
     */
    suspend fun getAllUsers(): List<AppUser> {
        return try {
            val snapshot = db.collection("users").get().await()
            snapshot.toObjects(AppUser::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 4. فحص الصلاحية: هل يحق للمستخدم الحالي تعديل بيانات صندوق معين؟
     */
    fun canEditShift(cabinetShift: String): Boolean {
        val role = currentUser?.role ?: return false
        if (role == "Admin") return true
        return role.equals(cabinetShift, ignoreCase = true)
    }
}