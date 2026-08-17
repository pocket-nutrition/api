package org.pocketnutrition.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ingredient search result, optionally enriched with nutritional values")
data class IngredientSuggestionWithNutrition(
    @Schema(description = "Stable ASCII slug identifier", example = "poulet_filet")
    val ingredientId: String,
    @Schema(description = "Human-readable label in the matched language", example = "Poulet, filet")
    val label: String,
    @Schema(description = "BCP-47 language code of the label (fr, en) or 'und' if undetermined", example = "fr")
    val lang: String,
    @Schema(description = "Typical serving size in grams (100 g default)", example = "100.0")
    val servingG: Double?,
    @Schema(description = "Nutritional profile for the requested quantity/cooking context; null when not requested")
    val nutrition: NutritionalProfileResponse?,
    @Schema(description = "Available culinary units with gram equivalents; empty when density is unknown")
    val servingUnits: List<ServingUnit> = emptyList(),
    @Schema(description = "Preferred display unit in the UI: 'g' or 'ml'; null means grams")
    val displayUnit: String? = null,
    @Schema(
        description = "Typical eating state from the knowledge base. Knowledge stores seven values: "
            + "'raw', 'processed', 'cooked', 'boiled', 'steamed', 'roasted', 'fried'. Defaults to 'raw' "
            + "when the knowledge base has no value (every Open Food Facts product), so a client never "
            + "has to invent an eating state.",
    )
    val typicalState: String? = null,
    @Schema(
        description = "Whether the servingG portion is measured before cooking ('raw') or after "
            + "('cooked'). Decides whether the yield factor applies to servingG, so it changes the "
            + "weight a client shows. Defaults to 'cooked' when the knowledge base has no value.",
    )
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
    @Schema(
        description = "Brand that sells this Open Food Facts product, for telling otherwise "
            + "identical rows apart: the catalogue holds dozens of products whose label is the bare "
            + "food name, so without this a client renders several rows reading only 'Poulet'. Null "
            + "for canonical ingredients and recipes. Never folded into `label` — the client owns "
            + "that typography and deduplicates on the label alone.",
        example = "Le Gaulois",
    )
    val brand: String? = null,
    @Schema(
        description = "Knowledge-base food group, a 21-value closed vocabulary (see "
            + "pocket-nutrition-knowledge/schemas/ingredient.schema.json). Lets a client show a "
            + "category icon for a curated ingredient, which has no photo. Always 'other' for OFF "
            + "products and recipes, so it distinguishes curated entries from each other, not from "
            + "products — use `productType` for that.",
        example = "vegetable",
    )
    val foodGroup: String? = null,
    @Schema(description = "Natural measurement mode: 'item' = countable whole units, 'weight' = grams, 'volume' = mL, 'measure' = culinary units; null = weight by default")
    val servingMode: String? = null,
    @Schema(description = "French display label for one whole unit when serving_mode is 'item' (e.g. 'oeuf')")
    val unitLabelFr: String? = null,
    val unitLabelEn: String? = null,
    @Schema(description = "Recipe components with per-person default weights; only present when productType is 'recipe'")
    val recipeComponents: List<RecipeComponentResponse>? = null,
)

@Schema(description = "One ingredient of a generic recipe, with its default portion and cooking method")
data class RecipeComponentResponse(
    @Schema(description = "Default weight in grams for one person", example = "200.0")
    val weightG: Double,
    @Schema(description = "Cooking method this component is prepared with", example = "boiled")
    val cookingMethod: String,
    @Schema(description = "Full suggestion for the component ingredient (nutrition is never populated here)")
    val ingredient: IngredientSuggestionWithNutrition,
)
