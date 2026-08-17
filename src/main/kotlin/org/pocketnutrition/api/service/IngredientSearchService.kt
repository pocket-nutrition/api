package org.pocketnutrition.api.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode
import com.fasterxml.jackson.annotation.JsonProperty
import org.pocketnutrition.api.client.dto.MlRawProfile
import org.pocketnutrition.api.dto.IngredientSuggestion
import org.pocketnutrition.api.dto.RecipeComponent
import org.pocketnutrition.api.dto.ServingUnit
import org.pocketnutrition.api.dto.StateMeta
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

internal data class LabelDoc(
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("text") val text: String? = null,
)

internal data class StateMetaDoc(
    @JsonProperty("portion_g") val portionG: Double? = null,
    @JsonProperty("density_g_ml") val densityGMl: Double? = null,
)

internal data class RecipeComponentDoc(
    @JsonProperty("ingredient_id")  val ingredientId: String? = null,
    @JsonProperty("weight_g")       val weightG: Double? = null,
    @JsonProperty("cooking_method") val cookingMethod: String? = null,
)

internal data class SearchDoc(
    @JsonProperty("product_id")          val productId: String? = null,
    @JsonProperty("product_type")        val productType: String? = null,
    @JsonProperty("components")          val components: List<RecipeComponentDoc>? = null,
    @JsonProperty("food_group")          val foodGroup: String? = null,
    @JsonProperty("labels")              val labels: List<LabelDoc>? = null,
    @JsonProperty("density_g_ml")        val densityGMl: Double? = null,
    @JsonProperty("portion_g")           val portionG: Double? = null,
    @JsonProperty("serving_unit_types")  val servingUnitTypes: List<String>? = null,
    @JsonProperty("display_unit")        val displayUnit: String? = null,
    @JsonProperty("serving_mode")        val servingMode: String? = null,
    @JsonProperty("unit_label_fr")       val unitLabelFr: String? = null,
    @JsonProperty("unit_label_en")       val unitLabelEn: String? = null,
    @JsonProperty("typical_state")           val typicalState: String? = null,
    @JsonProperty("portion_measured_state") val portionMeasuredState: String? = null,
    @JsonProperty("can_eat_raw")         val canEatRaw: Boolean? = null,
    @JsonProperty("can_eat_cooked")      val canEatCooked: Boolean? = null,
    @JsonProperty("yield_factors")       val yieldFactors: Map<String, Double>? = null,
    @JsonProperty("states")              val states: Map<String, StateMetaDoc>? = null,
    @JsonProperty("image_url")           val imageUrl: String? = null,
    @JsonProperty("brand")               val brand: String? = null,
    @JsonProperty("package_size_ml")     val packageSizeMl: Double? = null,
    @JsonProperty("package_size_g")      val packageSizeG: Double? = null,
    @JsonProperty("serving_size_g")      val servingSizeG: Double? = null,
    // nutrients — present for canonical ingredients (all 24) and OFF products (7 macros)
    @JsonProperty("energy_kcal")         val energyKcal: Double? = null,
    @JsonProperty("water_g")             val waterG: Double? = null,
    @JsonProperty("protein_g")           val proteinG: Double? = null,
    @JsonProperty("fat_g")               val fatG: Double? = null,
    @JsonProperty("carbohydrates_g")     val carbohydratesG: Double? = null,
    @JsonProperty("sugars_g")            val sugarsG: Double? = null,
    @JsonProperty("fiber_g")             val fiberG: Double? = null,
    @JsonProperty("calcium_mg")          val calciumMg: Double? = null,
    @JsonProperty("iron_mg")             val ironMg: Double? = null,
    @JsonProperty("magnesium_mg")        val magnesiumMg: Double? = null,
    @JsonProperty("sodium_mg")           val sodiumMg: Double? = null,
    @JsonProperty("vitamin_c_mg")        val vitaminCMg: Double? = null,
    @JsonProperty("saturated_fat_g")     val saturatedFatG: Double? = null,
    @JsonProperty("monounsaturated_fat_g") val monounsaturatedFatG: Double? = null,
    @JsonProperty("polyunsaturated_fat_g") val polyunsaturatedFatG: Double? = null,
    @JsonProperty("cholesterol_mg")      val cholesterolMg: Double? = null,
    @JsonProperty("potassium_mg")        val potassiumMg: Double? = null,
    @JsonProperty("phosphorus_mg")       val phosphorusMg: Double? = null,
    @JsonProperty("zinc_mg")             val zincMg: Double? = null,
    @JsonProperty("vitamin_a_ug")        val vitaminAUg: Double? = null,
    @JsonProperty("vitamin_d_ug")        val vitaminDUg: Double? = null,
    @JsonProperty("vitamin_b6_mg")       val vitaminB6Mg: Double? = null,
    @JsonProperty("vitamin_b12_ug")      val vitaminB12Ug: Double? = null,
    @JsonProperty("niacin_mg")           val niacinMg: Double? = null,
)

@Service
class IngredientSearchService(
    private val esClient: ElasticsearchClient,
) {

    private val log = LoggerFactory.getLogger(IngredientSearchService::class.java)

    data class SearchResult(val termCount: Int, val results: List<IngredientSuggestion>)

    fun search(q: String, lang: String?, limit: Int): SearchResult {
        val query = q.trim()
        if (query.isEmpty()) return SearchResult(0, emptyList())

        val terms = segment(query)
        if (terms.size == 1) return SearchResult(1, searchSingle(terms[0], lang, limit))

        val hasExplicitSeparator = query.contains(Regex("[,;]"))
        val searchTerms = if (terms.size > 1 && !hasExplicitSeparator) listOf(query) + terms else terms
        val perTermResults = searchTerms.map { searchSingle(it, lang, limit) }
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<IngredientSuggestion>()

        // A product literally named the whole typed phrase — an Open Food Facts "Pizza Reine" for
        // `q=pizza reine` — is pinned first, ahead of the per-term interleaving below. Without this,
        // a bare word from the phrase ("reine") can prefix- or fuzzy-match an unrelated curated
        // ingredient (reine-claude, a plum variety) purely because it is that sub-search's own top
        // result, and that unrelated match reached the front of the list ahead of the one document
        // that is unambiguously what was typed. Looked for only among `perTermResults[0]` — the
        // results already fetched for the undecomposed phrase — so this costs no extra request.
        exactPhraseProductMatch(query, perTermResults.getOrNull(0).orEmpty())?.let {
            if (seen.add(it.ingredientId)) merged.add(it)
        }

        for (results in perTermResults) {
            results.firstOrNull()?.let { if (seen.add(it.ingredientId)) merged.add(it) }
        }
        for (results in perTermResults) {
            for (result in results.drop(1)) {
                if (seen.add(result.ingredientId)) merged.add(result)
                if (merged.size >= limit) return SearchResult(terms.size, merged.take(limit))
            }
        }
        return SearchResult(terms.size, merged.take(limit))
    }

    /**
     * A product from the full-phrase results whose label is the typed query itself — an exact
     * normalized match, or the query plus a word-boundary suffix ("Pizza Reine 400g" for
     * `q=pizza reine`) — not a fuzzy or prefix-of-a-single-token hit. Restricted to `off_product`:
     * a curated ingredient matching this strictly would already rank correctly through
     * `composeWindow`'s curated-first quota, and this check exists only to rescue an exact product
     * match from behind the per-word noise a multi-word query can introduce.
     */
    private fun exactPhraseProductMatch(
        query: String,
        phraseResults: List<IngredientSuggestion>,
    ): IngredientSuggestion? {
        val normalizedQuery = slugify(query).replace("_", " ")
        return phraseResults.firstOrNull { suggestion ->
            if (suggestion.productType != "off_product") return@firstOrNull false
            val normalizedLabel = slugify(suggestion.label).replace("_", " ")
            normalizedLabel == normalizedQuery || normalizedLabel.startsWith("$normalizedQuery ")
        }
    }

    /**
     * Search one term, composing the result from two independent queries — curated first.
     *
     * One ranked query over the whole index cannot work here, and the reason is structural rather
     * than a tuning problem. The high-boost clauses below all reward a label that *equals* or
     * *begins with* the query: `labels.normalized.keyword` term (×10) and prefix (×4). French
     * culinary names put the head noun last — "blanc de poulet", "aile de poulet", "bouillon de
     * poulet" — so a one-word query for the head noun reaches curated ingredients only through the
     * low-boosted fuzzy `multiMatch`. Open Food Facts, meanwhile, is full of products whose
     * `product_name` is the bare food word, so `?q=poulet` matched hundreds of documents literally
     * named "Poulet" at ×10 and they took the entire window.
     *
     * Measured against the live index before this change: `?q=poulet&limit=30` returned **1**
     * curated ingredient and 29 products, every product carrying the identical label "Poulet", while
     * the knowledge base holds **67 distinctly-named chicken ingredients**. The `boost` field
     * (products 0.5, ingredients 1.0, recipes 1.5) cannot fix that — a ×10 term match on 0.5 still
     * beats a fuzzy match on 1.0.
     *
     * So each source family gets its own query and a guaranteed share of the window. Products keep a
     * reserved slice ([MIN_PRODUCT_SLOTS], or a fifth of the window, whichever is larger) because
     * some searches genuinely want a packaged product — "coca", "nutella" — and the curated
     * catalogue has nothing to offer them. When one family returns nothing the other takes the whole
     * window, so a query with no curated match is not punished with a short list.
     *
     * Two round trips rather than one. Deliberate: the alternative is over-fetching a mixed ranking
     * and hoping curated documents appear in it, which is exactly what fails today.
     */
    internal open fun searchSingle(q: String, lang: String?, limit: Int): List<IngredientSuggestion> {
        val curated = queryDocs(q, lang, CURATED_TYPES, limit)
        val products = queryDocs(q, lang, PRODUCT_TYPES, limit)
        return attachRecipeComponents(composeWindow(curated, products, limit), lang)
    }

    /**
     * Interleave the two source families into one window of at most [limit] entries, curated first.
     *
     * Kept pure and generic so the quota rule can be asserted without a mocked Elasticsearch — the
     * rule is the behaviour worth pinning, not the query that feeds it.
     *
     * Products get [MIN_PRODUCT_SLOTS] slots or a [PRODUCT_SLOT_DIVISOR]-th of the window, whichever
     * is larger, but only when there is something in the other family to protect. Either list being
     * empty hands the whole window to the other, so a search that only matches packaged products
     * ("coca") is not truncated to a couple of rows.
     */
    internal fun <T> composeWindow(curated: List<T>, products: List<T>, limit: Int): List<T> {
        if (limit <= 0) return emptyList()
        if (curated.isEmpty()) return products.take(limit)
        if (products.isEmpty()) return curated.take(limit)

        val productSlots = (limit / PRODUCT_SLOT_DIVISOR).coerceAtLeast(MIN_PRODUCT_SLOTS)
        val head = curated.take((limit - productSlots).coerceAtLeast(0))
        // Products absorb whatever curated left unused, so a thin curated match still fills the window.
        return head + products.take(limit - head.size)
    }

    /** Run the label query restricted to one family of `product_type` values. */
    private fun queryDocs(
        q: String,
        lang: String?,
        types: List<String>,
        size: Int,
    ): List<SearchDoc> {
        val response = try {
            esClient.search({ s ->
                s.index(INDEX_NAME)
                 .query { outer ->
                     outer.functionScore { fs ->
                         fs.query { fq ->
                           fq.bool { root ->
                             // Filter, not must: the source family is a hard constraint and must not
                             // contribute to the score.
                             root.filter { f ->
                                 f.terms { t ->
                                     t.field("product_type")
                                      .terms { v -> v.value(types.map { FieldValue.of(it) }) }
                                 }
                             }
                             root.must { rootMust ->
                             rootMust.nested { n ->
                                 n.path("labels")
                                  .query { lq ->
                                      lq.bool { outerBool ->
                                          // A textual clause MUST match. The language clause below is a
                                          // sibling `should`, so it only breaks ties between documents that
                                          // already matched the text. Leaving it in the same should-list as
                                          // the textual clauses (with minimum_should_match: 1) made every
                                          // document owning a label in that language a hit, which turned
                                          // `?q=<anything>&lang=fr` into a full-catalogue scan ranked by the
                                          // `boost` field alone — recipes (boost 1.5) first.
                                          outerBool.must { mq ->
                                              mq.bool { b ->
                                                  b.should { sh ->
                                                      sh.matchPhrasePrefix { m ->
                                                          m.field("labels.text").query(q).boost(3.0f)
                                                      }
                                                  }
                                                  b.should { sh ->
                                                      sh.multiMatch { m ->
                                                          m.query(q)
                                                           .fields("labels.text^2", "labels.normalized^3")
                                                           .fuzziness("AUTO")
                                                      }
                                                  }
                                                  b.should { sh ->
                                                      sh.term { t ->
                                                          t.field("labels.normalized.keyword")
                                                           .value(FieldValue.of(q.lowercase().trim()))
                                                           .boost(10.0f)
                                                      }
                                                  }
                                                  b.should { sh ->
                                                      sh.prefix { p ->
                                                          p.field("labels.normalized.keyword")
                                                           .value(q.lowercase().trim())
                                                           .boost(4.0f)
                                                      }
                                                  }
                                                  b.minimumShouldMatch("1")
                                              }
                                          }
                                          if (lang != null) {
                                              outerBool.should { sh ->
                                                  sh.term { t ->
                                                      t.field("labels.lang").value(FieldValue.of(lang)).boost(1.5f)
                                                  }
                                              }
                                          }
                                          outerBool
                                      }
                                  }
                                  .scoreMode(ChildScoreMode.Max)
                             }
                             }
                             root
                           }
                         }
                         fs.functions { fn ->
                             fn.fieldValueFactor { fvf ->
                                 fvf.field("boost")
                                    .factor(1.0)
                                    .modifier(FieldValueFactorModifier.None)
                                    .missing(1.0)
                             }
                         }
                         fs.boostMode(FunctionBoostMode.Multiply)
                     }
                 }
                 .size(size)
            }, SearchDoc::class.java)
        } catch (e: Exception) {
            log.warn("Ingredient search for '{}' failed on types {}: {}", q, types, e.message)
            return emptyList()
        }

        return response.hits().hits().mapNotNull { it.source() }
    }

    private fun toSuggestion(doc: SearchDoc, lang: String?): IngredientSuggestion? {
        val id = doc.productId ?: return null
        val preferred = doc.labels?.firstOrNull { it.lang == lang }
            ?: doc.labels?.firstOrNull { it.lang == "fr" }
            ?: doc.labels?.firstOrNull()
        return IngredientSuggestion(
            ingredientId = id,
            label = preferred?.text ?: id.replace("_", " "),
            lang = preferred?.lang ?: "und",
            portionG = doc.portionG,
            servingUnits = computeServingUnits(doc),
            displayUnit = doc.displayUnit,
            typicalState = doc.typicalState,
            portionMeasuredState = doc.portionMeasuredState,
            canEatRaw = doc.canEatRaw,
            canEatCooked = doc.canEatCooked,
            yieldFactors = doc.yieldFactors,
            productType = doc.productType,
            imageUrl = doc.imageUrl,
            brand = doc.brand,
            foodGroup = doc.foodGroup,
            servingMode = doc.servingMode,
            unitLabelFr = doc.unitLabelFr,
            unitLabelEn = doc.unitLabelEn,
            states = doc.states?.mapValues { (_, v) -> StateMeta(portionG = v.portionG, densityGMl = v.densityGMl) },
        )
    }

    /**
     * Map search docs to suggestions; recipe docs additionally carry fully-populated
     * component suggestions, resolved in a single terms query on product_id. A recipe
     * whose components cannot all be resolved is dropped — the frontend could not
     * unpack it.
     */
    private fun attachRecipeComponents(
        docs: List<SearchDoc>,
        lang: String?,
    ): List<IngredientSuggestion> {
        val componentIds = docs
            .filter { it.productType == "recipe" }
            .flatMap { it.components.orEmpty() }
            .mapNotNull { it.ingredientId }
            .distinct()
        val componentDocs = findByProductIds(componentIds)

        return docs.mapNotNull { doc ->
            val suggestion = toSuggestion(doc, lang) ?: return@mapNotNull null
            if (doc.productType != "recipe") return@mapNotNull suggestion
            val components = doc.components.orEmpty().map { c ->
                val ingredientDoc = componentDocs[c.ingredientId] ?: run {
                    log.warn("Recipe '{}' component '{}' not found in index — dropping recipe",
                        suggestion.ingredientId, c.ingredientId)
                    return@mapNotNull null
                }
                RecipeComponent(
                    weightG = c.weightG ?: return@mapNotNull null,
                    cookingMethod = c.cookingMethod ?: return@mapNotNull null,
                    ingredient = toSuggestion(ingredientDoc, lang) ?: return@mapNotNull null,
                )
            }
            if (components.isEmpty()) null else suggestion.copy(recipeComponents = components)
        }
    }

    private fun findByProductIds(productIds: List<String>): Map<String, SearchDoc> {
        if (productIds.isEmpty()) return emptyMap()
        return try {
            val response = esClient.search({ s ->
                s.index(INDEX_NAME)
                 .query { q ->
                     q.terms { t ->
                         t.field("product_id")
                          .terms { v -> v.value(productIds.map { FieldValue.of(it) }) }
                     }
                 }
                 .size(productIds.size)
            }, SearchDoc::class.java)
            response.hits().hits()
                .mapNotNull { it.source() }
                .filter { it.productType == "ingredient" }
                .associateBy { it.productId ?: "" }
        } catch (e: Exception) {
            log.warn("findByProductIds failed for {} ids: {}", productIds.size, e.message)
            emptyMap()
        }
    }

    /**
     * Look up a product by product_id and return its nutrient profile.
     * Returns null if the product is not found or has no nutrient data.
     */
    fun lookupProfile(productId: String): MlRawProfile? {
        val doc = findByProductId(productId) ?: return null
        val foodGroup = doc.foodGroup ?: "other"
        return MlRawProfile(
            energy_kcal          = doc.energyKcal,
            water_g              = doc.waterG,
            protein_g            = doc.proteinG,
            fat_g                = doc.fatG,
            carbohydrates_g      = doc.carbohydratesG,
            sugars_g             = doc.sugarsG,
            fiber_g              = doc.fiberG,
            calcium_mg           = doc.calciumMg,
            iron_mg              = doc.ironMg,
            magnesium_mg         = doc.magnesiumMg,
            sodium_mg            = doc.sodiumMg,
            vitamin_c_mg         = doc.vitaminCMg,
            saturated_fat_g      = doc.saturatedFatG,
            monounsaturated_fat_g = doc.monounsaturatedFatG,
            polyunsaturated_fat_g = doc.polyunsaturatedFatG,
            cholesterol_mg       = doc.cholesterolMg,
            potassium_mg         = doc.potassiumMg,
            phosphorus_mg        = doc.phosphorusMg,
            zinc_mg              = doc.zincMg,
            vitamin_a_ug         = doc.vitaminAUg,
            vitamin_d_ug         = doc.vitaminDUg,
            vitamin_b6_mg        = doc.vitaminB6Mg,
            vitamin_b12_ug       = doc.vitaminB12Ug,
            niacin_mg            = doc.niacinMg,
            food_group           = foodGroup,
            is_plant             = foodGroup in PLANT_GROUPS,
        )
    }

    /** Look up a product document by product_id (for BarcodeService and direct lookups). */
    internal fun lookupDocument(productId: String): SearchDoc? = findByProductId(productId)

    private fun findByProductId(productId: String): SearchDoc? {
        return try {
            val response = esClient.search({ s ->
                s.index(INDEX_NAME)
                 .query { q -> q.term { t -> t.field("product_id").value(FieldValue.of(productId)) } }
                 .size(1)
            }, SearchDoc::class.java)
            response.hits().hits().firstOrNull()?.source()
        } catch (e: Exception) {
            log.warn("findByProductId failed for '{}': {}", productId, e.message)
            null
        }
    }

    /**
     * Split a free-text query into individual ingredient phrases.
     * Commas and semicolons are explicit separators. For space-only queries,
     * greedy longest-match probes find multi-word ingredient names (e.g. "pomme de terre").
     */
    private fun segment(query: String): List<String> {
        if (query.contains(Regex("[,;]"))) {
            return query.split(Regex("[,;]+")).map { it.trim() }.filter { it.isNotBlank() }
        }
        val tokens = query.trim().split(Regex("\\s+"))
        if (tokens.size == 1) return listOf(query)

        val terms = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var matched = false
            for (window in (1..minOf(MAX_WINDOW, tokens.size - i)).reversed()) {
                val phrase = tokens.subList(i, i + window).joinToString(" ")
                if (probeExists(phrase)) {
                    terms.add(phrase)
                    i += window
                    matched = true
                    break
                }
            }
            if (!matched) {
                terms.add(tokens[i])
                i++
            }
        }
        return terms.ifEmpty { listOf(query) }
    }

    private fun probeExists(phrase: String): Boolean {
        val normalized = slugify(phrase).replace("_", " ")
        return try {
            val response = esClient.search({ s ->
                s.index(INDEX_NAME)
                 .query { q ->
                     q.nested { n ->
                         n.path("labels")
                          .query { lq ->
                              lq.term { t ->
                                  t.field("labels.normalized.keyword")
                                   .value(FieldValue.of(normalized))
                              }
                          }
                          .scoreMode(ChildScoreMode.Max)
                     }
                 }
                 .size(1)
            }, SearchDoc::class.java)
            (response.hits().total()?.value() ?: 0L) > 0L
        } catch (e: Exception) {
            false
        }
    }

    internal fun computeServingUnits(doc: SearchDoc): List<ServingUnit> {
        val units = mutableListOf<ServingUnit>()
        if (doc.servingMode == "item" && doc.unitLabelFr != null && doc.portionG != null) {
            units += ServingUnit(unit = "item", labelFr = doc.unitLabelFr, labelEn = doc.unitLabelEn, volumeMl = 0.0, grams = doc.portionG)
        }
        val density = doc.densityGMl
        if (density != null && !doc.servingUnitTypes.isNullOrEmpty()) {
            doc.servingUnitTypes.forEach { unit ->
                val volume = UNIT_VOLUMES[unit] ?: return@forEach
                units += ServingUnit(unit = unit, labelFr = UNIT_LABELS_FR[unit] ?: unit,
                    volumeMl = volume, grams = Math.round(volume * density * 100.0) / 100.0)
            }
        }
        val packageMl = doc.packageSizeMl
        val packageG = doc.packageSizeG
        // Gating on packageMl alone used to mean a solid product's whole-package weight never
        // surfaced, even once package_size_g held the right value: the vast majority of OFF
        // products are grams-quantified and packageMl is null for every one of them.
        if ((packageMl != null && packageMl > 0) || (packageG != null && packageG > 0)) {
            units += ServingUnit(
                unit     = "package",
                labelFr  = doc.unitLabelFr ?: "unité",
                labelEn  = doc.unitLabelEn ?: "unit",
                volumeMl = packageMl ?: 0.0,
                grams    = packageG ?: packageMl ?: 0.0,
            )
        }
        // The manufacturer's declared per-serving weight — what gives a multi-slice product like a
        // pizza or a tart a real "1 part" shortcut, distinct from the whole-package one above.
        val servingG = doc.servingSizeG
        if (servingG != null && servingG > 0) {
            units += ServingUnit(
                unit     = "serving",
                labelFr  = doc.unitLabelFr ?: "part",
                labelEn  = doc.unitLabelEn ?: "serving",
                volumeMl = 0.0,
                grams    = servingG,
            )
        }
        return units
    }

    companion object {
        private const val INDEX_NAME = "product_search"
        private const val MAX_WINDOW = 4

        /**
         * The two source families [searchSingle] queries separately. Values are the `product_type`
         * keyword emitted by `pocket-nutrition-ingest/pipeline/index_search.py`; the vocabulary is
         * closed and holds exactly these three. Recipes ride with ingredients because both are
         * curated knowledge and both are what someone logging a meal usually means.
         */
        private val CURATED_TYPES = listOf("ingredient", "recipe")
        private val PRODUCT_TYPES = listOf("off_product")

        /**
         * Window slice reserved for branded products: a fifth of the requested limit, never fewer
         * than [MIN_PRODUCT_SLOTS]. Small enough that a generic search reads as a list of foods
         * rather than a supermarket shelf, large enough that scanning for a specific packaged item
         * still works.
         */
        private const val PRODUCT_SLOT_DIVISOR = 5
        private const val MIN_PRODUCT_SLOTS = 2

        private val PLANT_GROUPS = setOf("vegetable", "cereal")

        private val UNIT_VOLUMES: Map<String, Double> = mapOf(
            "pinch"        to 0.3,
            "teaspoon"     to 5.0,
            "tablespoon"   to 15.0,
            "cup"          to 240.0,
            "handful"      to 60.0,
            "small_handful" to 30.0,
            "glass"        to 200.0,
            "shot"         to 40.0,
            "bowl"         to 400.0,
            "scoop"        to 30.0,
        )

        private val UNIT_LABELS_FR: Map<String, String> = mapOf(
            "pinch"        to "pincée",
            "teaspoon"     to "c.à.c.",
            "tablespoon"   to "c.à.s.",
            "cup"          to "tasse",
            "handful"      to "poignée",
            "small_handful" to "petite poignée",
            "glass"        to "verre",
            "shot"         to "dose",
            "bowl"         to "bol",
            "scoop"        to "mesurette",
        )
    }
}
