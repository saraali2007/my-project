package com.zfod.fireinspect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Holds all editable data and settings; every change is persisted automatically. */
class AppState(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dataFile = File(ctx.filesDir, "data.json")
    private val io = Executors.newSingleThreadExecutor()

    var cabinets by mutableStateOf<List<Cabinet>>(emptyList())
        private set

    var lang by mutableStateOf(if (prefs.getString("lang", "ar") == "en") Lang.EN else Lang.AR)
        private set
    /** 0 = dark, 1 = white, 2 = eye care */
    var appMode by mutableStateOf(prefs.getInt("appMode", 0).coerceIn(0, 2))
        private set
    var accent by mutableStateOf(prefs.getInt("accent", 0).coerceIn(0, APP_ACCENTS.size - 1))
        private set
    var scale by mutableStateOf(prefs.getInt("scale", 100).coerceIn(10, 300))
        private set
    var repTheme by mutableStateOf(ReportTheme.of(prefs.getString("repTheme", ReportTheme.DARK_DEEP.key)))
        private set
    var repTextColor by mutableStateOf(prefs.getInt("repTextColor", 0).coerceIn(0, REPORT_TEXT_COLORS.size - 1))
        private set
    var repPhotos by mutableStateOf(prefs.getBoolean("repPhotos", true))
        private set
    var standard by mutableStateOf(loadStandard())
        private set

    // ---- authentication & roles -----------------------------------------------------------------
    var currentUser by mutableStateOf<UserAccount?>(null)
    var showLoginDialog by mutableStateOf(true)
    var showAdminPanel by mutableStateOf(false)
    var users by mutableStateOf<List<UserAccount>>(listOf(UserAccount("admin", "admin123", "Admin", false)))

    init {
        cabinets = loadCabinets()
        loadUsers()
    }

    fun canEdit(shift: Char): Boolean {
        val r = currentUser?.role ?: return false
        if (r == "Admin") return true
        return r == "Shift $shift"
    }

    fun login(u: String, p: String): Boolean {
        val found = users.firstOrNull { it.username.equals(u.trim(), true) && it.password == p }
        if (found != null && !found.isBlocked) {
            currentUser = found
            showLoginDialog = false
            return true
        }
        return false
    }

    fun logout() {
        currentUser = null
        showLoginDialog = true
    }

    private fun loadUsers() {
        val defaultAdmin = UserAccount("admin", "admin123", "Admin", false)
        users = listOf(defaultAdmin)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection("users").get().await()
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { UserAccount.fromMap(it) }
                }
                if (list.isNotEmpty()) {
                    val hasAdmin = list.any { it.username.equals("admin", true) && it.role == "Admin" }
                    val finalUsers = if (hasAdmin) list else (listOf(defaultAdmin) + list)
                    withContext(Dispatchers.Main) {
                        users = finalUsers
                    }
                } else {
                    db.collection("users").document("admin").set(defaultAdmin.toMap()).await()
                }
            } catch (_: Exception) {}
        }
    }

    fun upsertUser(user: UserAccount) {
        val list = users.toMutableList()
        val idx = list.indexOfFirst { it.username.equals(user.username, true) }
        if (idx >= 0) list[idx] = user else list.add(user)
        users = list
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance().collection("users").document(user.username).set(user.toMap()).await()
            } catch (_: Exception) {}
        }
    }

    fun deleteUser(username: String) {
        if (username.equals("admin", true)) return
        users = users.filter { !it.username.equals(username, true) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance().collection("users").document(username).delete().await()
            } catch (_: Exception) {}
        }
    }

    // ---- cloud sync & explicit storage bucket ----------------------------------------------------
    private val storageBucket = "gs://fire-inspection-b1c5a.firebasestorage.app"

    fun upsert(c: Cabinet) {
        val i = cabinets.indexOfFirst { it.id == c.id }
        val l = cabinets.toMutableList()
        if (i >= 0) {
            val old = l[i]
            l[i] = c
            if (old.shift != c.shift) {
                l.removeAt(i)
                l.add(c)
            }
        } else {
            l.add(c)
        }
        cabinets = l
        persist()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val storage = FirebaseStorage.getInstance(storageBucket)
                val updatedPhotos = c.photos.toMutableList()

                for (idx in updatedPhotos.indices) {
                    val p = updatedPhotos[idx]
                    if (p.isNotEmpty() && !p.startsWith("http")) {
                        val file = PhotoStore.fullFile(ctx, p)
                        if (file.exists()) {
                            val ref = storage.reference.child("photos/$p")
                            ref.putFile(Uri.fromFile(file)).await()
                            val url = ref.downloadUrl.await().toString()
                            updatedPhotos[idx] = url
                        }
                    }
                }

                val finalCabinet = c.copy(photos = updatedPhotos)
                db.collection("inspections").document(c.id).set(finalCabinet.toMap()).await()
            } catch (_: Exception) {}
        }
    }

    fun delete(id: String) {
        val c = cabinets.firstOrNull { it.id == id } ?: return
        cabinets = cabinets.filter { it.id != id }
        c.photos.filter { it.isNotEmpty() }.forEach { PhotoStore.delete(ctx, it) }
        persist()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance().collection("inspections").document(id).delete().await()
            } catch (_: Exception) {}
        }
    }

    fun syncWithCloud(context: Context, onResult: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val storage = FirebaseStorage.getInstance(storageBucket)

                for (c in cabinets) {
                    val updatedPhotos = c.photos.toMutableList()
                    for (idx in updatedPhotos.indices) {
                        val p = updatedPhotos[idx]
                        if (p.isNotEmpty() && !p.startsWith("http")) {
                            val file = PhotoStore.fullFile(context, p)
                            if (file.exists()) {
                                val ref = storage.reference.child("photos/$p")
                                ref.putFile(Uri.fromFile(file)).await()
                                updatedPhotos[idx] = ref.downloadUrl.await().toString()
                            }
                        }
                    }
                    db.collection("inspections").document(c.id).set(c.copy(photos = updatedPhotos).toMap()).await()
                }

                val snapshot = db.collection("inspections").get().await()
                val remoteCabinets = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { Cabinet.fromMap(it) }
                }

                val map = cabinets.associateBy { it.id }.toMutableMap()
                for (rc in remoteCabinets) {
                    val existing = map[rc.id]
                    if (existing == null || (rc.epochDay ?: 0L) >= (existing.epochDay ?: 0L)) {
                        map[rc.id] = rc
                    }
                }
                val mergedCabinets = map.values.toList()

                for (c in mergedCabinets) {
                    for (p in c.photos) {
                        if (p.isNotEmpty()) {
                            val localName = if (p.contains("photos%2F")) p.substringAfter("photos%2F").substringBefore("?") else p.substringAfterLast("/")
                            val localFull = PhotoStore.fullFile(context, localName)
                            val localThumb = PhotoStore.thumbFile(context, localName)
                            if (!localFull.exists() && p.startsWith("http")) {
                                try {
                                    val ref = storage.getReferenceFromUrl(p)
                                    ref.getFile(localFull).await()
                                    val bmp = BitmapFactory.decodeFile(localFull.absolutePath)
                                    if (bmp != null) {
                                        val thumbBmp = fitBmp(bmp, 480)
                                        thumbBmp.compress(Bitmap.CompressFormat.JPEG, 75, localThumb.outputStream())
                                        if (thumbBmp !== bmp) thumbBmp.recycle()
                                        bmp.recycle()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    cabinets = mergedCabinets
                    persist()
                    onResult(true, Tr.get(lang, "sync_success"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private fun fitBmp(b: Bitmap, max: Int): Bitmap {
        val s = max.toFloat() / maxOf(b.width, b.height)
        if (s >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    // ---- settings & data persistence -------------------------------------------------------------
    fun applyLang(v: Lang) { lang = v; prefs.edit().putString("lang", if (v == Lang.EN) "en" else "ar").apply() }
    fun applyAppMode(v: Int) { appMode = v; prefs.edit().putInt("appMode", v).apply() }
    fun applyAccent(v: Int) { accent = v; prefs.edit().putInt("accent", v).apply() }
    fun applyScale(v: Int) { scale = v.coerceIn(10, 300); prefs.edit().putInt("scale", scale).apply() }
    fun applyRepTheme(v: ReportTheme) { repTheme = v; prefs.edit().putString("repTheme", v.key).apply() }
    fun applyRepTextColor(v: Int) { repTextColor = v; prefs.edit().putInt("repTextColor", v).apply() }
    fun applyRepPhotos(v: Boolean) { repPhotos = v; prefs.edit().putBoolean("repPhotos", v).apply() }

    fun setStandard(k: Int, v: Int) {
        val l = standard.toMutableList()
        l[k] = v.coerceIn(0, 99)
        standard = l
        prefs.edit().putString("standard", l.joinToString(",")).apply()
    }

    private fun loadStandard(): List<Int> {
        val s = prefs.getString("standard", null) ?: return DEFAULT_STANDARD
        val parts = s.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == EQ_COUNT) parts else DEFAULT_STANDARD
    }

    fun cabinetsOf(shift: Char): List<Cabinet> = cabinets.filter { it.shift == shift }

    fun restoreOriginal() {
        cabinets.flatMap { it.photos }.filter { it.isNotEmpty() }.forEach { PhotoStore.delete(ctx, it) }
        cabinets = loadSeed()
        persist()
    }

    private fun persist() {
        val snapshot = Json.write(cabinets.map { it.toMap() })
        io.execute {
            try {
                val tmp = File(ctx.filesDir, "data.json.tmp")
                tmp.writeText(snapshot, Charsets.UTF_8)
                if (!tmp.renameTo(dataFile)) {
                    dataFile.writeText(snapshot, Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadCabinets(): List<Cabinet> {
        if (dataFile.exists()) {
            try {
                val list = parse(dataFile.readText(Charsets.UTF_8))
                if (list.isNotEmpty() || dataFile.length() > 2) return list
            } catch (_: Exception) {
                try { dataFile.copyTo(File(ctx.filesDir, "data.corrupt.json"), overwrite = true) } catch (_: Exception) {}
            }
        }
        val seed = loadSeed()
        cabinets = seed
        persist()
        return seed
    }

    private fun loadSeed(): List<Cabinet> {
        val text = ctx.assets.open("seed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text)
    }

    private fun parse(text: String): List<Cabinet> {
        val raw = Json.parse(text) as? List<*> ?: return emptyList()
        return raw.mapNotNull { (it as? Map<*, *>)?.let { m -> Cabinet.fromMap(m) } }
    }

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}
