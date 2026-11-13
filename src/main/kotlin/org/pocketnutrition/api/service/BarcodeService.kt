package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.dto.IngredientSuggestionWithNutrition
import org.springframework.stereotype.Service

@Service
class BarcodeService(private val ingredientSearchService: IngredientSearchService) {

    fun findByBarcode(barcode: String, lang: String?): IngredientSuggestionWithNutrition? {
        val doc = ingredientSearchService.lookupDocument("off:$barcode") ?: return null
        val preferred = doc.labels.pickPreferred(lang)
        val label = preferred?.text ?: barcode
        val resolvedLang = preferred?.lang ?: "und"
        val hasData = doc.energyKcal != null || doc.proteinG != null
        val request = FoodItemRequest(name = label, quantity = 100.0, unit = "g", cookingMethod = "raw", measuredState = "raw")
        val nutrition = doc.toOffCachedResult(barcode, label)
            .toResponse(request, effectiveWeightG = 100.0)
            .copy(available = hasData)
        return IngredientSuggestionWithNutrition(
            ingredientId = barcode,
            label = label,
            lang = resolvedLang,
            servingG = 100.0,
            nutrition = nutrition,
        )
    }
}
