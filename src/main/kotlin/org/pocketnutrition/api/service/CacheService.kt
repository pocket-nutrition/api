package org.pocketnutrition.api.service

import org.pocketnutrition.api.model.CachedNutritionResult

interface CacheService {
    fun find(ingredientId: String, cookingMethod: String, measuredState: String = "raw"): CachedNutritionResult?
    fun store(result: CachedNutritionResult)
}
