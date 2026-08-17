package org.pocketnutrition.api.client.dto

/**
 * Response DTO received from pocket-nutrition-ml POST /predict.
 * snake_case field names match the Python FastAPI schema directly.
 */
data class MlPredictionResponse(
    val cooking_type: String,
    val cooking_duration_min: Double?,
    val ingredients: List<MlIngredientResult>,
    val result: MlNutritionalResult?,
)

data class MlIngredientResult(
    val ingredient_id: String,
    val weight_g: Double,
    val unknown: Boolean,
)

data class MlNutritionalResult(
    val energy_kcal: Double?,
    val protein_g: Double?,
    val fat_g: Double?,
    val carbohydrates_g: Double?,
    val fiber_g: Double?,
    val water_g: Double?,
    val sugars_g: Double?,
    val calcium_mg: Double?,
    val iron_mg: Double?,
    val magnesium_mg: Double?,
    val sodium_mg: Double?,
    val vitamin_c_mg: Double?,
    val saturated_fat_g: Double?,
    val monounsaturated_fat_g: Double?,
    val polyunsaturated_fat_g: Double?,
    val cholesterol_mg: Double?,
    val potassium_mg: Double?,
    val phosphorus_mg: Double?,
    val zinc_mg: Double?,
    val vitamin_a_ug: Double?,
    val vitamin_d_ug: Double?,
    val vitamin_b6_mg: Double?,
    val vitamin_b12_ug: Double?,
    val niacin_mg: Double?,
    val confidence: Double,
)
