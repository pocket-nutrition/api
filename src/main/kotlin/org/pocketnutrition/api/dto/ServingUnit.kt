package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A culinary unit with its gram equivalent for a specific ingredient")
data class ServingUnit(
    @Schema(description = "Unit identifier (e.g. 'teaspoon')", example = "teaspoon")
    val unit: String,
    @Schema(description = "French display label", example = "c.à.c.")
    val labelFr: String,
    @Schema(description = "English display label", example = "tsp")
    val labelEn: String? = null,
    @Schema(description = "Volume in mL", example = "5.0")
    val volumeMl: Double,
    @Schema(description = "Mass in grams (volume_ml × density_g_ml)", example = "6.0")
    val grams: Double,
)
