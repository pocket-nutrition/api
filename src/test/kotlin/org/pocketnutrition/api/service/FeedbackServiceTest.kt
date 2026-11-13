package org.pocketnutrition.api.service

import org.pocketnutrition.api.dto.FeedbackCorrection
import org.pocketnutrition.api.dto.FeedbackRequest
import org.pocketnutrition.api.model.FeedbackReport
import org.pocketnutrition.api.repository.FeedbackRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class FeedbackServiceTest {

    private val repository: FeedbackRepository = mock()
    private val resolution: IngredientResolutionService = mock()
    private val objectMapper = JsonMapper.builder().build()

    private fun service(salt: String = "") = FeedbackService(repository, resolution, objectMapper, salt)

    private fun echoSave() {
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as FeedbackReport }
    }

    @Test
    fun `serialises corrections to json and resolves canonical slug`() {
        whenever(resolution.resolve("poivre noir")).thenReturn("poivre_noir")
        echoSave()

        val req = FeedbackRequest(
            name = "poivre noir",
            corrections = listOf(
                FeedbackCorrection("measures", "reference_weight", "5", "g"),
                FeedbackCorrection("measures", "serving_unit", "pinch", null),
            ),
        )
        val r = service().submit(req, null, null)

        assertEquals("poivre_noir", r.ingredientId)
        assertTrue(r.resolved)
        assertTrue(r.corrections!!.contains("reference_weight"))
        assertTrue(r.corrections!!.contains("pinch"))
    }

    @Test
    fun `comment-only report has null corrections and uses slugify fallback`() {
        whenever(resolution.resolve("Pâtes complètes")).thenReturn(null)
        echoSave()

        val req = FeedbackRequest(name = "Pâtes complètes", corrections = emptyList(), comment = "info")
        val r = service().submit(req, null, null)

        assertNull(r.corrections)
        assertEquals(slugify("Pâtes complètes"), r.ingredientId)
        assertFalse(r.resolved)
        assertEquals("info", r.comment)
    }

    @Test
    fun `does not hash ip when no salt configured`() {
        whenever(resolution.resolve(any())).thenReturn(null)
        echoSave()

        val r = service(salt = "").submit(FeedbackRequest(name = "rice", comment = "x"), "1.2.3.4", "agent")

        assertNull(r.ipHash)
    }

    @Test
    fun `hashes ip when salt configured and never stores the raw ip`() {
        whenever(resolution.resolve(any())).thenReturn(null)
        echoSave()

        val r = service(salt = "pepper").submit(FeedbackRequest(name = "rice", comment = "x"), "1.2.3.4", "agent")

        assertEquals(64, r.ipHash?.length) // SHA-256 hex
        assertTrue(r.ipHash?.contains("1.2.3.4") != true)
    }

    @Test
    fun `defaults source surface to sandbox when absent`() {
        whenever(resolution.resolve(any())).thenReturn(null)
        echoSave()

        val r = service().submit(FeedbackRequest(name = "rice", comment = "x"), null, null)

        assertEquals("sandbox", r.sourceSurface)
    }

    @Test
    fun `stores the given source surface`() {
        whenever(resolution.resolve(any())).thenReturn(null)
        echoSave()

        val r = service().submit(FeedbackRequest(name = "rice", comment = "x", sourceSurface = "community"), null, null)

        assertEquals("community", r.sourceSurface)
    }

    @Test
    fun `lists reports for an ingredient with corrections parsed back to structured objects, newest first`() {
        val stored = FeedbackReport(
            reportedName = "riz",
            corrections = """[{"category":"measures","code":"reference_weight","value":"180","unit":"g"}]""",
            comment = null,
            ingredientId = "rice",
            cookingMethod = "raw",
            measuredState = "raw",
            sourceSurface = "community",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        ).also { it.id = 42L }
        whenever(repository.findByIngredientIdOrderByCreatedAtDesc(eq("rice"), eq(PageRequest.of(0, 5))))
            .thenReturn(PageImpl(listOf(stored), PageRequest.of(0, 5), 1))

        val result = service().listByIngredient("rice", offset = 0, limit = 5)

        assertEquals(1L, result.total)
        val report = result.reports.single()
        assertEquals(42L, report.id)
        assertEquals("community", report.sourceSurface)
        val correction = report.corrections.single()
        assertEquals("measures", correction.category)
        assertEquals("reference_weight", correction.code)
        assertEquals("180", correction.value)
        assertEquals("g", correction.unit)
    }

    @Test
    fun `comment-only report lists with an empty corrections list`() {
        val stored = FeedbackReport(
            reportedName = "riz",
            corrections = null,
            comment = "devrait permettre une pincée",
            ingredientId = "rice",
            sourceSurface = "sandbox",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        ).also { it.id = 7L }
        whenever(repository.findByIngredientIdOrderByCreatedAtDesc(eq("rice"), eq(PageRequest.of(1, 5))))
            .thenReturn(PageImpl(listOf(stored), PageRequest.of(1, 5), 6))

        val result = service().listByIngredient("rice", offset = 5, limit = 5)

        assertEquals(6L, result.total)
        val report = result.reports.single()
        assertTrue(report.corrections.isEmpty())
        assertEquals("devrait permettre une pincée", report.comment)
    }
}
