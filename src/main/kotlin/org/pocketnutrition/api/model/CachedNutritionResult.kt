package org.pocketnutrition.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "nutrition_cache")
data class CachedNutritionResult(
    @Id val id: String,
    val name: String,
    val cookingMethod: String,
    val energyKcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbohydratesG: Double,
    val fiberG: Double?,
    val waterG: Double?,
    val sugarsG: Double?,
    val calciumMg: Double?,
    val ironMg: Double?,
    val magnesiumMg: Double?,
    val sodiumMg: Double?,
    val vitaminCMg: Double?,
    val saturatedFatG: Double?,
    val monounsaturatedFatG: Double?,
    val polyunsaturatedFatG: Double?,
    val cholesterolMg: Double?,
    val potassiumMg: Double?,
    val phosphorusMg: Double?,
    val zincMg: Double?,
    val vitaminAUg: Double?,
    val vitaminDUg: Double?,
    val vitaminB6Mg: Double?,
    val vitaminB12Ug: Double?,
    val niacinMg: Double?,
    val confidence: Double,
    val source: String = "ml",
) {
    companion object {
        fun cacheKey(name: String, cookingMethod: String, measuredState: String = "raw"): String =
            "${name.trim().lowercase()}::${cookingMethod.trim().lowercase()}::${measuredState.trim().lowercase()}"
    }
}
