package org.pocketnutrition.api.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.HitsMetadata
import co.elastic.clients.util.ObjectBuilder
import org.pocketnutrition.api.dto.IngredientSuggestion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.util.function.Function
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class IngredientSearchServiceTest {

    @Mock
    private lateinit var esClient: ElasticsearchClient

    private lateinit var service: IngredientSearchService

    @BeforeEach
    fun setup() {
        service = Mockito.spy(IngredientSearchService(esClient))
    }

    private fun suggestion(id: String, label: String = id.replace("_", " ")) =
        IngredientSuggestion(ingredientId = id, label = label, lang = "fr")

    private fun stubSearchSingle(vararg cases: Pair<String, List<IngredientSuggestion>>) {
        for ((q, results) in cases) {
            doReturn(results).`when`(service).searchSingle(eq(q), any(), any())
        }
    }

    @Test
    fun `single word returns matches in score order`() {
        stubSearchSingle("raisin" to listOf(suggestion("raisin"), suggestion("raisin_sec", "Raisin sec")))

        val result = service.search("raisin", "fr", 10)

        assertEquals(1, result.termCount)
        assertEquals(listOf("raisin", "raisin_sec"), result.results.map { it.ingredientId })
    }

    @Test
    fun `partial multi-word query tries full phrase first — raisin-s surfaces raisin-sec`() {
        // segment("raisin s") → ["raisin", "s"]
        // search() prepends full query → searchSingle called with "raisin s", then "raisin", then "s"
        stubSearchSingle(
            "raisin s" to listOf(suggestion("raisin_sec", "Raisin sec")),
            "raisin"   to listOf(suggestion("raisin")),
            "s"        to listOf(suggestion("sel", "Sel")),
        )

        val result = service.search("raisin s", "fr", 10)

        assertEquals("raisin_sec", result.results[0].ingredientId)
    }

    @Test
    fun `an exact product match for the full phrase is pinned ahead of unrelated per-word matches`() {
        // Regression: segment("pizza reine") -> ["pizza", "reine"], no curated ingredient/recipe
        // is named the whole phrase. The per-word fallback then fuzzy/prefix-matched "pâte à pizza"
        // for "pizza" and "reine-claude" (a plum variety) for "reine" — each some sub-search's own
        // top-1 result, which the old top-1-of-every-subsearch rule let reach the front of the list
        // ahead of the actual "Pizza Reine" OFF product, which is unambiguously what was typed.
        val pizzaReine = IngredientSuggestion(
            ingredientId = "off:pizza_reine",
            label = "Pizza Reine",
            lang = "fr",
            productType = "off_product",
        )
        stubSearchSingle(
            "pizza reine" to listOf(suggestion("pate_a_pizza", "Pâte à pizza"), pizzaReine),
            "pizza" to listOf(suggestion("pate_a_pizza", "Pâte à pizza")),
            "reine" to listOf(suggestion("reine_claude", "Reine-claude")),
        )

        val result = service.search("pizza reine", "fr", 10)

        assertEquals("off:pizza_reine", result.results[0].ingredientId)
    }

    @Test
    fun `a product whose label carries the phrase plus a suffix still counts as an exact phrase match`() {
        // "Pizza Reine 400g" or "Pizza Reine Well&Fit" — the OFF name is rarely just the bare dish
        // name. A word-boundary prefix ("pizza reine " + anything) is still unambiguous; a product
        // named e.g. "Pizza Reine-Marguerite" (no boundary) would not match, which is the point of
        // requiring the boundary rather than a bare `startsWith`.
        val pizzaReineWithSuffix = IngredientSuggestion(
            ingredientId = "off:pizza_reine_400g",
            label = "Pizza Reine 400g",
            lang = "fr",
            productType = "off_product",
        )
        stubSearchSingle(
            "pizza reine" to listOf(suggestion("pate_a_pizza", "Pâte à pizza"), pizzaReineWithSuffix),
            "pizza" to listOf(suggestion("pate_a_pizza", "Pâte à pizza")),
            "reine" to listOf(suggestion("reine_claude", "Reine-claude")),
        )

        val result = service.search("pizza reine", "fr", 10)

        assertEquals("off:pizza_reine_400g", result.results[0].ingredientId)
    }

    @Test
    fun `comma-separated query does not prepend full phrase — each ingredient searched independently`() {
        // Commas are explicit separators — "raisin, pomme" must not try "raisin, pomme" as a phrase.
        stubSearchSingle(
            "raisin" to listOf(suggestion("raisin")),
            "pomme"  to listOf(suggestion("pomme")),
        )

        val result = service.search("raisin, pomme", "fr", 10)

        assertEquals(2, result.termCount)
        assertEquals("raisin", result.results[0].ingredientId)
        assertEquals("pomme", result.results[1].ingredientId)
    }

    @Test
    fun `duplicate results across search terms are deduplicated`() {
        // All three searchSingle calls return the same ingredient → appears only once.
        stubSearchSingle(
            "raisin s" to listOf(suggestion("raisin_sec", "Raisin sec")),
            "raisin"   to listOf(suggestion("raisin_sec", "Raisin sec")),
            "s"        to listOf(suggestion("raisin_sec", "Raisin sec")),
        )

        val result = service.search("raisin s", "fr", 10)

        assertEquals(1, result.results.size)
        assertEquals("raisin_sec", result.results[0].ingredientId)
    }

    @Test
    fun `es failure in searchSingle returns empty list without crashing`() {
        doReturn(emptyList<IngredientSuggestion>()).`when`(service).searchSingle(any(), any(), any())

        val result = service.search("raisin", "fr", 10)

        assertEquals(emptyList(), result.results)
    }

    // --- Elasticsearch query shape -------------------------------------------------------------
    // The tests above stub searchSingle, so the query handed to Elasticsearch was never asserted.
    // These run the real method against a mocked client and inspect the request that comes out.

    /**
     * Run the real searchSingle and return every SearchRequest it built.
     *
     * There are two now — one per source family — so order matters: curated first, products second,
     * matching the call order in searchSingle.
     */
    private fun captureRequests(q: String, lang: String?): List<SearchRequest> {
        @Suppress("UNCHECKED_CAST")
        val response = Mockito.mock(SearchResponse::class.java) as SearchResponse<SearchDoc>
        @Suppress("UNCHECKED_CAST")
        val hits = Mockito.mock(HitsMetadata::class.java) as HitsMetadata<SearchDoc>
        doReturn(hits).`when`(response).hits()

        val captor = argumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>()
        doReturn(response).`when`(esClient).search(captor.capture(), eq(SearchDoc::class.java))

        service.searchSingle(q, lang, 10)

        return captor.allValues.map { it.apply(SearchRequest.Builder()).build() }
    }

    /** The curated query — the first of the two. */
    private fun captureRequest(q: String, lang: String?): SearchRequest = captureRequests(q, lang).first()

    /**
     * The bool query nested under function_score → bool → must[0] → nested(labels).
     *
     * The extra `bool` is the product_type filter wrapper added when searchSingle split into one
     * query per source family; the textual block itself is unchanged.
     */
    private fun SearchRequest.labelsBool(): BoolQuery =
        query()!!.functionScore().query()!!.bool().must()[0].nested().query().bool()

    /** The `product_type` values this request is restricted to. */
    private fun SearchRequest.productTypeFilter(): List<String> =
        query()!!.functionScore().query()!!.bool().filter()
            .single { it.isTerms && it.terms().field() == "product_type" }
            .terms().terms().value().map { it.stringValue() }

    @Test
    fun `a textual clause must match — the language clause cannot match on its own`() {
        // Regression: labels.lang used to sit in the same should-list as the textual clauses with
        // minimum_should_match 1, so `?q=<anything>&lang=fr` matched every French-labelled document
        // and ranked the whole catalogue by the `boost` field alone — recipes (boost 1.5) first.
        val bool = captureRequest("concombre", "fr").labelsBool()

        // Matching is governed by the mandatory textual block, not by should clauses.
        assertNull(bool.minimumShouldMatch())
        assertEquals(1, bool.must().size)

        val textual = bool.must()[0].bool()
        assertEquals("1", textual.minimumShouldMatch())
        assertEquals(4, textual.should().size)
        assertTrue(
            textual.should().none { it.isTerm && it.term().field() == "labels.lang" },
            "labels.lang must not be one of the clauses that can satisfy the match",
        )

        // Language stays as a sibling should — score-only tie-breaker.
        assertEquals(1, bool.should().size)
        assertEquals("labels.lang", bool.should()[0].term().field())
        assertEquals("fr", bool.should()[0].term().value().stringValue())
    }

    @Test
    fun `without lang the query keeps the same mandatory textual block and no should clause`() {
        val bool = captureRequest("concombre", null).labelsBool()

        assertEquals(1, bool.must().size)
        assertEquals(4, bool.must()[0].bool().should().size)
        assertEquals("1", bool.must()[0].bool().minimumShouldMatch())
        assertTrue(bool.should().isEmpty())
    }

    @Test
    fun `one query per source family, each filtered to its own product types`() {
        // Curated and products are queried separately because a single ranked query cannot work:
        // the term/prefix boosts reward labels equal to the query, French curated names put the head
        // noun last ("blanc de poulet"), and OFF is full of products named the bare food word. The
        // live index returned 1 curated ingredient and 29 identically-labelled products for
        // `?q=poulet` while the knowledge base holds 67 distinct chicken ingredients.
        val requests = captureRequests("poulet", "fr")

        assertEquals(2, requests.size, "expected one query for curated and one for products")
        assertEquals(listOf("ingredient", "recipe"), requests[0].productTypeFilter())
        assertEquals(listOf("off_product"), requests[1].productTypeFilter())
    }

    @Test
    fun `the product type constraint is a filter, so it cannot influence the score`() {
        val root = captureRequest("poulet", "fr").query()!!.functionScore().query()!!.bool()

        assertEquals(1, root.filter().size)
        assertEquals("product_type", root.filter()[0].terms().field())
        // A `must` here would let the constraint contribute to relevance ranking.
        assertTrue(root.must().none { it.isTerms })
    }

    // --- Window composition --------------------------------------------------------------------
    // composeWindow is the quota rule that keeps products from taking the whole result list. It is
    // pure, so it is asserted directly rather than through a mocked client.

    @Test
    fun `products are capped at a fifth of the window so curated results lead`() {
        val curated = (1..30).map { "i$it" }
        val products = (1..30).map { "p$it" }

        val window = service.composeWindow(curated, products, 30)

        assertEquals(30, window.size)
        assertEquals(24, window.count { it.startsWith("i") })
        assertEquals(6, window.count { it.startsWith("p") })
        // Curated first — a person logging a meal usually means the food, not a packaged product.
        assertEquals("i1", window.first())
        assertEquals(24, window.indexOfFirst { it.startsWith("p") })
    }

    @Test
    fun `a query matching only products fills the whole window with them`() {
        // "coca", "nutella" — the curated catalogue has nothing, and the searcher should not be
        // punished with a two-row list.
        val window = service.composeWindow(emptyList(), (1..30).map { "p$it" }, 30)

        assertEquals(30, window.size)
    }

    @Test
    fun `a query matching only curated entries fills the whole window with them`() {
        val window = service.composeWindow((1..30).map { "i$it" }, emptyList(), 30)

        assertEquals(30, window.size)
    }

    @Test
    fun `products absorb the slack when curated matches are few`() {
        // Two curated matches must not leave 28 slots empty.
        val window = service.composeWindow(listOf("i1", "i2"), (1..30).map { "p$it" }, 30)

        assertEquals(30, window.size)
        assertEquals(listOf("i1", "i2"), window.take(2))
    }

    @Test
    fun `the reserved product slice never drops below the floor at small limits`() {
        // limit/5 would give 1 at limit 5 and 0 at limit 4; the floor keeps products reachable.
        val curated = (1..10).map { "i$it" }
        val products = (1..10).map { "p$it" }

        assertEquals(2, service.composeWindow(curated, products, 5).count { it.startsWith("p") })
        assertEquals(2, service.composeWindow(curated, products, 4).count { it.startsWith("p") })
        assertEquals(2, service.composeWindow(curated, products, 2).count { it.startsWith("p") })
    }

    @Test
    fun `a non-positive limit yields nothing rather than throwing`() {
        assertEquals(emptyList(), service.composeWindow(listOf("i1"), listOf("p1"), 0))
        assertEquals(emptyList(), service.composeWindow(listOf("i1"), listOf("p1"), -1))
    }

    // --- computeServingUnits ---------------------------------------------------------------------
    // The "package" unit is the whole-product shortcut (a yogurt pot, a soda bottle); "serving" is
    // the manufacturer's declared per-serving weight (a pizza slice, a tart wedge) — distinct
    // concepts, both derived from OFF fields the ingest pipeline parses independently.

    @Test
    fun `a gram-quantified product gets a package unit — not gated on packageMl`() {
        // Regression: this used to be gated on packageSizeMl alone, so a solid product (the common
        // case — package_size_ml is null for every one of them) never got a package unit at all,
        // even once package_size_g held the right value.
        val doc = SearchDoc(packageSizeG = 125.0)

        val units = service.computeServingUnits(doc)

        val pkg = units.single { it.unit == "package" }
        assertEquals(125.0, pkg.grams)
        assertEquals(0.0, pkg.volumeMl)
        assertEquals("unité", pkg.labelFr)
    }

    @Test
    fun `a millilitre-quantified product gets a package unit sized from packageMl`() {
        val doc = SearchDoc(packageSizeMl = 500.0)

        val pkg = service.computeServingUnits(doc).single { it.unit == "package" }

        assertEquals(500.0, pkg.volumeMl)
        assertEquals(500.0, pkg.grams)
    }

    @Test
    fun `no package unit when neither package size is known`() {
        val units = service.computeServingUnits(SearchDoc())

        assertTrue(units.none { it.unit == "package" })
    }

    @Test
    fun `a product with a declared serving size gets a serving unit`() {
        // The pizza-slice / tart-wedge case: the manufacturer's own per-serving weight, not derived
        // from the package total.
        val doc = SearchDoc(servingSizeG = 100.0)

        val serving = service.computeServingUnits(doc).single { it.unit == "serving" }

        assertEquals(100.0, serving.grams)
        assertEquals(0.0, serving.volumeMl)
        assertEquals("part", serving.labelFr)
    }

    @Test
    fun `package and serving units coexist when both are known`() {
        // A 400g frozen pizza cut into 4 parts: the whole-box weight and the one-slice weight are
        // both useful and neither should suppress the other.
        val doc = SearchDoc(packageSizeG = 400.0, servingSizeG = 100.0)

        val units = service.computeServingUnits(doc)

        assertEquals(400.0, units.single { it.unit == "package" }.grams)
        assertEquals(100.0, units.single { it.unit == "serving" }.grams)
    }

    @Test
    fun `non-positive package or serving sizes are ignored`() {
        val units = service.computeServingUnits(SearchDoc(packageSizeG = 0.0, servingSizeG = -5.0))

        assertTrue(units.none { it.unit == "package" || it.unit == "serving" })
    }
}
