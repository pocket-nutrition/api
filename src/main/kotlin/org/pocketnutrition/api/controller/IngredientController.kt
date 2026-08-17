package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.dto.IngredientSuggestion
import org.pocketnutrition.api.dto.IngredientSuggestionWithNutrition
import org.pocketnutrition.api.dto.NutritionalProfileResponse
import org.pocketnutrition.api.dto.RecipeComponentResponse
import org.pocketnutrition.api.service.BarcodeService
import org.pocketnutrition.api.service.DensityLookupService
import org.pocketnutrition.api.service.IngredientSearchService
import org.pocketnutrition.api.service.NutritionService
import org.pocketnutrition.api.service.ResolvedFoodItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Portion and state defaults applied when the knowledge base has no value for a suggestion.
 *
 * These belong to the server on purpose: they are the same answer for every client, and duplicating
 * them per platform is how the sandbox ended up with two regex tables mapping food labels to weights.
 * `DEFAULT_SERVING_G` matches the value BarcodeService already returns and the "100 g default" the
 * published contract has always documented for `servingG`.
 */
private const val DEFAULT_SERVING_G = 100.0

/** No cooking transform assumed — the neutral answer when knowledge has no eating state. */
private const val DEFAULT_TYPICAL_STATE = "raw"

/** Portion read as an as-served weight, so a client does not apply the yield factor to it. */
private const val DEFAULT_PORTION_MEASURED_STATE = "cooked"

@Tag(name = "Ingredients", description = "Ingredient search and barcode lookup")
@RestController
@RequestMapping("/ingredients")
class IngredientController(
    private val ingredientSearchService: IngredientSearchService,
    private val nutritionService: NutritionService,
    private val barcodeService: BarcodeService,
    private val densityLookupService: DensityLookupService,
) {

    @Operation(
        summary = "Search ingredients as you type",
        description = "Returns matching suggestions. When quantity + cooking_method are provided, " +
            "each suggestion also includes its nutritional profile (cache or ML prediction)."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Matching ingredient suggestions"),
        ApiResponse(responseCode = "400", description = "Blank query")
    )
    @GetMapping("/search")
    fun search(
        @Parameter(description = "Search query", required = true)
        @RequestParam q: String,
        @Parameter(description = "Preferred language code (fr, en)")
        @RequestParam(required = false) lang: String?,
        @Parameter(description = "Max results to return (1–50, default 10)")
        @RequestParam(defaultValue = "10") limit: Int,
        @Parameter(description = "Quantity for nutritional enrichment — requires cooking_method")
        @RequestParam(required = false) quantity: Double?,
        @Parameter(description = "Unit of quantity ('g' or 'ml', default 'g')")
        @RequestParam(required = false, defaultValue = "g") unit: String,
        @Parameter(description = "Cooking method (required when quantity is set)")
        @RequestParam(name = "cooking_method", required = false) cookingMethod: String?,
        @Parameter(description = "Whether quantity is before or after cooking")
        @RequestParam(name = "measured_state", required = false, defaultValue = "raw") measuredState: String,
    ): ResponseEntity<List<IngredientSuggestionWithNutrition>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val clampedLimit = limit.coerceIn(1, 50)
        val (termCount, suggestions) = ingredientSearchService.search(q, lang, clampedLimit)

        if (quantity != null && cookingMethod != null) {
            // Recipes are composite — nutrition is computed per component card client-side.
            val enrichable = suggestions.filter { it.productType != "recipe" }
            val resolvedItems = enrichable.map { suggestion ->
                val effectiveWeightG = if (unit == "ml") {
                    quantity * densityLookupService.getDensity(suggestion.ingredientId)
                } else {
                    quantity
                }
                ResolvedFoodItem(
                    request = FoodItemRequest(
                        name = suggestion.label,
                        quantity = quantity,
                        unit = unit,
                        cookingMethod = cookingMethod,
                        measuredState = measuredState,
                    ),
                    ingredientId = suggestion.ingredientId,
                    effectiveWeightG = effectiveWeightG,
                )
            }
            val nutritionResults = nutritionService.getNutritionForResolved(resolvedItems)
            val nutritionById = enrichable.mapIndexed { i, s -> s.ingredientId to nutritionResults.getOrNull(i) }.toMap()
            return ResponseEntity.ok()
                .header("X-Segment-Count", termCount.toString())
                .body(suggestions.map { toResponse(it, nutritionById[it.ingredientId]) })
        }

        return ResponseEntity.ok()
            .header("X-Segment-Count", termCount.toString())
            .body(suggestions.map { toResponse(it, null) })
    }

    private fun toResponse(
        suggestion: IngredientSuggestion,
        nutrition: NutritionalProfileResponse?,
    ): IngredientSuggestionWithNutrition =
        IngredientSuggestionWithNutrition(
            ingredientId = suggestion.ingredientId,
            label = suggestion.label,
            lang = suggestion.lang,
            // Defaults live here, not in the clients. Open Food Facts documents carry no knowledge
            // metadata at all — index_search._build_off_document hardcodes typical_state to null and
            // derives portion_g from the package quantity — so without these fallbacks every client
            // has to invent a portion weight and an eating state from the product label. The sandbox
            // used to do exactly that with two regex tables, which is what this replaces. The real
            // fix is upstream (populate knowledge source_mappings/off/ so an OFF barcode inherits its
            // canonical ingredient's metadata); until then the server answers, once, for everyone.
            servingG = suggestion.portionG ?: DEFAULT_SERVING_G,
            nutrition = nutrition,
            servingUnits = suggestion.servingUnits,
            displayUnit = suggestion.displayUnit,
            typicalState = suggestion.typicalState ?: DEFAULT_TYPICAL_STATE,
            portionMeasuredState = suggestion.portionMeasuredState ?: DEFAULT_PORTION_MEASURED_STATE,
            canEatRaw = suggestion.canEatRaw,
            canEatCooked = suggestion.canEatCooked,
            yieldFactors = suggestion.yieldFactors,
            productType = suggestion.productType,
            imageUrl = suggestion.imageUrl,
            brand = suggestion.brand,
            foodGroup = suggestion.foodGroup,
            servingMode = suggestion.servingMode,
            unitLabelFr = suggestion.unitLabelFr,
            unitLabelEn = suggestion.unitLabelEn,
            recipeComponents = suggestion.recipeComponents?.map { c ->
                RecipeComponentResponse(
                    weightG = c.weightG,
                    cookingMethod = c.cookingMethod,
                    ingredient = toResponse(c.ingredient, null),
                )
            },
        )

    @Operation(summary = "Look up a product by EAN-13 barcode")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Nutritional profile (per 100 g) for the scanned product"),
        ApiResponse(responseCode = "404", description = "Barcode not found in OpenFoodFacts catalogue")
    )
    @GetMapping("/barcode/{ean13}")
    fun barcode(
        @Parameter(description = "EAN-13 barcode", required = true)
        @PathVariable ean13: String,
    ): ResponseEntity<IngredientSuggestionWithNutrition> {
        val result = barcodeService.findByBarcode(ean13) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}
