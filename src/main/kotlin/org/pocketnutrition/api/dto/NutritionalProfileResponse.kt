package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Predicted nutritional profile for one food item (values per portion)")
data class NutritionalProfileResponse(
    val name: String,
    @Schema(description = "Measured quantity") val quantity: Double,
    @Schema(description = "Unit of measure", allowableValues = ["g"]) val unit: String,
    val cookingMethod: String,
    @Schema(description = "Whether the quantity was measured before or after cooking",
            allowableValues = ["raw", "cooked"]) val measuredState: String,
    @Schema(description = "Energy (kcal)") val energyKcal: Double,
    @Schema(description = "Protein (g)") val proteinG: Double,
    @Schema(description = "Total fat (g)") val fatG: Double,
    @Schema(description = "Carbohydrates (g)") val carbohydratesG: Double,
    @Schema(description = "Dietary fibre (g)", nullable = true) val fiberG: Double?,
    @Schema(description = "Water (g)", nullable = true) val waterG: Double?,
    @Schema(description = "Sugars (g)", nullable = true) val sugarsG: Double?,
    @Schema(description = "Calcium (mg)", nullable = true) val calciumMg: Double?,
    @Schema(description = "Iron (mg)", nullable = true) val ironMg: Double?,
    @Schema(description = "Magnesium (mg)", nullable = true) val magnesiumMg: Double?,
    @Schema(description = "Sodium (mg)", nullable = true) val sodiumMg: Double?,
    @Schema(description = "Vitamin C (mg)", nullable = true) val vitaminCMg: Double?,
    @Schema(description = "Saturated fat (g)", nullable = true) val saturatedFatG: Double?,
    @Schema(description = "Monounsaturated fat (g)", nullable = true) val monounsaturatedFatG: Double?,
    @Schema(description = "Polyunsaturated fat (g)", nullable = true) val polyunsaturatedFatG: Double?,
    @Schema(description = "Cholesterol (mg)", nullable = true) val cholesterolMg: Double?,
    @Schema(description = "Potassium (mg)", nullable = true) val potassiumMg: Double?,
    @Schema(description = "Phosphorus (mg)", nullable = true) val phosphorusMg: Double?,
    @Schema(description = "Zinc (mg)", nullable = true) val zincMg: Double?,
    @Schema(description = "Vitamin A (µg)", nullable = true) val vitaminAUg: Double?,
    @Schema(description = "Vitamin D (µg)", nullable = true) val vitaminDUg: Double?,
    @Schema(description = "Vitamin B6 (mg)", nullable = true) val vitaminB6Mg: Double?,
    @Schema(description = "Vitamin B12 (µg)", nullable = true) val vitaminB12Ug: Double?,
    @Schema(description = "Niacin / Vitamin B3 (mg)", nullable = true) val niacinMg: Double?,
    @Schema(description = "Prediction confidence score (0.0–1.0)", example = "0.87") val confidence: Double,
    @Schema(description = "Origin of this result", allowableValues = ["cache", "ml", "off_direct", "unavailable"])
    val source: String,
    @Schema(description = "False when no nutritional data could be found for this item")
    val available: Boolean = true,
)
