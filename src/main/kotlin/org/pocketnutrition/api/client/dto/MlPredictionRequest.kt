package org.pocketnutrition.api.client.dto

/**
 * Request DTO sent to pocket-nutrition-ml POST /predict.
 * Represents a single meal: one cooking context + one or more raw ingredients.
 * snake_case field names match the Python FastAPI schema directly.
 */
data class MlMealInput(
    val cooking_type: String,
    val cooking_duration_min: Double? = null,
    val ingredients: List<MlIngredientInput>,
)

/**
 * Raw nutritional profile per 100g (Ciqual-aligned schema) sourced from ingredient_profiles (Elasticsearch).
 * Sent to ML so it can build the feature vector without consulting its own food_index.
 */
data class MlRawProfile(
    val energy_kcal: Double?,
    val water_g: Double?,
    val protein_g: Double?,
    val fat_g: Double?,
    val carbohydrates_g: Double?,
    val sugars_g: Double?,
    val fiber_g: Double?,
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
    val food_group: String,
    val is_plant: Boolean,
)

data class MlIngredientInput(
    val ingredient_id: String,
    val weight_g: Double,
    val measured_state: String = "raw",
    val raw_profile: MlRawProfile? = null,
)
