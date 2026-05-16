package com.aidflow.pro.intake

import java.util.UUID

/**
 * A single household record captured in the field.
 *
 * Field names mirror the AidFlow Pro web app's canonical `Family` schema (the
 * 12 importable fields documented in `src/services/spreadsheetImport.ts` of the
 * web repo) so XLSX/CSV exports from the mobile app import into the web app
 * with no manual column-mapping step.
 */
data class FamilyRecord(
    val id: String = UUID.randomUUID().toString(),
    val headName: String = "",
    val memberCount: Int = 0,
    val childrenUnder5: Int = 0,
    val elderlyCount: Int = 0,
    val hasPregnantMember: Boolean = false,
    val medicalConditions: String = "",          // comma-separated, matches web
    val displacementStatus: DisplacementStatus = DisplacementStatus.Unknown,
    val incomeLevel: IncomeLevel = IncomeLevel.Unknown,
    val locationSector: String = "",
    val street: String = "",
    val city: String = "",
    val notes: String = "",
    val priorityScore: Int? = null,              // 0–100, computed locally
    val priorityReason: String = "",
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    val priorityLevel: PriorityLevel get() = PriorityLevel.fromScore(priorityScore)
}

enum class DisplacementStatus(val webValue: String, val display: String) {
    Resident("resident", "Resident"),
    RecentlyDisplaced("recently_displaced", "Recently displaced"),
    Refugee("refugee", "Refugee"),
    Unknown("", "Unknown");

    companion object {
        fun fromAny(raw: String?): DisplacementStatus {
            if (raw.isNullOrBlank()) return Unknown
            val normalised = raw.trim().lowercase().replace(' ', '_')
            return values().firstOrNull { it.webValue == normalised }
                ?: when {
                    "refug" in normalised -> Refugee
                    "displac" in normalised -> RecentlyDisplaced
                    "resid" in normalised || "local" in normalised || "host" in normalised -> Resident
                    else -> Unknown
                }
        }
    }
}

enum class IncomeLevel(val webValue: String, val display: String) {
    None("none", "None"),
    Minimal("minimal", "Minimal"),
    Moderate("moderate", "Moderate"),
    Unknown("", "Unknown");

    companion object {
        fun fromAny(raw: String?): IncomeLevel {
            if (raw.isNullOrBlank()) return Unknown
            val n = raw.trim().lowercase()
            return values().firstOrNull { it.webValue == n }
                ?: when {
                    "none" in n || "no income" in n || "zero" in n -> None
                    "minimal" in n || "low" in n || "poor" in n -> Minimal
                    "moderate" in n || "medium" in n || "stable" in n -> Moderate
                    else -> Unknown
                }
        }
    }
}

/** Computed from the priority score using the web app's PriorityLevel thresholds. */
enum class PriorityLevel(val display: String) {
    Critical("CRITICAL"),
    High("HIGH"),
    Medium("MEDIUM"),
    Normal("NORMAL"),
    Unknown("—");

    companion object {
        fun fromScore(score: Int?): PriorityLevel = when {
            score == null -> Unknown
            score >= 80 -> Critical
            score >= 60 -> High
            score >= 40 -> Medium
            else -> Normal
        }
    }
}

/** An item identified by Gemma vision in a supply photo. */
data class IdentifiedItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int? = null,
    val unit: String = "",
    val category: ItemCategory = ItemCategory.Other,
    val notes: String = "",
)

enum class ItemCategory(val webValue: String, val display: String) {
    Food("food", "Food"),
    Water("water", "Water"),
    Medical("medical", "Medical"),
    Shelter("shelter", "Shelter"),
    Hygiene("hygiene", "Hygiene"),
    Clothing("clothing", "Clothing"),
    Education("education", "Education"),
    Other("other", "Other");

    companion object {
        fun fromAny(raw: String?): ItemCategory {
            if (raw.isNullOrBlank()) return Other
            val n = raw.trim().lowercase()
            return values().firstOrNull { it.webValue == n }
                ?: when {
                    "food" in n || "ration" in n || "meal" in n -> Food
                    "water" in n -> Water
                    "med" in n || "drug" in n || "pharm" in n || "first aid" in n -> Medical
                    "shelter" in n || "tent" in n || "blanket" in n -> Shelter
                    "hygi" in n || "soap" in n || "sanit" in n -> Hygiene
                    "cloth" in n || "shoe" in n -> Clothing
                    "school" in n || "book" in n || "educat" in n -> Education
                    else -> Other
                }
        }
    }
}
