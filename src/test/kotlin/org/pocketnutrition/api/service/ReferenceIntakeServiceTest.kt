package org.pocketnutrition.api.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.pocketnutrition.api.model.EfsaDrvValueEntity
import org.pocketnutrition.api.repository.EfsaDrvValueRepository

class ReferenceIntakeServiceTest {

    private val repository: EfsaDrvValueRepository = mock()
    private val service = ReferenceIntakeService(repository)

    private fun row(
        nutrient: String,
        valueKind: String = "pri",
        unit: String? = "mg/day",
        raw: String = "11",
        min: Double? = 11.0,
        max: Double? = 11.0,
        increment: Boolean = false,
    ) = EfsaDrvValueEntity(
        id = 1, sourceRow = 0, classification = "Minerals", nutrient = nutrient,
        population = "Adults", populationDescription = "Adults",
        ageFrom = 18.0, ageTo = 999.0, ageUnit = "y", ageLabel = "≥ 18 years", pal = null,
        sex = "Male", isMale = true, isFemale = false,
        valueKind = valueKind, raw = raw, valueMin = min, valueMax = max, isIncrement = increment,
        unit = unit, sourceLink = "https://doi.org/10.2903/j.efsa.2015.4254", note = null,
    )

    @Test
    fun `every stored value is returned with its EFSA fields intact`() {
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc()).thenReturn(listOf(row("Iron")))

        val value = service.referenceIntakes().values.single()

        assertEquals("Iron", value.nutrient)
        assertEquals("pri", value.valueKind)
        assertEquals(11.0, value.valueMin)
        assertEquals("mg/day", value.unit)
        assertEquals("https://doi.org/10.2903/j.efsa.2015.4254", value.sourceLink)
    }

    @Test
    fun `a nutrient the journal tracks in the same unit carries its nutrient key`() {
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc())
            .thenReturn(listOf(row("Iron"), row("Energy", valueKind = "ar_kcal", unit = "kcal/day", raw = "2338")))

        val keys = service.referenceIntakes().values.associate { it.nutrient to it.nutrientKey }

        assertEquals("ironMg", keys["Iron"])
        assertEquals("energyKcal", keys["Energy"])
    }

    @Test
    fun `a value in a unit the journal cannot compare against has no nutrient key`() {
        // Energy in MJ, protein per kg of body weight, niacin per MJ, vitamin A in retinol
        // equivalents: none of them is directly comparable to a journal total in that key's unit.
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc()).thenReturn(
            listOf(
                row("Energy", valueKind = "ar", unit = "MJ/day"),
                row("Niacin", unit = "mg NE/MJ"),
                row("Vitamin A", unit = "μg RE/day"),
            )
        )

        assertTrue(service.referenceIntakes().values.all { it.nutrientKey == null })
    }

    @Test
    fun `protein per kg of body weight keeps the protein key with its own unit`() {
        // The app multiplies by the user's weight; the key says which journal total it is compared with.
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc())
            .thenReturn(listOf(row("Protein", unit = "g/kg bw per day", raw = "0.83", min = 0.83, max = 0.83)))

        val value = service.referenceIntakes().values.single()

        assertEquals("proteinG", value.nutrientKey)
        assertEquals("g/kg bw per day", value.unit)
    }

    @Test
    fun `added and free sugars are not mapped to total sugars`() {
        // Ciqual's sugars are total sugars; EFSA's advice is about added and free sugars.
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc())
            .thenReturn(listOf(row("Added and free sugars", valueKind = "ul", unit = null, raw = "ND", min = null, max = null)))

        assertNull(service.referenceIntakes().values.single().nutrientKey)
    }

    @Test
    fun `an empty table yields an empty list`() {
        whenever(repository.findAllByOrderByNutrientAscSourceRowAsc()).thenReturn(emptyList())

        assertTrue(service.referenceIntakes().values.isEmpty())
    }
}
