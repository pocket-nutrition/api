package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.IngredientSuggestionWithNutrition
import org.pocketnutrition.api.dto.NutritionalProfileResponse
import org.springframework.stereotype.Service

@Service
class BarcodeService(private val ingredientSearchService: IngredientSearchService) {

    fun findByBarcode(barcode: String): IngredientSuggestionWithNutrition? {
        val doc = ingredientSearchService.lookupDocument("off:$barcode") ?: return null
        val preferred = doc.labels?.firstOrNull { it.lang == "fr" }
            ?: doc.labels?.firstOrNull()
        val label = preferred?.text ?: barcode
        val lang = preferred?.lang ?: "und"
        val hasData = doc.energyKcal != null || doc.proteinG != null
        val nutrition = NutritionalProfileResponse(
            name = label,
            quantity = 100.0,
            unit = "g",
            cookingMethod = "raw",
            measuredState = "raw",
            energyKcal = doc.energyKcal.finite() ?: 0.0,
            proteinG = doc.proteinG.finite() ?: 0.0,
            fatG = doc.fatG.finite() ?: 0.0,
            carbohydratesG = doc.carbohydratesG.finite() ?: 0.0,
            fiberG = doc.fiberG.finite(),
            waterG = null,
            sugarsG = doc.sugarsG.finite(),
            calciumMg = null,
            ironMg = null,
            magnesiumMg = null,
            sodiumMg = doc.sodiumMg.finite(),
            vitaminCMg = null,
            saturatedFatG = null,
            monounsaturatedFatG = null,
            polyunsaturatedFatG = null,
            cholesterolMg = null,
            potassiumMg = null,
            phosphorusMg = null,
            zincMg = null,
            vitaminAUg = null,
            vitaminDUg = null,
            vitaminB6Mg = null,
            vitaminB12Ug = null,
            niacinMg = null,
            confidence = 1.0,
            source = "off_direct",
            available = hasData,
        )
        return IngredientSuggestionWithNutrition(
            ingredientId = barcode,
            label = label,
            lang = lang,
            servingG = 100.0,
            nutrition = nutrition,
        )
    }
}

private fun Double?.finite(): Double? = if (this == null || !isFinite()) null else this
