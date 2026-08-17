package org.pocketnutrition.api.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class IngredientResolutionService(
    private val esClient: ElasticsearchClient
) {

    private val log = LoggerFactory.getLogger(IngredientResolutionService::class.java)
    private val cache = ConcurrentHashMap<String, String>()

    private data class ProductSearchDoc(
        @JsonProperty("product_id") val productId: String? = null,
    )

    fun resolve(name: String): String? {
        val key = name.trim().lowercase()
        val hit = cache.computeIfAbsent(key) { searchForIngredient(it) ?: "" }
        return hit.takeIf { it.isNotEmpty() }
    }

    private fun searchForIngredient(name: String): String? {
        return try {
            exactMatch(name) ?: fuzzyMatch(name)
        } catch (e: Exception) {
            log.warn("Ingredient resolution failed for '{}': {}", name, e.message)
            null
        }
    }

    private fun exactMatch(name: String): String? {
        val response = esClient.search({ s ->
            s.index(INDEX_NAME)
             .query { q ->
                 q.bool { b ->
                     b.must { m ->
                         m.term { t -> t.field("product_type").value(FieldValue.of("ingredient")) }
                     }
                     b.must { m ->
                         m.nested { n ->
                             n.path("labels")
                              .query { lq ->
                                  lq.term { t ->
                                      t.field("labels.normalized.keyword").value(FieldValue.of(name))
                                  }
                              }
                              .scoreMode(ChildScoreMode.Max)
                         }
                     }
                 }
             }
             .sort { sort -> sort.field { f -> f.field("boost").order(SortOrder.Desc) } }
             .size(1)
        }, ProductSearchDoc::class.java)
        return response.hits().hits().firstOrNull()?.source()?.productId
    }

    private fun fuzzyMatch(name: String): String? {
        val response = esClient.search({ s ->
            s.index(INDEX_NAME)
             .query { q ->
                 q.bool { b ->
                     b.must { m ->
                         m.term { t -> t.field("product_type").value(FieldValue.of("ingredient")) }
                     }
                     b.must { m ->
                         m.nested { n ->
                             n.path("labels")
                              .query { lq ->
                                  lq.multiMatch { mm ->
                                      mm.query(name)
                                        .fields("labels.text^2", "labels.normalized^3")
                                        .fuzziness("AUTO")
                                        .minimumShouldMatch("60%")
                                  }
                              }
                              .scoreMode(ChildScoreMode.Max)
                         }
                     }
                 }
             }
             .size(1)
        }, ProductSearchDoc::class.java)
        val hit = response.hits().hits().firstOrNull() ?: return null
        if ((hit.score() ?: 0.0) < SCORE_THRESHOLD) return null
        return hit.source()?.productId
    }

    companion object {
        private const val INDEX_NAME = "product_search"
        private const val SCORE_THRESHOLD = 0.5
    }
}
