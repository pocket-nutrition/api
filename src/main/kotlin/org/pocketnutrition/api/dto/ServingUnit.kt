package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A culinary unit with its gram equivalent for a specific ingredient")
data class ServingUnit(
    @Schema(description = "Unit identifier (e.g. 'teaspoon')", example = "teaspoon")
    val unit: String,
    @Schema(description = "Display label per available language; at least one entry")
    val labels: List<LocalizedText>,
    @Schema(description = "Volume in mL", example = "5.0")
    val volumeMl: Double,
    @Schema(description = "Mass in grams (volume_ml × density_g_ml)", example = "6.0")
    val grams: Double,
)
