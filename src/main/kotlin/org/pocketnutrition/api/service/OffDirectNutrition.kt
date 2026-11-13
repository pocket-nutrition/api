package org.pocketnutrition.api.service

import org.pocketnutrition.api.model.CachedNutritionResult

/**
 * Maps an Open Food Facts product document to a per-100g nutrition record with
 * source="off_direct", cookingMethod="raw", confidence=1.0. OFF products carry only 7 macro
 * fields today (see SearchDoc / index_search.py); every other field is intentionally null.
 * Shared by BarcodeService (GET /ingredients/barcode) and NutritionService (POST /nutrition,
 * GET /ingredients/search?quantity=...) so this list is defined exactly once.
 */
internal fun SearchDoc.toOffCachedResult(ingredientId: String, name: String): CachedNutritionResult =
    CachedNutritionResult(
        id = CachedNutritionResult.cacheKey(ingredientId, "raw", "raw"),
        name = name,
        cookingMethod = "raw",
        energyKcal = energyKcal.finite() ?: 0.0,
        proteinG = proteinG.finite() ?: 0.0,
        fatG = fatG.finite() ?: 0.0,
        carbohydratesG = carbohydratesG.finite() ?: 0.0,
        fiberG = fiberG.finite(),
        waterG = null,
        sugarsG = sugarsG.finite(),
        calciumMg = null,
        ironMg = null,
        magnesiumMg = null,
        sodiumMg = sodiumMg.finite(),
        vitaminCMg = null,
        saturatedFatG = null,
        monounsaturatedFatG = null,
        polyunsaturatedFatG = null,
        cholesterolMg = null,
        potassiumMg = null,
        phosphorusMg = null,
        zincMg = null,
        vitaminAUg = null,
        vitaminDUg = null,
        vitaminB6Mg = null,
        vitaminB12Ug = null,
        niacinMg = null,
        confidence = 1.0,
        source = "off_direct",
    )

internal fun Double?.finite(): Double? = if (this == null || !isFinite()) null else this
