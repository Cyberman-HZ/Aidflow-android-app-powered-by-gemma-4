package com.aidflow.pro.intake

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeMapperTest {

    @Test
    fun `family record maps the 12 canonical fields exactly`() {
        val json = JSONObject("""
            {
              "head_name": "Ahmed Mahmoud",
              "member_count": 6,
              "children_under_5": 1,
              "elderly_count": 1,
              "has_pregnant_member": true,
              "medical_conditions": "asthma, diabetes",
              "displacement_status": "recently_displaced",
              "income_level": "minimal",
              "location_sector": "Sector 4",
              "street": "Block C, tent 17",
              "city": "Aleppo",
              "notes": "Lost ID papers in transit",
              "priority_score": 78,
              "priority_reason": "vulnerable composition + recent displacement"
            }
        """.trimIndent())

        val r = IntakeMapper.familyFromJson(json)
        assertEquals("Ahmed Mahmoud", r.headName)
        assertEquals(6, r.memberCount)
        assertEquals(1, r.childrenUnder5)
        assertEquals(1, r.elderlyCount)
        assertTrue(r.hasPregnantMember)
        assertEquals("asthma, diabetes", r.medicalConditions)
        assertEquals(DisplacementStatus.RecentlyDisplaced, r.displacementStatus)
        assertEquals(IncomeLevel.Minimal, r.incomeLevel)
        assertEquals("Sector 4", r.locationSector)
        assertEquals("Block C, tent 17", r.street)
        assertEquals("Aleppo", r.city)
        assertEquals("Lost ID papers in transit", r.notes)
        assertEquals(78, r.priorityScore)
        assertEquals(PriorityLevel.High, r.priorityLevel)
    }

    @Test
    fun `missing fields fall back to safe defaults instead of throwing`() {
        val json = JSONObject("""{"head_name": "Solo"}""")
        val r = IntakeMapper.familyFromJson(json)
        assertEquals("Solo", r.headName)
        assertEquals(0, r.memberCount)
        assertFalse(r.hasPregnantMember)
        assertEquals(DisplacementStatus.Unknown, r.displacementStatus)
        assertEquals(IncomeLevel.Unknown, r.incomeLevel)
        assertEquals(null, r.priorityScore)
    }

    @Test
    fun `medical conditions array is flattened to a comma-separated string`() {
        val json = JSONObject("""{"head_name":"x", "medical_conditions":["asthma","hypertension"]}""")
        val r = IntakeMapper.familyFromJson(json)
        assertEquals("asthma, hypertension", r.medicalConditions)
    }

    @Test
    fun `displacement and income are normalised from loose strings`() {
        val json = JSONObject("""
            {"head_name":"x","displacement_status":"Refugee","income_level":"moderate"}
        """.trimIndent())
        val r = IntakeMapper.familyFromJson(json)
        assertEquals(DisplacementStatus.Refugee, r.displacementStatus)
        assertEquals(IncomeLevel.Moderate, r.incomeLevel)
    }

    @Test
    fun `items list maps name quantity and category`() {
        val arr = JSONArray("""
            [
              {"name":"Bottled water 1.5L","quantity":24,"unit":"bottles","category":"water","notes":"in 4 boxes"},
              {"name":"Soap bar","quantity":50,"unit":"units","category":"hygiene"}
            ]
        """.trimIndent())
        val items = IntakeMapper.itemsFromJson(arr)
        assertEquals(2, items.size)
        assertEquals("Bottled water 1.5L", items[0].name)
        assertEquals(24, items[0].quantity)
        assertEquals(ItemCategory.Water, items[0].category)
        assertEquals(ItemCategory.Hygiene, items[1].category)
    }

    @Test
    fun `items with blank names are skipped`() {
        val arr = JSONArray("""[{"name":""},{"name":"Tent"}]""")
        val items = IntakeMapper.itemsFromJson(arr)
        assertEquals(1, items.size)
        assertEquals("Tent", items[0].name)
    }
}
