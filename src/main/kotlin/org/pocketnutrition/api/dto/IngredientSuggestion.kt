package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

data class StateMeta(
    @Schema(description = "Typical serving size in grams for this state; null when unavailable")
    val portionG: Double? = null,
    @Schema(description = "Density in g/mL for this state; null when unavailable")
    val densityGMl: Double? = null,
)

@Schema(description = "Ingredient search result")
data class IngredientSuggestion(
    @Schema(description = "Stable ASCII slug identifier (e.g. 'poulet_filet')", example = "poulet_filet")
    val ingredientId: String,
    @Schema(description = "Human-readable label in the matched language", example = "Poulet, filet")
    val label: String,
    @Schema(description = "BCP-47 language code of the label (fr, en) or 'und' if undetermined", example = "fr")
    val lang: String,
    @Schema(description = "Typical serving size in grams from the knowledge base; null when unavailable")
    val portionG: Double? = null,
    @Schema(description = "Available culinary units with gram equivalents; empty when density is unknown")
    val servingUnits: List<ServingUnit> = emptyList(),
    @Schema(description = "Preferred display unit in the UI: 'g' or 'ml'; null means grams")
    val displayUnit: String? = null,
    @Schema(description = "Typical eating state from the knowledge base: 'raw' or 'cooked'; null when unknown")
    val typicalState: String? = null,
    @Schema(description = "Whether the portion_g serving is measured before cooking (raw) or after (cooked); resolved server-side, defaults to the ingredient's state")
    val portionMeasuredState: String? = null,
    @Schema(description = "Whether this ingredient can be eaten raw; null when unknown")
    val canEatRaw: Boolean? = null,
    @Schema(description = "Whether this ingredient can be cooked; null when unknown")
    val canEatCooked: Boolean? = null,
    @Schema(description = "Per-method yield factor (cooked_weight / raw_weight) from the knowledge base; null when unknown")
    val yieldFactors: Map<String, Double>? = null,
    @Schema(description = "Source type: 'ingredient' for knowledge-base items, 'off_product' for Open Food Facts branded products; null for legacy clients")
    val productType: String? = null,
    @Schema(description = "Product front image URL from Open Food Facts; null for canonical ingredients or when unavailable")
    val imageUrl: String? = null,
    @Schema(description = "Brand that sells this Open Food Facts product; null for canonical ingredients and recipes")
    val brand: String? = null,
    @Schema(description = "Knowledge-base food group (21-value closed vocabulary); 'other' for OFF products and recipes")
    val foodGroup: String? = null,
    @Schema(description = "Natural measurement mode: 'item' = countable whole units, 'weight' = grams, 'volume' = mL, 'measure' = culinary units; null = weight by default")
    val servingMode: String? = null,
    @Schema(description = "French display label for one whole unit when serving_mode is 'item' (e.g. 'oeuf')")
    val unitLabelFr: String? = null,
    val unitLabelEn: String? = null,
    @Schema(description = "Per-state portion and density metadata from the knowledge base; null for OFF products")
    val states: Map<String, StateMeta>? = null,
    @Schema(description = "Recipe components with per-person default weights; only present when productType is 'recipe'")
    val recipeComponents: List<RecipeComponent>? = null,
)

@Schema(description = "One ingredient of a generic recipe, with its default portion and cooking method")
data class RecipeComponent(
    @Schema(description = "Default weight in grams for one person", example = "200.0")
    val weightG: Double,
    @Schema(description = "Cooking method this component is prepared with", example = "boiled")
    val cookingMethod: String,
    @Schema(description = "Full suggestion for the component ingredient")
    val ingredient: IngredientSuggestion,
)
