package org.pocketnutrition.api.service

import org.pocketnutrition.api.client.dto.MlNutritionalResult
import org.pocketnutrition.api.client.dto.MlPredictionResponse
import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.dto.NutritionalProfileResponse
import org.pocketnutrition.api.model.CachedNutritionResult
import org.springframework.stereotype.Service

@Service
class NutritionService(
    private val cacheService: CacheService,
    private val mlClientService: MlClientService,
    private val ingredientResolutionService: IngredientResolutionService,
    private val densityLookupService: DensityLookupService,
    private val ingredientSearchService: IngredientSearchService,
) {

    fun getNutrition(items: List<FoodItemRequest>): List<NutritionalProfileResponse> {
        val resolvedItems: List<ResolvedFoodItem> = items.map { item ->
            val resolvedId = item.ingredientId?.takeIf { it.isNotBlank() }
                ?: item.name?.let { ingredientResolutionService.resolve(it) }
            val ingredientId = resolvedId ?: slugify(item.name ?: "")
            val effectiveWeightG = when (item.unit) {
                "ml" -> item.quantity * densityLookupService.getDensity(ingredientId)
                "item" -> {
                    val doc = ingredientSearchService.lookupDocument(ingredientId)
                    val portionG = doc?.portionG
                        ?: doc?.states?.values?.firstNotNullOfOrNull { it.portionG }
                    if (portionG != null) item.quantity * portionG else item.quantity
                }
                else -> item.quantity
            }
            ResolvedFoodItem(item, ingredientId, effectiveWeightG, hasCiqualMapping = resolvedId != null)
        }
        return getNutritionForResolved(resolvedItems)
    }

    fun getNutritionForResolved(resolvedItems: List<ResolvedFoodItem>): List<NutritionalProfileResponse> {
        // Open Food Facts products are returned as-is (their own values, cookingMethod="raw"): the
        // ML cooking-transform model was trained only on raw-whole-food -> cooked-whole-food pairs
        // (Ciqual/McCance/USDA) and has never seen an industrial/packaged product, so running it on
        // one would extrapolate outside its training distribution. These items also bypass the
        // nutrition cache entirely — building the response below is already a single ES lookup, and
        // the cache key embeds the *requested* cooking method, which would churn on every different
        // request and would disagree with the forced "raw" result once caching is re-enabled.
        val offResponses: Map<Int, NutritionalProfileResponse> = resolvedItems.withIndex()
            .mapNotNull { (idx, item) ->
                ingredientSearchService.findOffDoc(item.ingredientId)?.let { doc ->
                    val hasData = doc.energyKcal != null || doc.proteinG != null
                    idx to doc.toOffCachedResult(item.ingredientId, item.request.name ?: item.ingredientId)
                        .toResponse(item.request, item.effectiveWeightG)
                        .copy(available = hasData)
                }
            }
            .toMap()

        val cacheHits: List<CachedNutritionResult?> = resolvedItems.mapIndexed { idx, item ->
            if (idx in offResponses) null else cacheService.find(item.ingredientId, item.request.cookingMethod, item.request.measuredState)
        }

        val mlMissIndices = cacheHits.indices.filter { it !in offResponses && cacheHits[it] == null }
        val mlPredictions: List<MlPredictionResponse> = if (mlMissIndices.isNotEmpty()) {
            mlClientService.predict(mlMissIndices.map { resolvedItems[it] })
        } else {
            emptyList()
        }

        mlPredictions.forEachIndexed { i, prediction ->
            // A missing or unknown-ingredient result is "no data", not a profile of zeros: never cache it.
            if (i < mlMissIndices.size && prediction.isUsable()) {
                cacheService.store(prediction.toCachedResult(resolvedItems[mlMissIndices[i]]))
            }
        }

        val mlByIndex: Map<Int, MlPredictionResponse> = mlMissIndices
            .mapIndexedNotNull { mlIdx, origIdx ->
                if (mlIdx < mlPredictions.size) origIdx to mlPredictions[mlIdx] else null
            }
            .toMap()

        return resolvedItems.mapIndexed { idx, item ->
            val cached = cacheHits[idx]
            when {
                offResponses.containsKey(idx) -> offResponses[idx]!!
                cached != null -> cached.toResponse(item.request, item.effectiveWeightG)
                mlByIndex.containsKey(idx) -> mlByIndex.getValue(idx).let { prediction ->
                    if (prediction.result == null) unavailableResponse(item.request) else prediction.toResponse(item)
                }
                else -> unavailableResponse(item.request)
            }
        }
    }

    /**
     * An OFF product document for this ingredient id, if any. Tries the id as given, then the
     * `off:`-prefixed convention `RawProfileLookupService` also falls back to, since search results
     * already carry the `off:` prefix while the barcode endpoint's id is bare.
     */
    private fun IngredientSearchService.findOffDoc(ingredientId: String): SearchDoc? =
        lookupDocument(ingredientId)?.takeIf { it.productType == "off_product" }
            ?: ingredientId.takeUnless { it.startsWith("off:") }
                ?.let { lookupDocument("off:$it") }
                ?.takeIf { it.productType == "off_product" }

    private fun unavailableResponse(item: FoodItemRequest) = NutritionalProfileResponse(
        name = item.name ?: item.ingredientId ?: "", quantity = item.quantity, unit = item.unit,
        cookingMethod = item.cookingMethod, measuredState = item.measuredState,
        energyKcal = 0.0, proteinG = 0.0, fatG = 0.0, carbohydratesG = 0.0,
        fiberG = null, waterG = null, sugarsG = null, calciumMg = null, ironMg = null,
        magnesiumMg = null, sodiumMg = null, vitaminCMg = null,
        saturatedFatG = null, monounsaturatedFatG = null, polyunsaturatedFatG = null,
        cholesterolMg = null, potassiumMg = null, phosphorusMg = null, zincMg = null,
        vitaminAUg = null, vitaminDUg = null, vitaminB6Mg = null, vitaminB12Ug = null,
        niacinMg = null, confidence = 0.0, source = "unavailable", available = false,
    )
}

// ML result arrives pre-scaled to effectiveWeightG; normalise to per-100g before caching.
private fun MlPredictionResponse.toCachedResult(item: ResolvedFoodItem): CachedNutritionResult {
    val r: MlNutritionalResult? = result
    val scale = item.effectiveWeightG / 100.0
    return CachedNutritionResult(
        id = CachedNutritionResult.cacheKey(item.ingredientId, item.request.cookingMethod, item.request.measuredState),
        name = item.request.name ?: item.ingredientId,
        cookingMethod = item.request.cookingMethod,
        energyKcal = (r?.energy_kcal ?: 0.0) / scale,
        proteinG = (r?.protein_g ?: 0.0) / scale,
        fatG = (r?.fat_g ?: 0.0) / scale,
        carbohydratesG = (r?.carbohydrates_g ?: 0.0) / scale,
        fiberG = r?.fiber_g?.div(scale),
        waterG = r?.water_g?.div(scale),
        sugarsG = r?.sugars_g?.div(scale),
        calciumMg = r?.calcium_mg?.div(scale),
        ironMg = r?.iron_mg?.div(scale),
        magnesiumMg = r?.magnesium_mg?.div(scale),
        sodiumMg = r?.sodium_mg?.div(scale),
        vitaminCMg = r?.vitamin_c_mg?.div(scale),
        saturatedFatG = r?.saturated_fat_g?.div(scale),
        monounsaturatedFatG = r?.monounsaturated_fat_g?.div(scale),
        polyunsaturatedFatG = r?.polyunsaturated_fat_g?.div(scale),
        cholesterolMg = r?.cholesterol_mg?.div(scale),
        potassiumMg = r?.potassium_mg?.div(scale),
        phosphorusMg = r?.phosphorus_mg?.div(scale),
        zincMg = r?.zinc_mg?.div(scale),
        vitaminAUg = r?.vitamin_a_ug?.div(scale),
        vitaminDUg = r?.vitamin_d_ug?.div(scale),
        vitaminB6Mg = r?.vitamin_b6_mg?.div(scale),
        vitaminB12Ug = r?.vitamin_b12_ug?.div(scale),
        niacinMg = r?.niacin_mg?.div(scale),
        confidence = r?.confidence ?: 0.0,
        source = "ml",
    )
}

private const val MIN_CONFIDENCE = 0.6

/** ML flags an ingredient as unknown when it had no raw profile to transform. */
private fun MlPredictionResponse.hasUnknownIngredient(): Boolean = ingredients.any { it.unknown }

private fun MlPredictionResponse.isUsable(): Boolean = result != null && !hasUnknownIngredient()

// Cached values are per-100g; scale to the effective gram weight.
internal fun CachedNutritionResult.toResponse(item: FoodItemRequest, effectiveWeightG: Double): NutritionalProfileResponse {
    val scale = effectiveWeightG / 100.0
    return NutritionalProfileResponse(
        name = name, quantity = item.quantity, unit = item.unit,
        cookingMethod = cookingMethod, measuredState = item.measuredState,
        energyKcal = energyKcal * scale,
        proteinG = proteinG * scale,
        fatG = fatG * scale,
        carbohydratesG = carbohydratesG * scale,
        fiberG = fiberG?.times(scale),
        waterG = waterG?.times(scale),
        sugarsG = sugarsG?.times(scale),
        calciumMg = calciumMg?.times(scale),
        ironMg = ironMg?.times(scale),
        magnesiumMg = magnesiumMg?.times(scale),
        sodiumMg = sodiumMg?.times(scale),
        vitaminCMg = vitaminCMg?.times(scale),
        saturatedFatG = saturatedFatG?.times(scale),
        monounsaturatedFatG = monounsaturatedFatG?.times(scale),
        polyunsaturatedFatG = polyunsaturatedFatG?.times(scale),
        cholesterolMg = cholesterolMg?.times(scale),
        potassiumMg = potassiumMg?.times(scale),
        phosphorusMg = phosphorusMg?.times(scale),
        zincMg = zincMg?.times(scale),
        vitaminAUg = vitaminAUg?.times(scale),
        vitaminDUg = vitaminDUg?.times(scale),
        vitaminB6Mg = vitaminB6Mg?.times(scale),
        vitaminB12Ug = vitaminB12Ug?.times(scale),
        niacinMg = niacinMg?.times(scale),
        confidence = confidence,
        source = if (source == "ml") "cache" else source,
        available = confidence >= MIN_CONFIDENCE,
    )
}

// ML result is already scaled to effectiveWeightG (= requested quantity after density conversion).
private fun MlPredictionResponse.toResponse(item: ResolvedFoodItem): NutritionalProfileResponse {
    val r: MlNutritionalResult? = result
    val confidence = r?.confidence ?: 0.0
    return NutritionalProfileResponse(
        name = item.request.name ?: item.ingredientId, quantity = item.request.quantity, unit = item.request.unit,
        cookingMethod = item.request.cookingMethod, measuredState = item.request.measuredState,
        energyKcal = r?.energy_kcal ?: 0.0,
        proteinG = r?.protein_g ?: 0.0,
        fatG = r?.fat_g ?: 0.0,
        carbohydratesG = r?.carbohydrates_g ?: 0.0,
        fiberG = r?.fiber_g,
        waterG = r?.water_g,
        sugarsG = r?.sugars_g,
        calciumMg = r?.calcium_mg,
        ironMg = r?.iron_mg,
        magnesiumMg = r?.magnesium_mg,
        sodiumMg = r?.sodium_mg,
        vitaminCMg = r?.vitamin_c_mg,
        saturatedFatG = r?.saturated_fat_g,
        monounsaturatedFatG = r?.monounsaturated_fat_g,
        polyunsaturatedFatG = r?.polyunsaturated_fat_g,
        cholesterolMg = r?.cholesterol_mg,
        potassiumMg = r?.potassium_mg,
        phosphorusMg = r?.phosphorus_mg,
        zincMg = r?.zinc_mg,
        vitaminAUg = r?.vitamin_a_ug,
        vitaminDUg = r?.vitamin_d_ug,
        vitaminB6Mg = r?.vitamin_b6_mg,
        vitaminB12Ug = r?.vitamin_b12_ug,
        niacinMg = r?.niacin_mg,
        confidence = confidence,
        source = "ml",
        available = confidence >= MIN_CONFIDENCE && !hasUnknownIngredient(),
    )
}
