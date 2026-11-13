package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.ReferenceIntakeValue
import org.pocketnutrition.api.dto.ReferenceIntakesResponse
import org.pocketnutrition.api.model.EfsaDrvValueEntity
import org.pocketnutrition.api.repository.EfsaDrvValueRepository
import org.springframework.stereotype.Service

/** Serves the EFSA reference table loaded by pocket-nutrition-data, tagged with the journal's nutrient keys. */
@Service
class ReferenceIntakeService(private val repository: EfsaDrvValueRepository) {

    fun referenceIntakes(): ReferenceIntakesResponse = ReferenceIntakesResponse(
        source = SOURCE,
        sourceUrl = SOURCE_URL,
        values = repository.findAllByOrderByNutrientAscSourceRowAsc().map { it.toValue() },
    )

    private fun EfsaDrvValueEntity.toValue() = ReferenceIntakeValue(
        nutrient = nutrient,
        nutrientKey = nutrientKey(nutrient, unit),
        classification = classification,
        population = population,
        populationDescription = populationDescription,
        ageFrom = ageFrom,
        ageTo = ageTo,
        ageUnit = ageUnit,
        ageLabel = ageLabel,
        pal = pal,
        sex = sex,
        isMale = isMale,
        isFemale = isFemale,
        valueKind = valueKind,
        raw = raw,
        valueMin = valueMin,
        valueMax = valueMax,
        isIncrement = isIncrement,
        unit = unit,
        sourceLink = sourceLink,
        note = note,
    )

    companion object {
        const val SOURCE = "EFSA Dietary Reference Values (DRV Finder)"
        const val SOURCE_URL = "https://multimedia.efsa.europa.eu/drvs/index.htm"

        /**
         * EFSA nutrient name → (journal nutrient key, units a journal total can be compared with).
         *
         * A unit outside the set means the value measures something else than the journal total —
         * energy in MJ, niacin per MJ of energy, vitamin A in retinol equivalents, a UL for one
         * chemical form — so it is served without a key rather than compared wrongly. Units that
         * only need arithmetic (% of energy, per kg of body weight, g instead of mg) are kept: the
         * client converts with the user's own energy target or weight.
         *
         * Not mapped on purpose: "Added and free sugars" (the journal has total sugars), "Water"
         * (the journal only sees water in food, not drinks), "Vitamin A" and "Niacin" (units above).
         */
        private val NUTRIENT_KEYS: Map<String, Pair<String, Set<String>>> = mapOf(
            "Energy" to ("energyKcal" to setOf("kcal/day")),
            "Protein" to ("proteinG" to setOf("g/day", "g/kg bw per day")),
            "Total carbohydrates" to ("carbohydratesG" to setOf("g/day", "E%")),
            "Total fat" to ("fatG" to setOf("g/day", "E%")),
            "Saturated fatty acids (SFA)" to ("saturatedFatG" to setOf("g/day", "E%")),
            "Dietary Fibre" to ("fiberG" to setOf("g/day")),
            "Sodium" to ("sodiumMg" to setOf("mg/day", "g/day")),
            "Potassium" to ("potassiumMg" to setOf("mg/day")),
            "Calcium" to ("calciumMg" to setOf("mg/day")),
            "Iron" to ("ironMg" to setOf("mg/day")),
            "Magnesium" to ("magnesiumMg" to setOf("mg/day")),
            "Phosphorus" to ("phosphorusMg" to setOf("mg/day")),
            "Zinc" to ("zincMg" to setOf("mg/day")),
            "Vitamin C" to ("vitaminCMg" to setOf("mg/day")),
            "Vitamin D" to ("vitaminDUg" to setOf("µg/day")),
            "Vitamin B6" to ("vitaminB6Mg" to setOf("mg/day")),
            "Cobalamin (vitamin B12)" to ("vitaminB12Ug" to setOf("µg/day")),
        )

        /** EFSA writes the micro sign two ways (U+00B5 and Greek mu U+03BC); compare on one. */
        private fun normaliseUnit(unit: String): String = unit.trim().replace('μ', 'µ')

        fun nutrientKey(nutrient: String, unit: String?): String? {
            val (key, units) = NUTRIENT_KEYS[nutrient] ?: return null
            if (unit == null) return key
            return key.takeIf { normaliseUnit(unit) in units }
        }
    }
}
