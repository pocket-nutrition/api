package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Full reference detail for one ingredient from the knowledge base — the current " +
        "known values a client shows alongside a correction form (e.g. the feedback dialog)",
)
data class IngredientDetailResponse(
    @Schema(description = "Stable ASCII slug identifier, as returned by /ingredients/search", example = "poulet_filet")
    val ingredientId: String,
    @Schema(description = "Canonical name per available language; empty when the knowledge base has none")
    val names: List<LocalizedText> = emptyList(),
    @Schema(description = "Alternate names / synonyms per language")
    val aliases: List<LocalizedText> = emptyList(),
    @Schema(description = "Knowledge-base food group (21-value closed vocabulary); 'other' for OFF products and recipes")
    val foodGroup: String? = null,
    @Schema(description = "Typical serving size in grams from the knowledge base; null when unavailable")
    val referenceWeightG: Double? = null,
    @Schema(description = "Preferred display unit in the UI: 'g' or 'ml'; null means grams")
    val displayUnit: String? = null,
    @Schema(description = "Available culinary units with gram equivalents; empty when density is unknown")
    val servingUnits: List<ServingUnit> = emptyList(),
    @Schema(description = "Whether this ingredient can be eaten raw; null when unknown")
    val canEatRaw: Boolean? = null,
    @Schema(description = "Whether this ingredient can be cooked; null when unknown")
    val canEatCooked: Boolean? = null,
    @Schema(description = "Typical eating state from the knowledge base: 'raw' or 'cooked'; null when unknown")
    val typicalState: String? = null,
    @Schema(description = "Reference macro-nutrient profile per 100 g; null when no nutrient data is available")
    val referenceNutrition: ReferenceNutritionResponse? = null,
)

@Schema(description = "Reference macro-nutrient values per 100 g, from the Ciqual-aligned source")
data class ReferenceNutritionResponse(
    @Schema(description = "Energy (kcal) per 100 g") val energyKcal: Double? = null,
    @Schema(description = "Protein (g) per 100 g") val proteinG: Double? = null,
    @Schema(description = "Total fat (g) per 100 g") val fatG: Double? = null,
    @Schema(description = "Carbohydrates (g) per 100 g") val carbohydratesG: Double? = null,
    @Schema(description = "Dietary fibre (g) per 100 g") val fiberG: Double? = null,
)
