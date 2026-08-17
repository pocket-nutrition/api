package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.FoodItemRequest

data class ResolvedFoodItem(
    val request: FoodItemRequest,
    val ingredientId: String,
    val effectiveWeightG: Double = request.quantity,
    val hasCiqualMapping: Boolean = false,
)
