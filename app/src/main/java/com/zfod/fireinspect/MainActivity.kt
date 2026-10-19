package com.zfod.fireinspect

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

sealed interface Screen {
    object Dash : Screen
    data class ShiftS(val shift: Char) : Screen
    data class Report(val shift: Char?) : Screen
    object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private val state by lazy { AppState(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(state) { AppRoot(state) }
        }
    }
}

@Composable
fun AppRoot(state: AppState) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Dash) }
    var editing by remember { mutableStateOf<Cabinet?>(null) }
    var editingNew by remember { mutableStateOf(false) }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val ic = WindowCompat.getInsetsController(window, view)
            ic.isAppearanceLightStatusBars = !c.dark
            ic.isAppearanceLightNavigationBars = !c.dark
        }
    }

    BackHandler(enabled = screen !is Screen.Dash && editing == null) { screen = Screen.Dash }

    val base = LocalDensity.current
    val factor = if (screen is Screen.Settings) 1f else state.scale / 100f

    // تصحيح حساب الكثافة وضبط الأبعاد
    CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale)) {
        Surface(color = c.bg, contentColor = c.text, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val s = screen) {
                    is Screen.Dash -> Column(Modifier.fillMaxSize()) {
                        AppHeader()
                        Toolbar(t("dashboard"), null) {
                            ToolbarActions(state, { screen = Screen.Settings })
                        }
                        Box(Modifier.weight(1f)) {
                            DashboardScreen(state, { sh -> screen = Screen.ShiftS(sh) }, { screen = Screen.Report(null) })
                        }
                    }
                    is Screen.ShiftS -> Column(Modifier.fillMaxSize()) {
                        AppHeader()
                        Toolbar(tf("shift_x", s.shift), { screen = Screen.Dash }) {
                            IconBtn(IconKind.PRINT, { screen = Screen.Report(s.shift) }, c.accent)
                            ToolbarActions(state, { screen = Screen.Settings })
                        }
                        Box(Modifier.weight(1f)) {
                            ShiftScreen(
                                state = state,
                                shift = s.shift,
                                onShift = { sh -> screen = Screen.ShiftS(sh) },
                                onEdit = { cab ->
                                    if (state.canEdit(cab.shift)) {
                                        editingNew = false
                                        editing = cab
                                    } else {
                                        toast(ctx, Tr.f(state.lang, "perm_denied", cab.shift))
                                    }
                                },
                                onAdd = {
                                    if (state.canEdit(s.shift)) {
                                        val now = LocalTime.now()
                                        // تصحيح حساب الدقيقة والوقت
                                        val blank = Cabinet.blank(s.shift, AppState.today(), now.hour * 60 + now.minute)
                                        editingNew = true
                                        editing = blank.copy(counts = state.standard)
                                    } else {
                                        toast(ctx, Tr.f(state.lang, "perm_denied", s.shift))
                                    }
                                },
                            )
                        }
                    }
                    is Screen.Report -> ReportScreen(state, s.shift) { screen = Screen.Dash }
                    is Screen.Settings -> SettingsScreen(state) { screen = Screen.Dash }
                }
                val e = editing
                if (e != null) {
                    key(e.id) {
                        EditScreen(state, e, editingNew) { editing = null }
                    }
                }
            }
        }
    }

    if (state.showLoginDialog && state.currentUser == null) {
        LoginDialog(state)
    }

    if (state.showAdminPanel) {
        AdminPanelDialog(state)
    }
}

@Composable
fun ToolbarActions(state: AppState, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val msgSyncing = t("syncing")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // زر المزامنة السحابية الحقيقي المربوط بسحابة Firebase
        IconBtn(IconKind.SYNC, {
            toast(ctx, msgSyncing)
            scope.launch(Dispatchers.IO) {
                try {
                    // مزامنة الصناديق ورفع ملف التقرير إلى Firebase
                    val allCabinets = state.cabinets.map { cab ->
                        mapOf(
                            "id" to cab.id,
                            "number" to cab.tagBox,
                            "location" to cab.location,
                            "shift" to cab.shift.toString(),
                            "notes" to cab.notes,
                            "photo1" to (cab.photos.getOrNull(0) ?: ""),
                            "photo2" to (cab.photos.getOrNull(1) ?: ""),
                            "photo3" to (cab.photos.getOrNull(2) ?: ""),
                            "updatedAt" to (cab.timeMin ?: 0)
                        )
                    }

                    // رفع الصور المعلقة
                    allCabinets.forEach { item ->
                        listOfNotNull(item["photo1"], item["photo2"], item["photo3"]).forEach { pName ->
                            val pStr = pName.toString()
                            if (pStr.isNotBlank()) {
                                PhotoStore.uploadToStorage(ctx, pStr)
                            }
                        }
                        SyncManager.saveCabinetToFirestore(item, item["id"]?.toString() ?: "cab_${System.currentTimeMillis()}")
                    }

                    // توليد ورفع تقرير الإكسل بالصور
                    val excelUrl = SyncManager.generateAndUploadExcel(ctx, allCabinets)

                    withContext(Dispatchers.Main) {
                        if (excelUrl != null) {
                            toast(ctx, "تمت المزامنة بنجاح وحفظ ملف الإكسل سحابياً")
                        } else {
                            toast(ctx, "تمت مزامنة البيانات")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        toast(ctx, "خطأ في المزامنة: ${e.message}")
                    }
                }
            }
        }, c.accent)

        if (state.currentUser?.role == "Admin") {
            IconBtn(IconKind.SHIELD, { state.showAdminPanel = true }, c.accent)
        }

        IconBtn(IconKind.GLOBE, { state.applyLang(if (state.lang == Lang.AR) Lang.EN else Lang.AR) }, c.accent)
        IconBtn(IconKind.GEAR, onSettings)
        IconBtn(IconKind.USER, { state.logout() }, ColBad)
    }
}

@Composable
fun LoginDialog(state: AppState) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = c.surface,
        title = { Txt(t("login"), size = 18, bold = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                errorMsg?.let {
                    Txt(it, size = 13, color = ColBad)
                }
                Field(username, { username = it }, t("username"), Modifier.fillMaxWidth())
                Field(password, { password = it }, t("password"), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    isLoading = true
                    errorMsg = null
                    scope.launch {
                        val (success, msg) = UserManager.login(username, password)
                        isLoading = false
                        if (success) {
                            state.login(username, password)
                            toast(ctx, msg)
                        } else {
                            errorMsg = msg
                        }
                    }
                }
            ) {
                Txt(if (isLoading) "..." else t("login_btn"), size = 14, bold = true, color = c.accent)
            }
        },
        dismissButton = {},
    )
}

@Composable
fun AdminPanelDialog(state: AppState) {
    val c = LocalColors.current
    var editUserTarget by remember { mutableStateOf<UserAccount?>(null) }

    AlertDialog(
        onDismissRequest = { state.showAdminPanel = false },
        containerColor = c.surface,
        title = { Txt(t("admin_panel"), size = 18, bold = true) },
        text = {
            Column(
                Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(t("add_user"), { editUserTarget = UserAccount("", "", "Shift A", false) }, Modifier.fillMaxWidth(), icon = IconKind.ADD)
                Spacer(Modifier.height(4.dp))
                state.users.forEach { u ->
                    Panel {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Txt(u.username, size = 15, bold = true)
                                Txt("${u.role} · ${if (u.blocked) t("blocked") else t("active")}", size = 12, color = if (u.blocked) ColBad else c.muted)
                            }
                            if (!u.username.equals("admin", true)) {
                                IconBtn(IconKind.EDIT, { editUserTarget = u }, c.accent, 32)
                                Spacer(Modifier.width(4.dp))
                                IconBtn(IconKind.DELETE, { state.deleteUser(u.username) }, ColBad, 32)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.showAdminPanel = false }) {
                Txt(t("close"), size = 14, bold = true, color = c.accent)
            }
        },
        dismissButton = {},
    )

    val target = editUserTarget
    if (target != null) {
        UserEditDialog(state, target) { editUserTarget = null }
    }
}

@Composable
fun UserEditDialog(state: AppState, user: UserAccount, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var uname by remember { mutableStateOf(user.username) }
    var pword by remember { mutableStateOf(user.password) }
    var role by remember { mutableStateOf(user.role) }
    var blocked by remember { mutableStateOf(user.blocked) }
    val isNew = user.username.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Txt(if (isNew) t("add_user") else tf("edit_cabinet", 0), size = 18, bold = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isNew) {
                    Field(uname, { uname = it }, t("username"), Modifier.fillMaxWidth())
                }
                Field(pword, { pword = it }, t("password"), Modifier.fillMaxWidth())
                Txt(t("role"), size = 12, bold = true, color = c.muted)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    USER_ROLES.forEach { r ->
                        ChipBtn(r, r == role, c.accent, { role = r })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(t("blocked"), size = 14, modifier = Modifier.weight(1f))
                    Switch(checked = blocked, onCheckedChange = { blocked = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (uname.isNotBlank() && pword.isNotBlank()) {
                    val newUser = UserAccount(uname.trim(), pword.trim(), role, blocked)
                    state.upsertUser(newUser)
                    // حفظ المستخدم في سحابة Firestore مباشرة
                    scope.launch(Dispatchers.IO) {
                        UserManager.saveUser(AppUser(newUser.username, newUser.password, newUser.role, newUser.blocked))
                    }
                    onDismiss()
                }
            }) {
                Txt(t("save"), size = 14, bold = true, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Txt(t("cancel"), size = 14, bold = true, color = c.muted)
            }
        },
    )
}