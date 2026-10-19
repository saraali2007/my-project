package com.zfod.fireinspect

import java.time.LocalDate

enum class Lang { AR, EN }

enum class Status(val code: String) {
    OK("OK"), NO("NO"), NA("NA");

    companion object {
        fun of(code: String?): Status = values().firstOrNull { it.code == code } ?: OK
    }
}

val SHIFTS: List<Char> = listOf('A', 'B', 'C', 'D')

const val EQ_COUNT = 8
const val PHOTO_SLOTS = 3

/** Default standard quantity per cabinet (same assumption as the Excel dashboard). */
val DEFAULT_STANDARD: List<Int> = listOf(2, 2, 2, 2, 2, 2, 1, 2)

data class Cabinet(
    val id: String,
    val shift: Char,
    val tagBox: String,
    val hydrantTag: String,
    val status: Status,
    val notes: String,
    val counts: List<Int?>,
    val epochDay: Long?,
    val timeMin: Int?,
    val remarks: String,
    val location: String,
    val photos: List<String>,
) {
    val hasPhotos: Boolean get() = photos.any { it.isNotEmpty() }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "shift" to shift.toString(),
        "tagBox" to tagBox,
        "hydrantTag" to hydrantTag,
        "status" to status.code,
        "notes" to notes,
        "counts" to counts,
        "epochDay" to epochDay,
        "timeMin" to timeMin,
        "remarks" to remarks,
        "location" to location,
        "photos" to photos,
    )

    companion object {
        fun fromMap(m: Map<*, *>): Cabinet {
            val rawCounts = (m["counts"] as? List<*>) ?: emptyList<Any?>()
            val counts = List(EQ_COUNT) { i -> (rawCounts.getOrNull(i) as? Number)?.toInt() }
            val rawPhotos = (m["photos"] as? List<*>) ?: emptyList<Any?>()
            val photos = List(PHOTO_SLOTS) { i -> (rawPhotos.getOrNull(i) as? String) ?: "" }
            val sh = (m["shift"] as? String)?.firstOrNull() ?: 'A'
            return Cabinet(
                id = (m["id"] as? String) ?: newId(),
                shift = if (sh in SHIFTS) sh else 'A',
                tagBox = (m["tagBox"] as? String) ?: "",
                hydrantTag = (m["hydrantTag"] as? String) ?: "",
                status = Status.of(m["status"] as? String),
                notes = (m["notes"] as? String) ?: "",
                counts = counts,
                epochDay = (m["epochDay"] as? Number)?.toLong(),
                timeMin = (m["timeMin"] as? Number)?.toInt(),
                remarks = (m["remarks"] as? String) ?: "",
                location = (m["location"] as? String) ?: "",
                photos = photos,
            )
        }

        fun newId(): String = "N-" + System.currentTimeMillis() + "-" + (Math.random() * 1000).toInt()

        fun blank(shift: Char, todayEpochDay: Long, nowMin: Int): Cabinet = Cabinet(
            id = newId(), shift = shift, tagBox = "", hydrantTag = "", status = Status.OK, notes = "",
            counts = DEFAULT_STANDARD, epochDay = todayEpochDay, timeMin = nowMin, remarks = "",
            location = "", photos = List(PHOTO_SLOTS) { "" },
        )
    }
}

object Fmt {
    fun date(epochDay: Long?): String = if (epochDay == null) "" else LocalDate.ofEpochDay(epochDay).toString()

    fun time(min: Int?): String =
        if (min == null) "" else String.format(java.util.Locale.US, "%02d:%02d", (min / 60) % 24, min % 60)

    fun pct(v: Double): String = String.format(java.util.Locale.US, "%.1f%%", v * 100.0)

    fun pct0(v: Double): String = String.format(java.util.Locale.US, "%.0f%%", v * 100.0)

    fun parseDate(s: String): Long? = try {
        LocalDate.parse(s.trim()).toEpochDay()
    } catch (e: Exception) {
        null
    }
}

/** Report palettes shared by the in-app preview, the HTML report and the Excel export. */
enum class ReportTheme(
    val key: String,
    val bg: String,
    val page: String,
    val card: String,
    val head: String,
    val line: String,
    val text: String,
    val muted: String,
    val accent: String,
    val dark: Boolean,
) {
    WHITE("white", "#dfe3ea", "#ffffff", "#f3f6fb", "#e6ecf5", "#c9d2e0", "#111827", "#5b6677", "#1d4ed8", false),
    LIGHT_BLUE("lightblue", "#cfe3f6", "#eaf4ff", "#ffffff", "#cfe6fb", "#a9cdee", "#0b2545", "#47627f", "#0369a1", false),
    PINK("pink", "#f3d3e0", "#fff0f6", "#ffffff", "#fbd5e5", "#f0b3cc", "#4a1230", "#8a4a68", "#be185d", false),
    DARK_SOFT("darksoft", "#252b36", "#2f3745", "#394355", "#445066", "#56627a", "#eef2f8", "#b4bfd0", "#60a5fa", true),
    DARK_DEEP("darkdeep", "#05080f", "#0b1220", "#101a2e", "#16233d", "#24365a", "#e8eefc", "#8fa3c7", "#38bdf8", true);

    companion object {
        fun of(key: String?): ReportTheme = values().firstOrNull { it.key == key } ?: DARK_DEEP
    }
}

/** Eight selectable text colours for the report. Index 0 = automatic (theme default). */
val REPORT_TEXT_COLORS: List<String?> = listOf(
    null, "#111111", "#FFFFFF", "#2196F3", "#43A047", "#F9A825", "#E53935", "#8E24AA",
)

/** Six accent colours for the app itself (ARGB longs as 0xFFRRGGBB). */
val APP_ACCENTS: List<Long> = listOf(
    0xFF1E88E5, 0xFF00ACC1, 0xFF43A047, 0xFFF9A825, 0xFFE53935, 0xFF8E24AA,
)

val SHIFT_COLORS: Map<Char, String> = mapOf(
    'A' to "#3B82F6", 'B' to "#22C55E", 'C' to "#F59E0B", 'D' to "#A855F7",
)

data class ReportOptions(
    val lang: Lang,
    val shifts: List<Char>,
    val overview: Boolean,
    val theme: ReportTheme,
    val textColorIndex: Int,
    val photos: Boolean,
    val printedDate: String,
    val printedTime: String,
) {
    val textColor: String get() = REPORT_TEXT_COLORS.getOrNull(textColorIndex) ?: theme.text
}

data class UserAccount(
    val username: String,
    val password: String,
    val role: String, // "Admin", "Shift A", "Shift B", "Shift C", "Shift D"
    val isBlocked: Boolean = false,
) {
    val blocked: Boolean get() = isBlocked

    fun toMap(): Map<String, Any?> = mapOf(
        "username" to username,
        "password" to password,
        "role" to role,
        "isBlocked" to isBlocked,
        "blocked" to isBlocked,
    )

    companion object {
        fun fromMap(m: Map<*, *>): UserAccount {
            val b = (m["isBlocked"] as? Boolean) ?: (m["blocked"] as? Boolean) ?: false
            return UserAccount(
                username = (m["username"] as? String) ?: "",
                password = (m["password"] as? String) ?: "",
                role = (m["role"] as? String) ?: "Admin",
                isBlocked = b,
            )
        }
    }
}

val USER_ROLES = listOf("Admin", "Shift A", "Shift B", "Shift C", "Shift D")
