package org.pocketnutrition.api.service

import org.springframework.stereotype.Service

@Service
class DensityLookupService {

    fun getDensity(ingredientId: String): Double = DENSITY_MAP.getOrDefault(ingredientId, 1.0)

    companion object {
        private val DENSITY_MAP: Map<String, Double> = mapOf(
            // Oils
            "huile_olive" to 0.916,
            "huile_d_olive" to 0.916,
            "huile_tournesol" to 0.920,
            "huile_colza" to 0.915,
            "huile_vegetale" to 0.920,
            "huile_de_coco" to 0.924,
            "huile_de_palme" to 0.891,
            "margarine" to 0.893,
            // Dairy
            "lait_entier" to 1.032,
            "lait_demi_ecreme" to 1.030,
            "lait_ecreme" to 1.034,
            "lait_de_soja" to 1.025,
            "lait_de_coco" to 1.050,
            "creme_fraiche" to 1.005,
            "creme_liquide" to 1.005,
            "yaourt" to 1.030,
            "yaourt_nature" to 1.030,
            // Alcohols
            "vin_rouge" to 0.994,
            "vin_blanc" to 0.993,
            "vin_rose" to 0.992,
            "biere" to 1.010,
            "biere_blonde" to 1.010,
            "biere_brune" to 1.015,
            "champagne" to 0.990,
            "whisky" to 0.950,
            "rhum" to 0.940,
            "vodka" to 0.953,
            "gin" to 0.946,
            "cognac" to 0.940,
            "cidre" to 1.007,
            // Juices & soft drinks
            "jus_orange" to 1.044,
            "jus_de_pomme" to 1.048,
            "jus_de_raisin" to 1.058,
            "jus_ananas" to 1.052,
            "soda" to 1.040,
            "eau_gazeuse" to 1.0,
            "eau" to 1.0,
            // Sauces & condiments
            "sauce_soja" to 1.190,
            "vinaigre" to 1.006,
            "vinaigre_balsamique" to 1.270,
            "miel" to 1.420,
            "sirop_d_erable" to 1.320,
            "sirop_agave" to 1.340,
        )
    }
}
