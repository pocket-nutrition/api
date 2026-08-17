package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A food item to include in the nutritional prediction")
data class FoodItemRequest(
    @Schema(description = "Ingredient name (free text); optional when ingredientId is provided", example = "chicken breast")
    val name: String? = null,
    @Schema(description = "Measured quantity", example = "150.0")
    val quantity: Double,
    @Schema(description = "Unit of measure", example = "g", allowableValues = ["g", "ml", "item"])
    val unit: String,
    @Schema(
        description = "Cooking method applied to the ingredient",
        example = "grilled",
        // Must stay in sync with the validation whitelist in NutritionController.getNutrition.
        allowableValues = ["raw", "boiled", "steamed", "grilled", "roasted", "fried", "cooked"]
    )
    val cookingMethod: String,
    @Schema(
        description = "Whether the quantity was measured before or after cooking",
        example = "raw",
        allowableValues = ["raw", "cooked"]
    )
    val measuredState: String,
    @Schema(description = "Pre-resolved canonical ingredient slug from /ingredients/search; bypasses name resolution when provided", example = "poulet_filet")
    val ingredientId: String? = null,
)
