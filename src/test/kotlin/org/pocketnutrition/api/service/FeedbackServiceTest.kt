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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

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
}
