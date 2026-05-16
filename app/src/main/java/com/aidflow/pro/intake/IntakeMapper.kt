package com.aidflow.pro.intake

import com.aidflow.pro.intake.JsonExtractor.optBoolean2
import com.aidflow.pro.intake.JsonExtractor.optInteger
import com.aidflow.pro.intake.JsonExtractor.optStr
import org.json.JSONArray
import org.json.JSONObject

/**
 * Translates raw LLM JSON output into the typed domain objects. The shape we
 * ask Gemma for matches the canonical AidFlow Pro web schema, so this mapping
 * is mostly direct with light defensive normalisation.
 */
object IntakeMapper {

    fun familyFromJson(json: JSONObject): FamilyRecord {
        val priority = json.optInteger("priority_score")
            ?: json.optInteger("priorityScore")
        return FamilyRecord(
            headName = json.optStr("head_name").ifBlank { json.optStr("headName") }.trim(),
            memberCount = (json.optInteger("member_count") ?: json.optInteger("memberCount") ?: 0).coerceAtLeast(0),
            childrenUnder5 = (json.optInteger("children_under_5") ?: json.optInteger("childrenUnder5") ?: 0).coerceAtLeast(0),
            elderlyCount = (json.optInteger("elderly_count") ?: json.optInteger("elderlyCount") ?: 0).coerceAtLeast(0),
            hasPregnantMember = json.optBoolean2("has_pregnant_member", false) ||
                json.optBoolean2("hasPregnantMember", false),
            medicalConditions = normaliseConditions(json.optStr("medical_conditions").ifBlank { json.optStr("medicalConditions") }),
            displacementStatus = DisplacementStatus.fromAny(
                json.optStr("displacement_status").ifBlank { json.optStr("displacementStatus") }
            ),
            incomeLevel = IncomeLevel.fromAny(
                json.optStr("income_level").ifBlank { json.optStr("incomeLevel") }
            ),
            locationSector = json.optStr("location_sector").ifBlank { json.optStr("locationSector") }.trim(),
            street = json.optStr("street").trim(),
            city = json.optStr("city").trim(),
            notes = json.optStr("notes").trim(),
            priorityScore = priority?.coerceIn(0, 100),
            priorityReason = json.optStr("priority_reason").ifBlank { json.optStr("priorityReason") }.trim(),
        )
    }

    fun itemsFromJson(json: JSONArray): List<IdentifiedItem> = buildList {
        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue
            val name = obj.optStr("name").ifBlank { obj.optStr("item") }.trim()
            if (name.isBlank()) continue
            add(
                IdentifiedItem(
                    name = name,
                    quantity = obj.optInteger("quantity"),
                    unit = obj.optStr("unit").trim(),
                    category = ItemCategory.fromAny(obj.optStr("category")),
                    notes = obj.optStr("notes").trim(),
                )
            )
        }
    }

    /**
     * Accepts either a comma-separated string or a JSON array of strings and
     * normalises to the comma-separated form the web app stores.
     */
    private fun normaliseConditions(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("[")) {
            runCatching { JSONArray(trimmed) }.getOrNull()?.let { arr ->
                return (0 until arr.length())
                    .map { arr.optString(it).trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
            }
        }
        return trimmed
    }
}
